package app.pursekeep

import app.pursekeep.capture.AndroidCapture
import app.pursekeep.outbox.CaptureRepository
import app.pursekeep.pairing.PairingParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ContractTest {
    private val json = Json { encodeDefaults = true }
    private fun fixture(name: String) = File("../contract/fixtures/$name").readText()
    private fun same(expectedJson: String, actualJson: String) =
        assertEquals(Json.parseToJsonElement(expectedJson), Json.parseToJsonElement(actualJson))

    @Test fun `wallet fixture serialises identically`() = same(
        fixture("android-wallet.json"),
        json.encodeToString(AndroidCapture.serializer(), AndroidCapture(app = "com.google.android.apps.walletnfcrel", title = "€12,40 with Visa ••1234", text = "MERCADONA", postedAt = "2026-09-22T13:05:00+02:00")),
    )

    @Test fun `test capture matches its fixture`() = same(
        fixture("android-wallet-test.json"),
        json.encodeToString(AndroidCapture.serializer(), CaptureRepository.testCapture("2026-09-22T13:05:00+02:00")),
    )

    @Test fun `bank fixture with quotes round-trips`() {
        val decoded = json.decodeFromString(AndroidCapture.serializer(), fixture("android-bank.json"))
        assertEquals("Paid €8.50 at \"La Farola\"", decoded.title)
        same(fixture("android-bank.json"), json.encodeToString(AndroidCapture.serializer(), decoded))
    }

    @Test fun `pairing fixture is accepted`() {
        val p = PairingParser.parse(fixture("qr-pairing.json")).getOrThrow()
        assertEquals("https://money-maker.peacock-snapper.ts.net", p.url)
    }
}
