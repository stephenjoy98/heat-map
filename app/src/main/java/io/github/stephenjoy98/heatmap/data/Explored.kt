package io.github.stephenjoy98.heatmap.data

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

/**
 * Measures distance over new ground only. Fixes are fed in time order; the route between consecutive fixes
 * (split with the same [TrackRules] as the map) is walked in short steps over a ~60 m grid, and a step only
 * counts if its grid square hasn't been visited before. Going down the same street again adds nothing,
 * while a parallel street a block over does. Steps are only added up for fixes in [countFrom, countTo), but
 * everything earlier still marks ground as visited, so a year's figure is the ground first covered that year.
 */
class ExploredDistance(private val countFrom: Long = 0L, private val countTo: Long = Long.MAX_VALUE) {
    private val visited = HashSet<Long>()

    // The square the route is passing through for the first time, if any: steps count until it leaves, up
    // to the square's diagonal, so GPS jitter while standing still in a new square can't keep adding up.
    private var firstPass: Long? = null
    private var firstPassMeters = 0.0
    private var hasPrev = false
    private var prevTime = 0L
    private var prevLat = 0.0
    private var prevLon = 0.0

    /** Metres of new ground covered so far, within the counting window. */
    var meters = 0.0
        private set

    fun add(time: Long, lat: Double, lon: Double, intervalMs: Int) {
        if (hasPrev && !TrackRules.isBreak(prevTime, prevLat, prevLon, time, lat, lon, intervalMs)) {
            val d = TrackRules.distanceMeters(prevLat, prevLon, lat, lon)
            val steps = ceil(d / STEP_M).toInt().coerceAtLeast(1)
            val stepLength = d / steps
            val counting = time in countFrom until countTo
            for (i in 0 until steps) {
                // The middle of each step decides which square it belongs to.
                val f = (i + 0.5) / steps
                val cell = cellOf(prevLat + (lat - prevLat) * f, prevLon + (lon - prevLon) * f)
                when {
                    visited.add(cell) -> {
                        firstPass = cell
                        firstPassMeters = 0.0
                    }
                    cell != firstPass -> firstPass = null
                }
                if (firstPass != null && firstPassMeters < MAX_PER_CELL_M) {
                    val credit = minOf(stepLength, MAX_PER_CELL_M - firstPassMeters)
                    firstPassMeters += credit
                    if (counting) meters += credit
                }
            }
        } else {
            // The start of a new route: no distance yet, but leaving a square never seen before counts.
            val cell = cellOf(lat, lon)
            firstPass = if (visited.add(cell)) cell else null
            firstPassMeters = 0.0
        }
        hasPrev = true
        prevTime = time
        prevLat = lat
        prevLon = lon
    }

    private fun cellOf(lat: Double, lon: Double): Long {
        val row = floor(lat * METERS_PER_DEGREE / CELL_M).toLong()
        // Squares stay roughly square away from the equator: narrower in degrees of longitude.
        val rowLat = (row + 0.5) * CELL_M / METERS_PER_DEGREE
        val col = floor(lon * METERS_PER_DEGREE * cos(rowLat * PI / 180) / CELL_M).toLong()
        return (row shl 32) or (col and 0xFFFFFFFFL)
    }

    private companion object {
        const val CELL_M = 60.0
        const val STEP_M = 20.0
        val MAX_PER_CELL_M = CELL_M * kotlin.math.sqrt(2.0)
        const val METERS_PER_DEGREE = 111_320.0
    }
}
