package io.github.stephenjoy98.heatmap.data

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/** A recorded dot with its height, for 3D routes. */
class ElevationPoint(val lat: Double, val lon: Double, val altitude: Double)

/**
 * Turns routes with heights into 3D geometry: each hop between two dots becomes a thin strip [heightM]
 * above the ground, so a map that can extrude shapes shows the route rising and falling. Heights are measured
 * from the lowest dot shown and exaggerated a little, so hills read clearly next to 3D buildings, and GPS
 * height jitter is smoothed over neighbouring dots first.
 */
object Route3d {
    /** One hop of a route: its four corners (lat, lon pairs) and its height above the ground in metres. */
    class Segment(val corners: DoubleArray, val heightM: Double)

    private const val EXAGGERATION = 1.5
    // Flat routes still float just above the ground, so they're visible.
    private const val LIFT_M = 3.0
    private const val HALF_WIDTH_M = 2.5
    private const val SMOOTH_RADIUS = 2
    private const val METERS_PER_DEGREE = 111_320.0

    fun build(tracks: List<List<ElevationPoint>>, maxSegments: Int = 8_000): List<Segment> {
        val routes = tracks.filter { it.size >= 2 }
        if (routes.isEmpty()) return emptyList()
        val smoothed = routes.map(::smooth)
        val lowest = smoothed.minOf { it.min() }
        val hops = routes.sumOf { it.size - 1 }
        // Thin out very long histories so the map stays responsive.
        val step = max(1, (hops + maxSegments - 1) / maxSegments)
        val out = ArrayList<Segment>(minOf(hops, maxSegments) + 1)
        routes.forEachIndexed { r, pts ->
            val heights = smoothed[r]
            var i = 0
            while (i < pts.size - 1) {
                val j = minOf(i + step, pts.size - 1)
                val a = pts[i]
                val b = pts[j]
                val h = ((heights[i] + heights[j]) / 2 - lowest) * EXAGGERATION + LIFT_M
                strip(a, b)?.let { out += Segment(it, h) }
                i = j
            }
        }
        return out
    }

    /** Moving average of each dot's height with its neighbours on the same route. */
    private fun smooth(pts: List<ElevationPoint>): DoubleArray = DoubleArray(pts.size) { i ->
        var sum = 0.0
        var n = 0
        for (k in maxOf(0, i - SMOOTH_RADIUS)..minOf(pts.lastIndex, i + SMOOTH_RADIUS)) {
            sum += pts[k].altitude
            n++
        }
        sum / n
    }

    /** A thin rectangle around the hop from [a] to [b], or null if they're on the same spot. */
    private fun strip(a: ElevationPoint, b: ElevationPoint): DoubleArray? {
        val metersPerLon = METERS_PER_DEGREE * cos(Math.toRadians((a.lat + b.lat) / 2))
        val dx = (b.lon - a.lon) * metersPerLon
        val dy = (b.lat - a.lat) * METERS_PER_DEGREE
        val len = sqrt(dx * dx + dy * dy)
        if (len < 0.5) return null
        // Sideways offset, half the strip's width, back in degrees.
        val offLat = dx / len * HALF_WIDTH_M / METERS_PER_DEGREE
        val offLon = -dy / len * HALF_WIDTH_M / metersPerLon
        return doubleArrayOf(
            a.lat + offLat, a.lon + offLon,
            b.lat + offLat, b.lon + offLon,
            b.lat - offLat, b.lon - offLon,
            a.lat - offLat, a.lon - offLon,
        )
    }
}
