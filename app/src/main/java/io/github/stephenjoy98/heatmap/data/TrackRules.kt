package io.github.stephenjoy98.heatmap.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** Decides whether two consecutive fixes belong to the same route or start a new one. */
object TrackRules {
    private const val MIN_GAP_MS = 90_000L
    private const val MAX_GAP_MS = 15 * 60_000L
    private const val MAX_SPEED_MPS = 70.0 // ~250 km/h; faster jumps are glitches or flights

    /**
     * Fixes are joined when the gap is at most three ticks (so one or two missed fixes don't break a
     * route), but never more than 15 minutes apart: hourly or daily samples are shown as dots,
     * because a straight line between them wouldn't follow any real road.
     */
    fun isBreak(
        prevTime: Long, prevLat: Double, prevLon: Double,
        time: Long, lat: Double, lon: Double,
        intervalMs: Int,
    ): Boolean {
        val gap = time - prevTime
        val maxGap = (intervalMs * 3L).coerceAtLeast(MIN_GAP_MS).coerceAtMost(MAX_GAP_MS)
        if (gap <= 0 || gap > maxGap) return true
        return distanceMeters(prevLat, prevLon, lat, lon) / (gap / 1000.0) > MAX_SPEED_MPS
    }

    /** Equirectangular approximation; plenty accurate over the short hops between fixes. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rad = PI / 180
        val x = (lon2 - lon1) * rad * cos((lat1 + lat2) / 2 * rad)
        val y = (lat2 - lat1) * rad
        return sqrt(x * x + y * y) * 6_371_000.0
    }
}
