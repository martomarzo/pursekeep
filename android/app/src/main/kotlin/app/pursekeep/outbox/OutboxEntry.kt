package app.pursekeep.outbox

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "outbox", indices = [Index(value = ["hash"], unique = true)])
data class OutboxEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val hash: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val status: String = PENDING,
    val lastError: String? = null,
    val serverStatus: String? = null,
) {
    companion object {
        const val PENDING = "pending"
        const val SENT = "sent"
        const val FAILED = "failed"
    }
}
