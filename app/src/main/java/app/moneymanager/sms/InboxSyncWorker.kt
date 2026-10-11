package app.moneymanager.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.moneymanager.MoneyManagerApp
import app.moneymanager.util.Permissions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Reads the SMS inbox and imports anything new.
 *  - full = true: whole inbox (first run, "Rescan inbox"), with progress for the UI.
 *  - full = false: the last few days only (every ~6 hours, catches anything the live receiver missed).
 */
class InboxSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyManagerApp
        val c = app.container
        if (!Permissions.hasSms(applicationContext)) return Result.success()
        val full = inputData.getBoolean(KEY_FULL, false)
        val prefs = c.settings.current()
        val since = if (full || prefs.lastSync == 0L) 0L else prefs.lastSync - 3 * DAY
        val started = System.currentTimeMillis()
        val messages = SmsReader.readInbox(applicationContext, since)
        setProgress(workDataOf(PROGRESS_DONE to 0, PROGRESS_TOTAL to messages.size, PROGRESS_ADDED to 0))
        val stats = c.ingestor.ingest(messages) { done, s ->
            setProgress(workDataOf(PROGRESS_DONE to done, PROGRESS_TOTAL to messages.size, PROGRESS_ADDED to s.added))
        }
        c.settings.setLastSync(started)
        if (full) c.settings.setInitialScanDone(true)
        return Result.success(
            workDataOf(
                PROGRESS_TOTAL to messages.size,
                PROGRESS_ADDED to stats.added,
                RESULT_DUPLICATES to stats.duplicates,
                RESULT_UNPARSED to stats.unparsed,
            ),
        )
    }

    companion object {
        private const val DAY = 24 * 3600 * 1000L
        const val KEY_FULL = "full"
        const val PROGRESS_DONE = "done"
        const val PROGRESS_TOTAL = "total"
        const val PROGRESS_ADDED = "added"
        const val RESULT_DUPLICATES = "duplicates"
        const val RESULT_UNPARSED = "unparsed"
        private const val FULL_SCAN = "inbox-full-scan"
        private const val PERIODIC = "inbox-periodic-sync"

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<InboxSyncWorker>(6, TimeUnit.HOURS).build(),
            )
        }

        fun runFullScan(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                FULL_SCAN,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<InboxSyncWorker>().setInputData(workDataOf(KEY_FULL to true)).build(),
            )
        }

        fun observeFullScan(context: Context): Flow<WorkInfo?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(FULL_SCAN).map { list ->
                list.maxByOrNull { if (it.state.isFinished) 0 else 1 }
            }
    }
}
