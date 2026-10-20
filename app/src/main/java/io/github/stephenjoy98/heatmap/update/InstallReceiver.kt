package io.github.stephenjoy98.heatmap.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Hears back from Android's installer. If it needs you to confirm (the first self-update, or on Android 11 and
 * earlier), the confirmation opens straight away while the app is on screen, or as a notification otherwise.
 * On success Android closes the app and replaces it; BootReceiver then restarts recording.
 */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val version = intent.getStringExtra(EXTRA_VERSION).orEmpty()
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                } ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val opened = Updater.inForeground && runCatching { context.startActivity(confirm) }.isSuccess
                if (!opened) {
                    val tap = PendingIntent.getActivity(context, 1, confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    Updater.notify(context, "Update ready to install", "Tap to install Location Ticker $version", tap)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // Android closes the app to replace it.
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.failed("The update to $version was cancelled")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"
                Updater.failed("Couldn't install $version: $message")
            }
        }
    }

    companion object {
        const val EXTRA_VERSION = "version"
    }
}
