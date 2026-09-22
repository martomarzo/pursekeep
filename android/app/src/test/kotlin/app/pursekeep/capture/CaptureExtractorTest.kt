package app.pursekeep.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class CaptureExtractorTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val enabled = setOf(KnownApps.GOOGLE_WALLET)
    private fun input(
        pkg: String = KnownApps.GOOGLE_WALLET,
        title: String? = "€12,40 with Visa ••1234",
        text: String? = "MERCADONA",
        bigText: String? = null,
        summary: Boolean = false,
        ongoing: Boolean = false,
    ) = NotificationInput(pkg, 1_790_000_000_000L, title, text, bigText, summary, ongoing)

    @Test fun `extracts wallet notification with offset timestamp`() {
        val c = CaptureExtractor.extract(input(), enabled, zone)!!
        assertEquals("android_notification", c.kind)
        assertEquals(KnownApps.GOOGLE_WALLET, c.app)
        assertEquals("€12,40 with Visa ••1234", c.title)
        assertEquals("MERCADONA", c.text)
        assertEquals("2026-09-21T16:13:20+02:00", c.postedAt)
    }

    @Test fun `skips disabled package`() = assertNull(CaptureExtractor.extract(input(pkg = "com.example"), enabled, zone))
    @Test fun `skips group summary`() = assertNull(CaptureExtractor.extract(input(summary = true), enabled, zone))
    @Test fun `skips ongoing`() = assertNull(CaptureExtractor.extract(input(ongoing = true), enabled, zone))
    @Test fun `skips blank`() = assertNull(CaptureExtractor.extract(input(title = " ", text = null), enabled, zone))

    @Test fun `prefers bigText over text`() {
        val c = CaptureExtractor.extract(input(text = "short", bigText = "the long version"), enabled, zone)!!
        assertEquals("the long version", c.text)
    }

    @Test fun `truncates to contract limits`() {
        val c = CaptureExtractor.extract(input(title = "t".repeat(3000), text = "x".repeat(5000)), enabled, zone)!!
        assertEquals(2000, c.title.length)
        assertEquals(4000, c.text.length)
    }

    @Test fun `hash is stable and sensitive to text`() {
        val a = CaptureExtractor.extract(input(), enabled, zone)!!
        val b = CaptureExtractor.extract(input(text = "LIDL"), enabled, zone)!!
        assertEquals(64, CaptureExtractor.hash(a).length)
        assertEquals(CaptureExtractor.hash(a), CaptureExtractor.hash(a.copy()))
        assert(CaptureExtractor.hash(a) != CaptureExtractor.hash(b))
    }
}
