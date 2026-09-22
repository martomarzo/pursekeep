package app.pursekeep.outbox

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pursekeep.Notifications
import app.pursekeep.PurseKeepApp
import app.pursekeep.net.SendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class OutboxFlushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as PurseKeepApp
        val pairing = app.settings.pairing.first() ?: return@withContext Result.success()
        val dao = app.db.outbox()
        val now = System.currentTimeMillis()
        var retry = false
        for (entry in dao.pending()) {
            if (now - entry.createdAt > MAX_AGE_MS) {
                dao.update(entry.copy(status = OutboxEntry.FAILED, lastError = "expired"))
                app.captures.log("rejected", "Expired after 30 days: ${entry.hash.take(8)}")
                continue
            }
            when (val r = app.api.send(pairing.serverUrl, pairing.token, entry.payloadJson)) {
                is SendResult.Sent -> {
                    dao.update(entry.copy(status = OutboxEntry.SENT, serverStatus = r.serverStatus, attempts = entry.attempts + 1, lastError = null))
                    app.settings.setLastSentAt(System.currentTimeMillis())
                    app.captures.log("sent", "→ ${r.serverStatus}")
                }
                SendResult.Unauthorized -> {
                    app.settings.markUnpaired()
                    Notifications.showUnpaired(applicationContext)
                    app.captures.log("unpaired", "Server rejected the token (401)")
                    return@withContext Result.success()
                }
                is SendResult.Rejected -> {
                    dao.update(entry.copy(status = OutboxEntry.FAILED, lastError = r.error, attempts = entry.attempts + 1))
                    app.captures.log("rejected", r.error)
                }
                is SendResult.Retry -> {
                    dao.update(entry.copy(attempts = entry.attempts + 1, lastError = r.reason))
                    app.captures.log("retry", r.reason)
                    retry = true
                    break
                }
            }
        }
        dao.pruneSent(before = now - MAX_AGE_MS)
        if (retry) Result.retry() else Result.success()
    }

    companion object {
        private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val NAME = "flush-outbox"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<OutboxFlushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // APPEND_OR_REPLACE: if a flush is running, run another pass after it (a row may have
            // been inserted after the running pass read its list).
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
