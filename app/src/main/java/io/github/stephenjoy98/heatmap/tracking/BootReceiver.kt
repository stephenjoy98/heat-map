package io.github.stephenjoy98.heatmap.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.stephenjoy98.heatmap.data.Prefs

/** Resumes recording after a reboot or app update if it was on before. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
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
