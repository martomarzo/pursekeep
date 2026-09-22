package app.pursekeep.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseClassifierTest {
    @Test fun `201 booked`() = assertEquals(SendResult.Sent("booked"), ResponseClassifier.classify(201, """{"id":"x","status":"booked"}"""))
    @Test fun `201 needs_account`() = assertEquals(SendResult.Sent("needs_account"), ResponseClassifier.classify(201, """{"id":"x","status":"needs_account"}"""))
    @Test fun `200 duplicate`() = assertEquals(SendResult.Sent("duplicate"), ResponseClassifier.classify(200, """{"duplicate":true}"""))
    @Test fun `401`() = assertEquals(SendResult.Unauthorized, ResponseClassifier.classify(401, """{"error":"unauthorized"}"""))
    @Test fun `400 never retried`() = assertEquals(SendResult.Rejected("empty body"), ResponseClassifier.classify(400, """{"error":"empty body"}"""))
    @Test fun `500 retries`() = assertTrue(ResponseClassifier.classify(500, "boom") is SendResult.Retry)
    @Test fun `unparseable success body still counts as sent`() = assertEquals(SendResult.Sent("ok"), ResponseClassifier.classify(201, "<html>"))
}
