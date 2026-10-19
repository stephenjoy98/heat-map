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
import com.google.firebase.auth.FirebaseAuth
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.roads.RoadSnapper
import io.github.stephenjoy98.heatmap.tracking.TripDetector
import java.util.concurrent.TimeUnit

/**
 * Backup on the schedule chosen under Backup (every 6 or 12 hours, daily or weekly), plus sharing of routes
 * and stats with friends, which also runs whenever the app opens.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!FirebaseGate.isConfigured(applicationContext) || FirebaseAuth.getInstance().currentUser == null) {
            return Result.success()
        }
        return try {
            TripDetector.process(applicationContext)
            runCatching { RoadSnapper.processPending(applicationContext) }
            // App-open syncs come more often than the schedule; only back up once it's due.
            val dueAfter = Prefs.backupEveryHours.value * 3_600_000L * 9 / 10
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
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** (Re)schedules the periodic sync at the chosen backup frequency; call again after changing it. */
        fun schedule(context: Context) {
            val hours = Prefs.backupEveryHours.value.toLong()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(hours, TimeUnit.HOURS).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
