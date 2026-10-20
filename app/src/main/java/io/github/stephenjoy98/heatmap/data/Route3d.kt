package io.github.stephenjoy98.heatmap.data

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/** A dot on a route with its height, for 3D routes. */
class ElevationPoint(val lat: Double, val lon: Double, val altitude: Double)

/**
 * Turns routes with heights into 3D geometry: a continuous ribbon [heightM] above the ground, one piece per
 * hop, with neighbouring pieces meeting at the bends so the ribbon has no gaps or overlaps there.
 *
 * Heights are measured from one fixed [baseline]: 0 when they're already heights above the ground (as the map's
 * 3D buildings are), so a route is drawn at its true height next to them. They're smoothed over about 120 m of
 * route first, so GPS height jitter doesn't make the ribbon jump. Routes that go over the same ground at the
 * same height are drawn once ([withoutOverlaps]): overlapping ribbons flicker as the map moves.
 */
object Route3d {
    /** One hop of a route: its four corners (lat, lon pairs) and its height above the ground in metres. */
    class Segment(val corners: DoubleArray, val heightM: Double)

    // Ground-level routes still float just above the ground, so they're visible.
    private const val LIFT_M = 2.0
    private const val HALF_WIDTH_M = 2.5
    private const val SMOOTH_HALF_WINDOW_M = 60.0
    private const val MEDIAN_RADIUS = 2
    // Sharp bends would make the mitred corners shoot out; cap how far they reach. Where a route turns back
    // on itself (GPS jitter, a U-turn) it's split instead, so each piece gets a square end.
    private const val MAX_MITER = 2.0
    private const val U_TURN_COS = -0.5
    // Each piece stops this far short of its neighbours: walls touching in the same plane flicker.
    private const val JOINT_GAP_M = 0.06
    // A route keeps a point only where leaving it out would move the line or its height by more than this.
    private const val SIMPLIFY_XY_M = 1.0
    private const val SIMPLIFY_H_M = 0.5
    private const val DENSE_STEP_M = 4.0
    private const val OVERLAP_CELL_M = 6.0
    private const val OVERLAP_HEIGHT_M = 4.0
    // A route's own last ~40 m doesn't count as an earlier route.
    private const val OWN_TAIL = 10
    private const val METERS_PER_DEGREE = 111_320.0

    fun build(tracks: List<List<ElevationPoint>>, baseline: Double, maxSegments: Int = 8_000): List<Segment> {
        val routes = tracks.filter { it.size >= 2 }.map { route ->
            val heights = smooth(route)
            simplify(List(route.size) { ElevationPoint(route[it].lat, route[it].lon, heights[it]) })
        }.flatMap(::splitAtUTurns).filter { it.size >= 2 }
        if (routes.isEmpty()) return emptyList()
        val hops = routes.sumOf { it.size - 1 }
        // Thin out very long histories so the map stays responsive.
        val step = max(1, (hops + maxSegments - 1) / maxSegments)
        val out = ArrayList<Segment>(minOf(hops, maxSegments) + 1)
        for (route in routes) {
            val pts = if (step == 1) route else route.filterIndexed { i, _ -> i % step == 0 || i == route.lastIndex }
            if (pts.size < 2) continue
            val heights = pts.map { (it.altitude - baseline).coerceAtLeast(0.0) + LIFT_M }
            val (left, right) = offsets(pts) ?: continue
            for (i in 0 until pts.size - 1) {
                val len = meters(pts[i], pts[i + 1])
                if (len < 0.2) continue
                val t = (JOINT_GAP_M / len).coerceAtMost(0.1)
                fun inset(edge: DoubleArray, k: Int, other: Int, c: Int) = edge[k * 2 + c] + (edge[other * 2 + c] - edge[k * 2 + c]) * t
                out += Segment(
                    doubleArrayOf(
                        inset(left, i, i + 1, 0), inset(left, i, i + 1, 1),
                        inset(left, i + 1, i, 0), inset(left, i + 1, i, 1),
                        inset(right, i + 1, i, 0), inset(right, i + 1, i, 1),
                        inset(right, i, i + 1, 0), inset(right, i, i + 1, 1),
                    ),
                    (heights[i] + heights[i + 1]) / 2,
                )
            }
        }
        return out
    }

    /** [route] with points added so none are more than [stepM] apart, heights in between interpolated. */
    fun densify(route: List<ElevationPoint>, stepM: Double = DENSE_STEP_M): List<ElevationPoint> {
        if (route.size < 2) return route
        val out = ArrayList<ElevationPoint>(route.size * 2)
        out += route[0]
        for (i in 1 until route.size) {
            val a = route[i - 1]
            val b = route[i]
            val n = (meters(a, b) / stepM).toInt()
            for (k in 1..n) {
                val f = k / (n + 1.0)
                out += ElevationPoint(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f, a.altitude + (b.altitude - a.altitude) * f)
            }
            out += b
        }
        return out
    }

