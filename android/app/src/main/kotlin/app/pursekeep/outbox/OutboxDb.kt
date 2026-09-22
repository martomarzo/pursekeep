package app.pursekeep.outbox

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OutboxEntry::class, EventEntry::class], version = 1, exportSchema = false)
abstract class OutboxDb : RoomDatabase() {
    abstract fun outbox(): OutboxDao
    abstract fun events(): EventDao

    companion object {
        fun build(context: Context): OutboxDb =
            Room.databaseBuilder(context.applicationContext, OutboxDb::class.java, "pursekeep.db").build()
    }
}
