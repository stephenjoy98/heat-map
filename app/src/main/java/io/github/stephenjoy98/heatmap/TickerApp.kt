package io.github.stephenjoy98.heatmap

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService
import org.osmdroid.config.Configuration
import java.io.File

class TickerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)

        Configuration.getInstance().apply {
            load(this@TickerApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }

        val channel = NotificationChannel(
            LocationTickerService.CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
