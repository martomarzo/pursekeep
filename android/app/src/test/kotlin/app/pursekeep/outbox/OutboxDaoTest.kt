package app.pursekeep.outbox

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutboxDaoTest {
    private lateinit var db: OutboxDb

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OutboxDb::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    private fun entry(hash: String, at: Long) = OutboxEntry(payloadJson = "{}", hash = hash, createdAt = at)

    @Test fun `duplicate hash is ignored`() = runTest {
        assertEquals(1L, db.outbox().insert(entry("h1", 10)))
        assertEquals(-1L, db.outbox().insert(entry("h1", 11)))
        assertEquals(1, db.outbox().pendingCount().first())
    }

    @Test fun `pending is oldest first and excludes sent`() = runTest {
        db.outbox().insert(entry("b", 20)); db.outbox().insert(entry("a", 10)); db.outbox().insert(entry("c", 30))
        val c = db.outbox().pending().first { it.hash == "c" }
        db.outbox().update(c.copy(status = OutboxEntry.SENT))
        assertEquals(listOf("a", "b"), db.outbox().pending().map { it.hash })
    }

    @Test fun `event log keeps newest 50`() = runTest {
        repeat(60) { db.events().insert(EventEntry(at = it.toLong(), kind = "k", message = "m$it")) }
        db.events().trim()
        val latest = db.events().latest().first()
        assertEquals(50, latest.size)
        assertEquals("m59", latest.first().message)
    }
}
