package app.pursekeep.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.pursekeep.capture.KnownApps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

data class Pairing(val serverUrl: String, val token: String, val deviceName: String)

class Settings(private val context: Context) {
    private object K {
        val serverUrl = stringPreferencesKey("serverUrl")
        val token = stringPreferencesKey("token")
        val deviceName = stringPreferencesKey("deviceName")
        val paired = booleanPreferencesKey("paired")
        val broken = booleanPreferencesKey("pairingBroken")
        val enabled = stringSetPreferencesKey("enabledPackages")
        val custom = stringSetPreferencesKey("customPackages")
        val lastSentAt = longPreferencesKey("lastSentAt")
        val lastUpdateCheck = longPreferencesKey("lastUpdateCheck")
        val latestTag = stringPreferencesKey("latestTag")
    }

    private val data get() = context.store.data

    val pairing: Flow<Pairing?> = data.map { p ->
        val url = p[K.serverUrl]; val tok = p[K.token]
        if (url != null && tok != null && p[K.paired] == true) Pairing(url, tok, p[K.deviceName].orEmpty()) else null
    }
    val pairingBroken: Flow<Boolean> = data.map { it[K.broken] == true }
    val serverUrl: Flow<String?> = data.map { it[K.serverUrl] }
    val enabledPackages: Flow<Set<String>> = data.map { it[K.enabled] ?: KnownApps.defaultEnabled }
    val customPackages: Flow<Set<String>> = data.map { it[K.custom] ?: emptySet() }
    val lastSentAt: Flow<Long?> = data.map { it[K.lastSentAt] }
    val latestKnownTag: Flow<String?> = data.map { it[K.latestTag] }

    suspend fun pair(url: String, token: String, name: String) = context.store.edit {
        it[K.serverUrl] = url; it[K.token] = token; it[K.deviceName] = name; it[K.paired] = true; it[K.broken] = false
    }
    suspend fun unpair() = context.store.edit { it.remove(K.token); it[K.paired] = false; it[K.broken] = false }
    /** Server said 401: keep URL/name for display, drop the token. */
    suspend fun markUnpaired() = context.store.edit { it.remove(K.token); it[K.paired] = false; it[K.broken] = true }

    suspend fun setPackageEnabled(pkg: String, on: Boolean) = context.store.edit {
        val cur = it[K.enabled] ?: KnownApps.defaultEnabled
        it[K.enabled] = if (on) cur + pkg else cur - pkg
    }
    suspend fun addCustomPackage(pkg: String) = context.store.edit {
        it[K.custom] = (it[K.custom] ?: emptySet()) + pkg
        it[K.enabled] = (it[K.enabled] ?: KnownApps.defaultEnabled) + pkg
    }
    suspend fun removeCustomPackage(pkg: String) = context.store.edit {
        it[K.custom] = (it[K.custom] ?: emptySet()) - pkg
        it[K.enabled] = (it[K.enabled] ?: KnownApps.defaultEnabled) - pkg
    }
    suspend fun setLastSentAt(ms: Long) = context.store.edit { it[K.lastSentAt] = ms }
    suspend fun recordUpdateCheck(tag: String?, atMs: Long) = context.store.edit {
        it[K.lastUpdateCheck] = atMs; if (tag != null) it[K.latestTag] = tag
    }
    suspend fun lastUpdateCheckAt(): Long = data.first()[K.lastUpdateCheck] ?: 0L
}
