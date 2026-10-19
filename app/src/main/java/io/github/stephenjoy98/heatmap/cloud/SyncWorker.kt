package io.github.stephenjoy98.heatmap.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.roads.RoadSnapper
import io.github.stephenjoy98.heatmap.tracking.TripDetector
import java.util.concurrent.TimeUnit

/**
 * Backup and sharing of routes and stats with friends. Runs whenever the app opens, which starts a backup
 * straight away, and in the background on the schedule chosen under Backup (every 6 or 12 hours, daily or
 * weekly), which backs up once it's due.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!FirebaseGate.isConfigured(applicationContext) || FirebaseAuth.getInstance().currentUser == null) {
            return Result.success()
        }
        return try {
            TripDetector.process(applicationContext)
            runCatching { RoadSnapper.processPending(applicationContext) }
            // Opening the app backs up at once (unless one just ran, when hopping between apps); the
            // schedule waits until it's due. Backups only send the days changed since the last one.
            val dueAfter = if (inputData.getBoolean(KEY_APP_OPEN, false)) {
                APP_OPEN_GAP_MS
            } else {
                Prefs.backupEveryHours.value * 3_600_000L * 9 / 10
            }
            if (Prefs.autoBackup.value && System.currentTimeMillis() - Prefs.lastBackup.value >= dueAfter) {
                Backup.backUp(applicationContext)
            }
            Friends.syncMine(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_APP_OPEN = "app_open"
        private const val APP_OPEN_GAP_MS = 5 * 60_000L
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** (Re)schedules the periodic sync at the chosen backup frequency; call again after changing it. */
        fun schedule(context: Context) {
            val hours = Prefs.backupEveryHours.value.toLong()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(hours, TimeUnit.HOURS).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Syncs as soon as there's a connection; [appOpen] also starts a backup (when automatic backup is on). */
        fun runNow(context: Context, appOpen: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_APP_OPEN to appOpen))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