    /**
     * The routes with the stretches an earlier route already covers (within about 6–12 m, at about the same
     * height) left out, so each street is drawn once. Pass routes [densify]d, cleanest first. A left-over
     * stretch keeps one point either side, so it joins the ribbon it continues.
     */
    fun withoutOverlaps(tracks: List<List<ElevationPoint>>): List<List<ElevationPoint>> {
        val first = tracks.firstOrNull { it.isNotEmpty() } ?: return emptyList()
        val metersPerLon = METERS_PER_DEGREE * cos(Math.toRadians(first[0].lat))
        // Cell → (route, point index, height) of the first route through it.
        val owners = HashMap<Long, Triple<Int, Int, Double>>()
        fun row(p: ElevationPoint) = floor(p.lat * METERS_PER_DEGREE / OVERLAP_CELL_M).toInt()
        fun col(p: ElevationPoint) = floor(p.lon * metersPerLon / OVERLAP_CELL_M).toInt()
        fun key(r: Int, c: Int) = (r.toLong() shl 32) or (c.toLong() and 0xFFFFFFFFL)
        val out = ArrayList<List<ElevationPoint>>()
        tracks.forEachIndexed { t, route ->
            val covered = BooleanArray(route.size) { i ->
                val p = route[i]
                val r = row(p)
                val c = col(p)
                var hit = false
                loop@ for (dr in -1..1) for (dc in -1..1) {
                    val o = owners[key(r + dr, c + dc)] ?: continue
                    if ((o.first != t || o.second < i - OWN_TAIL) && abs(o.third - p.altitude) < OVERLAP_HEIGHT_M) {
                        hit = true
                        break@loop
                    }
                }
                if (!hit) owners.putIfAbsent(key(r, c), Triple(t, i, p.altitude))
                hit
            }
            var i = 0
            while (i < route.size) {
                if (covered[i]) {
                    i++
                    continue
                }
                var j = i
                while (j + 1 < route.size && !covered[j + 1]) j++
                val piece = route.subList(maxOf(0, i - 1), minOf(route.size, j + 2))
                if (piece.size >= 2) out += piece
                i = j + 1
            }
        }
        return out
    }

    /** [pts] cut into pieces wherever the route turns back by more than about 120°. */
    private fun splitAtUTurns(pts: List<ElevationPoint>): List<List<ElevationPoint>> {
        if (pts.size < 3) return listOf(pts)
        val metersPerLon = METERS_PER_DEGREE * cos(Math.toRadians(pts[0].lat))
        val out = ArrayList<List<ElevationPoint>>()
        var from = 0
        for (i in 1 until pts.size - 1) {
            val ax = (pts[i].lon - pts[i - 1].lon) * metersPerLon
            val ay = (pts[i].lat - pts[i - 1].lat) * METERS_PER_DEGREE
            val bx = (pts[i + 1].lon - pts[i].lon) * metersPerLon
            val by = (pts[i + 1].lat - pts[i].lat) * METERS_PER_DEGREE
            val la = sqrt(ax * ax + ay * ay)
            val lb = sqrt(bx * bx + by * by)
            if (la < 0.01 || lb < 0.01) continue
            if ((ax * bx + ay * by) / (la * lb) < U_TURN_COS) {
                out += pts.subList(from, i + 1)
                from = i
            }
        }
        out += pts.subList(from, pts.size)
        return out
    }

    /** Leaves out points that lie (within [SIMPLIFY_XY_M] and [SIMPLIFY_H_M]) on the line between their neighbours. */
    private fun simplify(pts: List<ElevationPoint>): List<ElevationPoint> {
        if (pts.size < 3) return pts
        val metersPerLon = METERS_PER_DEGREE * cos(Math.toRadians(pts[pts.size / 2].lat))
        val x = DoubleArray(pts.size) { pts[it].lon * metersPerLon }
        val y = DoubleArray(pts.size) { pts[it].lat * METERS_PER_DEGREE }
        val keep = BooleanArray(pts.size)
        keep[0] = true
        keep[pts.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to pts.lastIndex)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b - a < 2) continue
            val dx = x[b] - x[a]
            val dy = y[b] - y[a]
            val len2 = dx * dx + dy * dy
            var worst = -1
            var worstScore = 1.0
            for (k in a + 1 until b) {
                val f = if (len2 < 1e-9) 0.0 else (((x[k] - x[a]) * dx + (y[k] - y[a]) * dy) / len2).coerceIn(0.0, 1.0)
                val ex = x[a] + dx * f - x[k]
                val ey = y[a] + dy * f - y[k]
                val eh = pts[a].altitude + (pts[b].altitude - pts[a].altitude) * f - pts[k].altitude
                val score = max(sqrt(ex * ex + ey * ey) / SIMPLIFY_XY_M, abs(eh) / SIMPLIFY_H_M)
                if (score > worstScore) {
                    worstScore = score
                    worst = k
                }
            }
            if (worst >= 0) {
                keep[worst] = true
                stack.addLast(a to worst)
                stack.addLast(worst to b)
            }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
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
            val cosHalf = nx * -b[1] + ny * b[0]
            val reach = HALF_WIDTH_M / cosHalf.coerceAtLeast(1.0 / MAX_MITER)
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
