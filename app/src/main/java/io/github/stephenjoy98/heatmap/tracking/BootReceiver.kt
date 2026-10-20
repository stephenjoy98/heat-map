package io.github.stephenjoy98.heatmap.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.stephenjoy98.heatmap.BuildConfig
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.update.Updater

/** Resumes recording after a reboot or app update if it was on before, and tells you about an update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Updates can install while you're away from the app; say so, with a way back in.
            Updater.notify(context, "Location Ticker updated to ${BuildConfig.VERSION_NAME}", "Tap to see what's new")
        }
        if (!Prefs.tracking.value) return
        // Starting a location service from the background needs "Allow all the time".
        if (!LocationTickerService.hasLocationPermission(context) ||
            !LocationTickerService.hasBackgroundPermission(context)
        ) return
        try {
            LocationTickerService.start(context)
        } catch (e: Exception) {
            // The system refused a background start; the user can restart from the app.
        }
    }
}
