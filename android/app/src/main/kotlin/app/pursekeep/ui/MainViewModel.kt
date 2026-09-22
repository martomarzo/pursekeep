package app.pursekeep.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pursekeep.BuildConfig
import app.pursekeep.PurseKeepApp
import app.pursekeep.capture.KnownApps
import app.pursekeep.net.UpdateChecker
import app.pursekeep.outbox.EventEntry
import app.pursekeep.outbox.OutboxFlushWorker
import app.pursekeep.pairing.PairingParser
import app.pursekeep.settings.Pairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Home, Pairing, Apps }

data class UiState(
    val pairing: Pairing? = null,
    val pairingBroken: Boolean = false,
    val serverUrl: String? = null,
    val enabled: Set<String> = KnownApps.defaultEnabled,
    val custom: Set<String> = emptySet(),
    val pending: Int = 0,
    val events: List<EventEntry> = emptyList(),
    val lastSentAt: Long? = null,
    val notificationAccess: Boolean = false,
    val batteryExempt: Boolean = false,
    val updateTag: String? = null,
    val pairingError: String? = null,
    val screen: Screen = Screen.Home,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PurseKeepApp
    private val system = MutableStateFlow(Triple(false, false, null as String?)) // access, battery, updateTag
    private val local = MutableStateFlow(Pair(Screen.Home, null as String?))     // screen, pairingError

    val state: StateFlow<UiState> = combine(
        app.settings.pairing, app.settings.pairingBroken, app.settings.serverUrl,
        app.settings.enabledPackages, app.settings.customPackages,
        app.db.outbox().pendingCount(), app.db.events().latest(), app.settings.lastSentAt,
        system, local,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val sys = values[8] as Triple<Boolean, Boolean, String?>
        val loc = values[9] as Pair<Screen, String?>
        UiState(
            pairing = values[0] as Pairing?, pairingBroken = values[1] as Boolean, serverUrl = values[2] as String?,
            enabled = values[3] as Set<String>, custom = values[4] as Set<String>,
            pending = values[5] as Int, events = values[6] as List<EventEntry>, lastSentAt = values[7] as Long?,
            notificationAccess = sys.first, batteryExempt = sys.second, updateTag = sys.third,
            pairingError = loc.second, screen = loc.first,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun refreshSystemState() {
        val ctx = getApplication<Application>()
        system.value = system.value.copy(first = SystemActions.hasNotificationAccess(ctx), second = SystemActions.isBatteryExempt(ctx))
        OutboxFlushWorker.enqueue(ctx)
        checkUpdates()
    }

    fun go(screen: Screen) { local.value = Pair(screen, null) }

    fun pairManual(url: String, token: String) = pairWith(PairingParser.manual(url, token))
    fun pairScanned(raw: String) = pairWith(PairingParser.parse(raw))

    private fun pairWith(result: Result<app.pursekeep.pairing.PairingPayload>) {
        result.onFailure { local.value = Pair(Screen.Pairing, it.message ?: "Invalid pairing code") }
        result.onSuccess { p ->
            viewModelScope.launch {
                app.settings.pair(p.url, p.token, android.os.Build.MODEL)
                app.captures.log("info", "Paired with ${p.url}")
                app.captures.sendTest()
                local.value = Pair(Screen.Home, null)
            }
        }
    }

    fun unpair() = viewModelScope.launch { app.settings.unpair(); app.captures.log("info", "Unpaired") }
    fun sendTest() = viewModelScope.launch { app.captures.sendTest() }
    fun setPackageEnabled(pkg: String, on: Boolean) = viewModelScope.launch { app.settings.setPackageEnabled(pkg, on) }
    fun addCustomPackage(pkg: String) = viewModelScope.launch { if (pkg.isNotBlank()) app.settings.addCustomPackage(pkg.trim()) }
    fun removeCustomPackage(pkg: String) = viewModelScope.launch { app.settings.removeCustomPackage(pkg) }

    private fun checkUpdates() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        if (now - app.settings.lastUpdateCheckAt() < 24L * 60 * 60 * 1000) {
            applyTag(app.settings.latestKnownTag.first())
            return@launch
        }
        val tag = withContext(Dispatchers.IO) { app.updates.fetchLatestTag() }
        app.settings.recordUpdateCheck(tag, now)
        applyTag(tag)
    }

    private fun applyTag(tag: String?) {
        val newer = tag?.takeIf { UpdateChecker.isNewer(it, BuildConfig.VERSION_NAME) }
        system.value = system.value.copy(third = newer)
    }
}
