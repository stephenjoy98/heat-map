package io.github.stephenjoy98.heatmap.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.LocationPoint
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.Units
import io.github.stephenjoy98.heatmap.ui.MainActivity
import io.github.stephenjoy98.heatmap.ui.formatInterval
import java.text.DateFormat
import java.util.Date

/**
 * Foreground service that asks for a location fix once per configured interval and stores each one.
 * Every start command (re)applies the current settings, so it is also how interval changes take effect.
 */
class LocationTickerService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var store: PointStore
    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler

    // Only touched on the worker thread.
    private var intervalMs = 60_000L
    private var maxAccuracyM = 100f
    private var lastSavedAt = 0L
    private var lastDetectedAt = 0L

    // Automatic interval: recent fixes, and since when they've stayed in one spot. Worker thread only.
    private val recent = ArrayDeque<Location>()
    private var stillSince = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::record)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        store = PointStore.get(this)
        worker = HandlerThread("location-ticker").apply { start() }
        handler = Handler(worker.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !hasLocationPermission(this)) {
            Prefs.setTracking(false)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(null),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            // Typically: started from the background without "Allow all the time" location access.
            Prefs.setTracking(false)
            stopSelf()
            return START_NOT_STICKY
        }

        Prefs.setTracking(true)
        ActivityTracker.start(this)
        ActivityTracker.onChange = { activity -> if (Prefs.autoInterval.value) handler.post { reconsider(activity, null) } }
        requestUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        ActivityTracker.stop(this)
        ActivityTracker.onChange = null
        val app = applicationContext
        handler.post { TripDetector.process(app) }
        worker.quitSafely()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission") // Checked in onStartCommand.
    /** The interval to use now: the fixed setting, or in automatic mode the last choice (1 minute to start). */
    @Volatile
    private var autoSec = AutoInterval.UNKNOWN_SEC

    private fun requestUpdates() {
        val seconds = if (Prefs.autoInterval.value) autoSec else Prefs.intervalSec.value
        Prefs.setLiveIntervalSec(seconds)
        val interval = seconds * 1000L
        val highAccuracy = Prefs.highAccuracy.value
        handler.post {
            intervalMs = interval
            maxAccuracyM = accuracyLimitM(highAccuracy)
            if (lastSavedAt == 0L) lastSavedAt = store.latest()?.time ?: 0L
        }

        val priority = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(interval)
            .setWaitForAccurateLocation(false)
            .build()
        fused.removeLocationUpdates(callback)
        fused.requestLocationUpdates(request, callback, worker.looper)
    }

    private fun record(location: Location) {
        if (location.hasAccuracy() && location.accuracy > maxAccuracyM) return
        // The fused provider can deliver early (e.g. when another app asks for location); keep the set pace.
        if (location.time - lastSavedAt < intervalMs * 9 / 10) return

        store.insert(
            LocationPoint(
                time = location.time,
                lat = location.latitude,
                lon = location.longitude,
                accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
                intervalMs = intervalMs.toInt(),
                activity = ActivityTracker.current,
                altitude = altitudeOf(location),
            )
        )
        lastSavedAt = location.time

        if (Prefs.autoInterval.value) reconsider(ActivityTracker.current, location)

        // Look for finished trips every couple of minutes rather than on every fix.
        if (location.time - lastDetectedAt > 2 * 60_000L) {
            lastDetectedAt = location.time
            TripDetector.process(this)
        }

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(location))
    }

    /** Automatic mode: picks a new interval from activity, speed and stillness; re-requests if it changed. */
    private fun reconsider(activity: Int, fix: Location?) {
        if (fix != null) {
            recent.addLast(fix)
            // Judge stillness over about the last minute, however often fixes come (every second when
            // driving, every minute when long still), keeping at least three fixes.
            while (recent.size > 3 && fix.time - recent.first().time > STILL_WINDOW_MS) recent.removeFirst()
            while (recent.size > 120) recent.removeFirst()
            val anchor = recent.first()
            val allClose = recent.all { it.distanceTo(anchor) < 40f }
            stillSince = if (allClose && recent.size >= 3) (if (stillSince == 0L) anchor.time else stillSince) else 0L
        }
        val last = recent.lastOrNull()
        val prev = recent.getOrNull(recent.size - 2)
        val speed = when {
            last != null && last.hasSpeed() -> last.speed
            last != null && prev != null && last.time > prev.time -> last.distanceTo(prev) / ((last.time - prev.time) / 1000f)
            else -> null
        }
        val stillFor = if (stillSince > 0L && last != null) last.time - stillSince else 0L
        val chosen = AutoInterval.choose(activity, speed, stillFor)
        if (chosen != autoSec) {
            autoSec = chosen
            ContextCompat.getMainExecutor(this).execute { if (Prefs.tracking.value) requestUpdates() }
        }
    }

    private fun buildNotification(last: Location?): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, LocationTickerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val every = (if (Prefs.autoInterval.value) "Auto · every " else "Every ") + formatInterval(Prefs.liveIntervalSec.value)
        val text = if (last == null) {
            "$every · waiting for first fix"
        } else {
            val time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(last.time))
            "$every · last fix $time (±${Units.length(last.accuracy.toDouble())})"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ticker)
            .setContentTitle("Recording location")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val STILL_WINDOW_MS = 60_000L

        /** Height above sea level where Android provides it (14+), otherwise GPS height; null if neither. */
        fun altitudeOf(location: Location): Double? = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude() -> location.mslAltitudeMeters
            location.hasAltitude() -> location.altitude
            else -> null
        }

        const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "io.github.stephenjoy98.heatmap.STOP"

        /** Fixes less accurate than this (in metres) aren't recorded. */
        fun accuracyLimitM(highAccuracy: Boolean): Float = if (highAccuracy) 100f else 500f

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun hasBackgroundPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        /** Starts recording, or applies changed settings if it is already running. */
        fun start(context: Context) {
            Prefs.setTracking(true)
            ContextCompat.startForegroundService(context, Intent(context, LocationTickerService::class.java))
        }

        fun stop(context: Context) {
            Prefs.setTracking(false)
            context.stopService(Intent(context, LocationTickerService::class.java))
        }
    }
}
