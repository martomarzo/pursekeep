package app.pursekeep.capture

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class NotificationInput(
    val packageName: String,
    val postTimeMillis: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
)

object CaptureExtractor {
    const val MAX_TITLE = 2000
    const val MAX_TEXT = 4000
    private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    fun extract(input: NotificationInput, enabledPackages: Set<String>, zone: ZoneId = ZoneId.systemDefault()): AndroidCapture? {
        if (input.packageName !in enabledPackages) return null
        if (input.isGroupSummary || input.isOngoing) return null
        val title = input.title.orEmpty().trim().take(MAX_TITLE)
        val body = input.bigText?.takeIf { it.isNotBlank() } ?: input.text.orEmpty()
        val text = body.trim().take(MAX_TEXT)
        if (title.isBlank() && text.isBlank()) return null
        return AndroidCapture(
            app = input.packageName.take(200),
            title = title,
            text = text,
            postedAt = isoAt(input.postTimeMillis, zone),
        )
    }

    fun isoAt(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(ISO)

    fun nowIso(zone: ZoneId = ZoneId.systemDefault()): String = isoAt(System.currentTimeMillis(), zone)

    fun hash(c: AndroidCapture): String = sha256("${c.app}|${c.postedAt}|${c.title}|${c.text}")

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
