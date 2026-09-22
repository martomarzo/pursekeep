package app.pursekeep.outbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: OutboxEntry): Long

    @Query("SELECT * FROM outbox WHERE status = 'pending' ORDER BY createdAt ASC, id ASC")
    suspend fun pending(): List<OutboxEntry>

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'pending'")
    fun pendingCount(): Flow<Int>

    @Update
    suspend fun update(entry: OutboxEntry)

    @Query("DELETE FROM outbox WHERE status = 'sent' AND createdAt < :before")
    suspend fun pruneSent(before: Long)
}
