package app.pursekeep.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PairingPayload(val v: Int, val app: String, val url: String, val token: String)

object PairingParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): Result<PairingPayload> = runCatching {
        val p = try { json.decodeFromString<PairingPayload>(raw) } catch (e: Exception) {
            throw IllegalArgumentException("Not a PurseKeep pairing code")
        }
        require(p.v == 1) { "Unsupported pairing version ${p.v} — update the app" }
        require(p.app == "pursekeep") { "Not a PurseKeep pairing code" }
        validated(p.url, p.token)
    }

    fun manual(url: String, token: String): Result<PairingPayload> = runCatching { validated(url, token) }

    private fun validated(url: String, token: String): PairingPayload {
        val u = url.trim().trimEnd('/')
        require(u.startsWith("https://") && u.length > "https://".length) { "Server URL must start with https://" }
        require(token.isNotBlank()) { "Missing token" }
        return PairingPayload(1, "pursekeep", u, token.trim())
    }
}
