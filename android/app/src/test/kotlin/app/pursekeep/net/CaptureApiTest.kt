package app.pursekeep.net

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class CaptureApiTest {
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    private val api = CaptureApi("0.1.0", 1, client)
    private val body = """{"kind":"android_notification","app":"a","title":"t","text":"x","postedAt":"2026-09-22T13:05:00+02:00"}"""

    @Test fun `posts the payload with the contract headers`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"1","status":"needs_account"}"""))
        server.start()
        try {
            val result = api.send(server.url("/").toString(), "tok", body)
            assertEquals(SendResult.Sent("needs_account"), result)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/wallet/capture", req.path)
            assertEquals("Bearer tok", req.getHeader("Authorization"))
            assertEquals("android/0.1.0+1", req.getHeader("X-PurseKeep-Client"))
            assertEquals("PurseKeep-Android/0.1.0", req.getHeader("User-Agent"))
            assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
            assertEquals(body, req.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test fun `connection failure maps to Retry`() {
        val server = MockWebServer()
        server.start()
        val url = server.url("/").toString()
        server.shutdown()
        assertTrue(api.send(url, "tok", body) is SendResult.Retry)
    }
}
