package io.github.stephenjoy98.heatmap.update

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
import io.github.stephenjoy98.heatmap.data.Prefs
import java.util.concurrent.TimeUnit

/**
 * Looks for a new release when the app opens and every few hours. With automatic updates on, it downloads the
 * new version (on Wi-Fi only, if chosen) and installs it, waiting until you're not using the app. With them
 * off, it just lets you know.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val release = Updater.check(context) ?: return Result.success()
        if (Prefs.autoUpdate.value && !(Prefs.updateWifiOnly.value && Updater.onMeteredNetwork(context))) {
            Updater.downloadAndInstall(context, release, whenAway = true)
        } else if (Prefs.lastUpdateNotified != release.label + release.build) {
            Prefs.lastUpdateNotified = release.label + release.build
            Updater.notify(context, "Location Ticker ${release.label} is available", "Open Settings › Updates to install it")
        }
        return Result.success()
    }

    companion object {
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork("update-check-now", ExistingWorkPolicy.KEEP, request)
        }
    }
}
