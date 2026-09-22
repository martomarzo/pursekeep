package app.pursekeep.outbox

import android.content.Context
import app.pursekeep.capture.AndroidCapture
import app.pursekeep.capture.CaptureExtractor
import app.pursekeep.capture.KnownApps
import app.pursekeep.capture.NotificationInput
import app.pursekeep.settings.Settings
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class CaptureRepository(private val context: Context, private val db: OutboxDb, private val settings: Settings) {
    private val json = Json { encodeDefaults = true }

    suspend fun onNotification(input: NotificationInput) {
        val enabled = settings.enabledPackages.first()
        val capture = CaptureExtractor.extract(input, enabled) ?: return
        enqueue(capture, source = "notification")
    }

    suspend fun enqueue(capture: AndroidCapture, source: String) {
        val entry = OutboxEntry(
            payloadJson = json.encodeToString(AndroidCapture.serializer(), capture),
            hash = CaptureExtractor.hash(capture),
            createdAt = System.currentTimeMillis(),
        )
        val id = db.outbox().insert(entry)
        if (id == -1L) { log("skip", "Duplicate ignored: ${capture.title}"); return }
        log("captured", "[$source] ${capture.title} — ${capture.text.take(60)}")
        OutboxFlushWorker.enqueue(context)
    }

    suspend fun sendTest() = enqueue(testCapture(CaptureExtractor.nowIso()), source = "test")

    suspend fun log(kind: String, message: String) {
        db.events().insert(EventEntry(at = System.currentTimeMillis(), kind = kind, message = message))
        db.events().trim()
    }

    companion object {
        /** Must stay byte-for-byte equal to android/contract/fixtures/android-wallet-test.json (minus postedAt). */
        fun testCapture(postedAt: String) = AndroidCapture(
            app = KnownApps.GOOGLE_WALLET,
            title = "€0.01 with Visa ••0000",
            text = "PurseKeep test",
            postedAt = postedAt,
        )
    }
}
