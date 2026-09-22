package app.pursekeep

import android.app.Application
import app.pursekeep.net.CaptureApi
import app.pursekeep.net.UpdateChecker
import app.pursekeep.outbox.CaptureRepository
import app.pursekeep.outbox.OutboxDb
import app.pursekeep.settings.Settings

class PurseKeepApp : Application() {
    lateinit var db: OutboxDb; private set
    lateinit var settings: Settings; private set
    lateinit var api: CaptureApi; private set
    lateinit var captures: CaptureRepository; private set
    lateinit var updates: UpdateChecker; private set

    override fun onCreate() {
        super.onCreate()
        db = OutboxDb.build(this)
        settings = Settings(this)
        api = CaptureApi(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        captures = CaptureRepository(this, db, settings)
        updates = UpdateChecker()
        Notifications.ensureChannel(this)
    }
}
