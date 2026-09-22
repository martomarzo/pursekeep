package app.pursekeep.outbox

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kind: String,     // captured | sent | retry | rejected | unpaired | skip | info
    val message: String,
)
