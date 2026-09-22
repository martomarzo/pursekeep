package app.pursekeep.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
) {
    /** Blocking. Returns the latest release tag (e.g. "android-v0.2.0") or null on any failure. */
    fun fetchLatestTag(): String? = runCatching {
        val req = Request.Builder().url(LATEST_URL).header("Accept", "application/vnd.github+json").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            Json.parseToJsonElement(r.body.string()).jsonObject["tag_name"]?.jsonPrimitive?.contentOrNull
        }
    }.getOrNull()

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/martomarzo/money-maker/releases/latest"
        const val RELEASES_PAGE = "https://github.com/martomarzo/money-maker/releases/latest"
        private const val PREFIX = "android-v"

        fun isNewer(tag: String, current: String): Boolean {
            if (!tag.startsWith(PREFIX)) return false
            val a = parse(tag.removePrefix(PREFIX)) ?: return false
            val b = parse(current) ?: return false
            for (i in 0 until 3) if (a[i] != b[i]) return a[i] > b[i]
            return false
        }

        private fun parse(v: String): List<Int>? =
            v.split(".").map { it.toIntOrNull() ?: return null }.takeIf { it.size == 3 }
    }
}
