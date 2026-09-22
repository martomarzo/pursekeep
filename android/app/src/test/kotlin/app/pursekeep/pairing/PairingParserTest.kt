package app.pursekeep.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingParserTest {
    private val ok = """{"v":1,"app":"pursekeep","url":"https://money-maker.peacock-snapper.ts.net/","token":"abc123"}"""

    @Test fun `parses valid payload and trims trailing slash`() {
        val p = PairingParser.parse(ok).getOrThrow()
        assertEquals("https://money-maker.peacock-snapper.ts.net", p.url)
        assertEquals("abc123", p.token)
    }
    @Test fun `rejects wrong version`() =
        assertTrue(PairingParser.parse(ok.replace("\"v\":1", "\"v\":2")).exceptionOrNull()!!.message!!.contains("version"))
    @Test fun `rejects other app`() =
        assertTrue(PairingParser.parse(ok.replace("pursekeep", "other")).isFailure)
    @Test fun `rejects http`() =
        assertTrue(PairingParser.parse(ok.replace("https://", "http://")).exceptionOrNull()!!.message!!.contains("https"))
    @Test fun `rejects empty token`() =
        assertTrue(PairingParser.parse(ok.replace("abc123", " ")).isFailure)
    @Test fun `rejects garbage`() = assertTrue(PairingParser.parse("hello").isFailure)
    @Test fun `manual entry validates the same way`() {
        assertEquals("https://x.example", PairingParser.manual(" https://x.example/ ", "t").getOrThrow().url)
        assertTrue(PairingParser.manual("x.example", "t").isFailure)
    }
}
