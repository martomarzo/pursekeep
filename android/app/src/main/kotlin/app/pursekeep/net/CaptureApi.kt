package app.pursekeep.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object ResponseClassifier {
    fun classify(code: Int, body: String): SendResult = when {
        code == 200 || code == 201 -> SendResult.Sent(parseStatus(body))
        code == 401 -> SendResult.Unauthorized
        code == 408 || code == 425 || code == 429 -> SendResult.Retry("HTTP $code")
        code in 400..499 -> SendResult.Rejected(parseError(body) ?: "HTTP $code")
        else -> SendResult.Retry("HTTP $code")
    }

    private fun parseStatus(body: String): String = runCatching {
        val obj = Json.parseToJsonElement(body).jsonObject
        if (obj["duplicate"]?.jsonPrimitive?.booleanOrNull == true) "duplicate"
        else obj["status"]?.jsonPrimitive?.contentOrNull ?: "ok"
    }.getOrDefault("ok")

    private fun parseError(body: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}

class CaptureApi(
    private val clientVersionName: String,
    private val clientVersionCode: Int,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Blocking; call from Dispatchers.IO. */
    fun send(serverUrl: String, token: String, payloadJson: String): SendResult {
        val request = Request.Builder()
            .url(serverUrl.trimEnd('/') + "/api/wallet/capture")
            .header("Authorization", "Bearer $token")
            .header("X-PurseKeep-Client", "android/$clientVersionName+$clientVersionCode")
            .header("User-Agent", "PurseKeep-Android/$clientVersionName")
            .post(payloadJson.toRequestBody(jsonType))
            .build()
        return try {
            client.newCall(request).execute().use { r -> ResponseClassifier.classify(r.code, r.body.string()) }
        } catch (e: IOException) {
            SendResult.Retry(e.message ?: e.javaClass.simpleName)
        }
    }
}
