package io.github.stephenjoy98.heatmap.data

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/** A dot on a route with its height, for 3D routes. */
class ElevationPoint(val lat: Double, val lon: Double, val altitude: Double)

/**
 * Turns routes with heights into 3D geometry: a continuous ribbon [heightM] above the ground, one piece per
 * hop, with neighbouring pieces sharing their corners so the ribbon has no gaps or overlaps at the bends.
 *
 * Heights are measured from one fixed [baseline] (a low point of everything you've recorded), not from what's
 * on screen, so the route keeps the same height however the map is moved. They're smoothed over about
 * 120 m of route first, so GPS height jitter doesn't make the ribbon jump.
 */
object Route3d {
    /** One hop of a route: its four corners (lat, lon pairs) and its height above the ground in metres. */
    class Segment(val corners: DoubleArray, val heightM: Double)

    private const val EXAGGERATION = 1.5
    // Flat routes still float just above the ground, so they're visible.
    private const val LIFT_M = 3.0
    private const val HALF_WIDTH_M = 2.5
    private const val SMOOTH_HALF_WINDOW_M = 60.0
    private const val MEDIAN_RADIUS = 2
    // Sharp bends would make the mitred corners shoot out; cap how far they reach.
    private const val MAX_MITER = 3.0
    private const val METERS_PER_DEGREE = 111_320.0

    fun build(tracks: List<List<ElevationPoint>>, baseline: Double, maxSegments: Int = 8_000): List<Segment> {
        val routes = tracks.filter { it.size >= 2 }
        if (routes.isEmpty()) return emptyList()
        val hops = routes.sumOf { it.size - 1 }
        // Thin out very long histories so the map stays responsive.
        val step = max(1, (hops + maxSegments - 1) / maxSegments)
        val out = ArrayList<Segment>(minOf(hops, maxSegments) + 1)
        for (route in routes) {
            val pts = if (step == 1) route else route.filterIndexed { i, _ -> i % step == 0 || i == route.lastIndex }
            if (pts.size < 2) continue
            val heights = smooth(pts).map { ((it - baseline) * EXAGGERATION).coerceAtLeast(0.0) + LIFT_M }
            val (left, right) = offsets(pts) ?: continue
            for (i in 0 until pts.size - 1) {
                out += Segment(
                    doubleArrayOf(
                        left[i * 2], left[i * 2 + 1],
                        left[i * 2 + 2], left[i * 2 + 3],
                        right[i * 2 + 2], right[i * 2 + 3],
                        right[i * 2], right[i * 2 + 1],
                    ),
                    (heights[i] + heights[i + 1]) / 2,
                )
            }
        }
        return out
    }

    /**
     * Heights with GPS spikes removed (median of each dot and its two neighbours either side), then averaged
     * within [SMOOTH_HALF_WINDOW_M] along the route either side of each dot.
     */
    private fun smooth(pts: List<ElevationPoint>): DoubleArray {
        val raw = DoubleArray(pts.size) { i ->
            val window = (maxOf(0, i - MEDIAN_RADIUS)..minOf(pts.lastIndex, i + MEDIAN_RADIUS)).map { pts[it].altitude }.sorted()
            window[window.size / 2]
        }
        val along = DoubleArray(pts.size)
        for (i in 1 until pts.size) along[i] = along[i - 1] + meters(pts[i - 1], pts[i])
        val out = DoubleArray(pts.size)
        var lo = 0
        var hi = 0
        var sum = 0.0
        for (i in pts.indices) {
            while (hi < pts.size && along[hi] - along[i] <= SMOOTH_HALF_WINDOW_M) sum += raw[hi++]
            while (along[i] - along[lo] > SMOOTH_HALF_WINDOW_M) sum -= raw[lo++]
            out[i] = sum / (hi - lo)
        }
        return out
    }

