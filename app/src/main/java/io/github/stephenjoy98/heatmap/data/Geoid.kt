package io.github.stephenjoy98.heatmap.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import androidx.annotation.WorkerThread
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.floor

/**
 * GPS satellites give heights above a mathematical ellipsoid, not above sea level. The two differ by the geoid
 * height, up to about ±100 m around the world (about −17 m around Vancouver, so GPS heights there read 17 m low).
 * Android 14 and later can convert with a geoid map kept on the phone (AltitudeConverter); this keeps the
 * difference for each ~10 km square, so every height the app shows or records is above sea level.
 *
 * On older Android there's no converter: [manualOffset] (set by matching the ground height in the height check)
 * is used instead.
 */
object Geoid {
    /** Whether the phone can convert GPS heights to sea level by itself (Android 14+). */
    val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    private const val CELL_DEG = 0.1
    private val offsets = ConcurrentHashMap<Long, Double>()
    private val pending = ConcurrentHashMap.newKeySet<Long>()
    private val background = Executors.newSingleThreadExecutor()
    @SuppressLint("StaticFieldLeak") // The application context.
    private var app: Context? = null
    private var converter: Any? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    private fun cell(lat: Double, lon: Double): Long =
        (floor((lat + 90) / CELL_DEG).toLong() shl 32) or floor((lon + 180) / CELL_DEG).toLong()

    /**
     * Sea level minus the GPS ellipsoid at a spot, in metres (add it to a GPS height to get the height above sea
     * level), or null where the phone can't tell. Blocks while it reads the geoid map: not on the main thread.
     */
    @WorkerThread
    fun offset(lat: Double, lon: Double): Double? {
        // Spelled out (rather than [supported]) so the build's API check can see it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val key = cell(lat, lon)
        offsets[key]?.let { return it }
        val context = app ?: return null
        return runCatching {
            val c = synchronized(this) {
                (converter ?: android.location.altitude.AltitudeConverter().also { converter = it }) as android.location.altitude.AltitudeConverter
            }
            // A height of 0 above the ellipsoid comes back as minus the geoid height above sea level.
            val probe = Location("geoid").apply {
                latitude = floor((lat + 90) / CELL_DEG) * CELL_DEG - 90 + CELL_DEG / 2
                longitude = floor((lon + 180) / CELL_DEG) * CELL_DEG - 180 + CELL_DEG / 2
                altitude = 0.0
            }
            c.addMslAltitudeToLocation(context, probe)
            probe.mslAltitudeMeters
        }.getOrNull()?.also { offsets[key] = it }
    }

    /** [offset] if it's known already; otherwise null now, and it's worked out in the background for next time. */
    fun cachedOffset(lat: Double, lon: Double): Double? {
        if (!supported) return null
        val key = cell(lat, lon)
        offsets[key]?.let { return it }
        if (pending.add(key)) background.execute { offset(lat, lon); pending.remove(key) }
        return null
    }

    /** The difference to add on phones without the converter, from matching the ground height; 0 until set. */
    val manualOffset: Double get() = Prefs.altitudeOffset

    /**
     * Once: heights recorded before the conversion existed were GPS (ellipsoid) heights, so they're moved to sea
     * level now, square by square. Dots recorded since are converted as they come in. Every day is backed up again
     * with the corrected heights. Runs off the main thread.
     */
    @WorkerThread
    @Synchronized
    fun correctHistory(store: PointStore) {
        if (!supported || Prefs.altitudeCorrected) return
        val cutoff = Prefs.altitudeCorrectionCutoff
        val cells = store.altitudeCells(cutoff, CELL_DEG)
        var all = true
        for ((row, col) in cells) {
            val lat = row * CELL_DEG - 90 + CELL_DEG / 2
            val lon = col * CELL_DEG - 180 + CELL_DEG / 2
            val off = offset(lat, lon)
            if (off == null) {
                all = false
                continue
            }
            store.shiftAltitudes(cutoff, CELL_DEG, row, col, off)
        }
        // If the geoid map couldn't be read (rare), try again next time rather than leave some squares uncorrected.
        if (all) {
            Prefs.setAltitudeCorrected()
            if (cells.isNotEmpty()) Prefs.resetBackupComplete()
        }
    }
}
