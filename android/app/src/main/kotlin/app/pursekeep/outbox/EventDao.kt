package app.pursekeep.outbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert suspend fun insert(event: EventEntry)

    @Query("SELECT * FROM events ORDER BY at DESC, id DESC LIMIT 50")
    fun latest(): Flow<List<EventEntry>>

    @Query("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY at DESC, id DESC LIMIT 50)")
    suspend fun trim()
}
