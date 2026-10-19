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

/** Daily (and on app open) backup plus sharing of routes and stats with friends. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!FirebaseGate.isConfigured(applicationContext) || FirebaseAuth.getInstance().currentUser == null) {
            return Result.success()
        }
        return try {
            TripDetector.process(applicationContext)
            runCatching { RoadSnapper.processPending(applicationContext) }
            if (Prefs.autoBackup.value) Backup.backUp(applicationContext)
            Friends.syncMine(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
