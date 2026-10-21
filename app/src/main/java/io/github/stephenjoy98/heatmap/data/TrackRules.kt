package io.github.stephenjoy98.heatmap.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** Decides whether two consecutive fixes belong to the same route or start a new one. */
object TrackRules {
    private const val MIN_GAP_MS = 90_000L
    private const val MAX_GAP_MS = 15 * 60_000L
    private const val MAX_SPEED_MPS = 70.0 // ~250 km/h; faster jumps are glitches or flights
    // GPS jumps shorter than this don't break a route: road snapping (or the next fixes) put it right.
    private const val MIN_JUMP_BREAK_M = 1_000.0
    // Moving like a vehicle (3–45 m/s on average), a gap of up to this long is a tunnel or lost signal, not a
    // stop, so the route carries on and road snapping fills it in.
    private const val VEHICLE_GAP_MS = 10 * 60_000L

    /**
     * Fixes are joined when the gap is at most three ticks (so one or two missed fixes don't break a
     * route), but never more than 15 minutes apart: hourly or daily samples are shown as dots,
     * because a straight line between them wouldn't follow any real road. At vehicle speed a gap of up to
     * 10 minutes (a tunnel) and GPS jumps under 1 km don't break a route either; road snapping straightens
     * them out.
     */
    fun isBreak(
        prevTime: Long, prevLat: Double, prevLon: Double,
        time: Long, lat: Double, lon: Double,
        intervalMs: Int,
    ): Boolean {
        val gap = time - prevTime
        if (gap <= 0) return true
        val meters = distanceMeters(prevLat, prevLon, lat, lon)
        val speed = meters / (gap / 1000.0)
        if (gap > maxGapMs(intervalMs)) {
            // Lost signal at speed (a tunnel, say): keep going if it still looks like the same drive.
            return !(gap <= VEHICLE_GAP_MS && speed in 3.0..45.0)
        }
        return speed > MAX_SPEED_MPS && meters > MIN_JUMP_BREAK_M
    }

    /** The longest gap between two fixes that still counts as one continuous route. */
    fun maxGapMs(intervalMs: Int): Long = (intervalMs * 3L).coerceAtLeast(MIN_GAP_MS).coerceAtMost(MAX_GAP_MS)

    /** Equirectangular approximation; plenty accurate over the short hops between fixes. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rad = PI / 180
        val x = (lon2 - lon1) * rad * cos((lat1 + lat2) / 2 * rad)
        val y = (lat2 - lat1) * rad
        return sqrt(x * x + y * y) * 6_371_000.0
    }
}
