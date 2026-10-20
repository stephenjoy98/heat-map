package io.github.stephenjoy98.heatmap

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import io.github.stephenjoy98.heatmap.cloud.Account
import io.github.stephenjoy98.heatmap.cloud.FirebaseGate
import io.github.stephenjoy98.heatmap.cloud.SyncWorker
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService
import io.github.stephenjoy98.heatmap.tracking.TripDetector
import io.github.stephenjoy98.heatmap.update.UpdateWorker
import io.github.stephenjoy98.heatmap.update.Updater
import org.maplibre.android.MapLibre

class TickerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)

        MapLibre.getInstance(this)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                LocationTickerService.CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.channel_description) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                TripDetector.QUESTIONS_CHANNEL_ID,
                getString(R.string.questions_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = getString(R.string.questions_channel_description) }
        )

        nm.createNotificationChannel(
            NotificationChannel(Updater.CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New versions of Location Ticker, and when one is ready to install"
            }
        )
        UpdateWorker.schedule(this)

        if (FirebaseGate.isConfigured(this)) {
            Account.init(this)
            SyncWorker.schedule(this)
        }
    }
}