    /**
     * The ribbon's left and right edges as interleaved (lat, lon) arrays, one pair per dot. At each bend the
     * offset follows the average direction of the two hops (a mitre), so neighbouring pieces meet exactly.
     */
    private fun offsets(pts: List<ElevationPoint>): Pair<DoubleArray, DoubleArray>? {
        val n = pts.size
        val metersPerLon = METERS_PER_DEGREE * cos(Math.toRadians(pts[n / 2].lat))
        // Unit direction of each hop, in metres (east, north); zero-length hops borrow the previous one.
        val dir = Array(n - 1) { DoubleArray(2) }
        var last: DoubleArray? = null
        for (i in 0 until n - 1) {
            val dx = (pts[i + 1].lon - pts[i].lon) * metersPerLon
            val dy = (pts[i + 1].lat - pts[i].lat) * METERS_PER_DEGREE
            val len = sqrt(dx * dx + dy * dy)
            dir[i] = if (len > 0.01) doubleArrayOf(dx / len, dy / len) else last ?: continue
            last = dir[i]
        }
        val first = last ?: return null
        // Fill leading zero-length hops with the first real direction.
        for (i in 0 until n - 1) {
            if (dir[i][0] == 0.0 && dir[i][1] == 0.0) dir[i] = dir.firstOrNull { it[0] != 0.0 || it[1] != 0.0 } ?: first
        }
        val left = DoubleArray(n * 2)
        val right = DoubleArray(n * 2)
        for (i in 0 until n) {
            val a = dir[maxOf(0, i - 1)]
            val b = dir[minOf(n - 2, i)]
            // Left-hand normal of the averaged direction, stretched so the ribbon keeps its width at the bend.
            var nx = -(a[1] + b[1])
            var ny = a[0] + b[0]
            val nlen = sqrt(nx * nx + ny * ny)
            if (nlen < 1e-6) {
                nx = -b[1]
                ny = b[0]
            } else {
                nx /= nlen
                ny /= nlen
            }
            val cosHalf = (nx * -b[1] + ny * b[0]).coerceAtLeast(1.0 / MAX_MITER)
            val reach = HALF_WIDTH_M / cosHalf
            val offLat = ny * reach / METERS_PER_DEGREE
            val offLon = nx * reach / metersPerLon
            left[i * 2] = pts[i].lat + offLat
            left[i * 2 + 1] = pts[i].lon + offLon
            right[i * 2] = pts[i].lat - offLat
            right[i * 2 + 1] = pts[i].lon - offLon
        }
        return left to right
    }

    private fun meters(a: ElevationPoint, b: ElevationPoint): Double {
        val dx = (b.lon - a.lon) * METERS_PER_DEGREE * cos(Math.toRadians((a.lat + b.lat) / 2))
        val dy = (b.lat - a.lat) * METERS_PER_DEGREE
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Gives a route's drawn line (its smoothed or road-snapped shape, [lat]/[lon]) the heights of the dots it
     * was made from, matching them by how far along the route each point is.
     */
    fun drape(lat: DoubleArray, lon: DoubleArray, dots: List<ElevationPoint>): List<ElevationPoint> {
        if (lat.isEmpty()) return emptyList()
        if (dots.size < 2) return List(lat.size) { ElevationPoint(lat[it], lon[it], dots.firstOrNull()?.altitude ?: 0.0) }
        val dotAlong = DoubleArray(dots.size)
        for (i in 1 until dots.size) dotAlong[i] = dotAlong[i - 1] + meters(dots[i - 1], dots[i])
        val lineAlong = DoubleArray(lat.size)
        for (i in 1 until lat.size) {
            lineAlong[i] = lineAlong[i - 1] + meters(ElevationPoint(lat[i - 1], lon[i - 1], 0.0), ElevationPoint(lat[i], lon[i], 0.0))
        }
        val dotTotal = dotAlong.last().coerceAtLeast(1e-6)
        val lineTotal = lineAlong.last().coerceAtLeast(1e-6)
        var k = 0
        return List(lat.size) { i ->
            val target = lineAlong[i] / lineTotal * dotTotal
            while (k < dots.size - 2 && dotAlong[k + 1] < target) k++
            val span = (dotAlong[k + 1] - dotAlong[k]).coerceAtLeast(1e-6)
            val f = ((target - dotAlong[k]) / span).coerceIn(0.0, 1.0)
            ElevationPoint(lat[i], lon[i], dots[k].altitude + (dots[k + 1].altitude - dots[k].altitude) * f)
        }
    }
}
