package io.github.stephenjoy98.heatmap.roads

import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/**
 * A rail or ferry network (OpenStreetMap ways joined at shared nodes) that a recorded trace can be snapped to:
 * each stretch of the trace is matched to the line that runs closest to it, and the result follows the tracks
 * (or the ferry route) between them instead of the straight lines between recorded dots.
 *
 * Long ways are cut into pieces of at most [STEP_M] so a dot can be matched anywhere along a straight stretch.
 * Pure Kotlin, so it can be tested off the phone.
 */
class TrackNetwork(nodes: Map<Long, Pair<Double, Double>>, ways: List<LongArray>) {
    private val lat = ArrayList<Double>()
    private val lon = ArrayList<Double>()
    private val edges = ArrayList<MutableList<Edge>>()
    private val grid = HashMap<Long, MutableList<Int>>()
    private val mPerDegLat = 111_320.0
    private val mPerDegLon: Double

    private class Edge(val to: Int, val m: Double)

    init {
        val anyLat = nodes.values.firstOrNull()?.first ?: 0.0
        mPerDegLon = 111_320.0 * cos(Math.toRadians(anyLat))
        val index = HashMap<Long, Int>()
        fun nodeOf(id: Long): Int? {
            index[id]?.let { return it }
            val (a, b) = nodes[id] ?: return null
            return add(a, b).also { index[id] = it }
        }
        for (way in ways) {
            var prev: Int? = null
            for (id in way) {
                val n = nodeOf(id) ?: continue
                prev?.let { p -> link(p, n) }
                prev = n
            }
        }
        for (i in lat.indices) grid.getOrPut(cell(lat[i], lon[i])) { mutableListOf() }.add(i)
    }

    val size: Int get() = lat.size

    private fun add(a: Double, b: Double): Int {
        lat += a
        lon += b
        edges += mutableListOf<Edge>()
        return lat.size - 1
    }

    // Joins two nodes, putting extra ones in between on long stretches.
    private fun link(a: Int, b: Int) {
        val d = dist(lat[a], lon[a], lat[b], lon[b])
        val pieces = max(1, (d / STEP_M).toInt() + 1)
        var prev = a
        for (k in 1 until pieces) {
            val f = k.toDouble() / pieces
            val mid = add(lat[a] + (lat[b] - lat[a]) * f, lon[a] + (lon[b] - lon[a]) * f)
            connect(prev, mid)
            prev = mid
        }
        connect(prev, b)
    }

    private fun connect(a: Int, b: Int) {
        val d = dist(lat[a], lon[a], lat[b], lon[b])
        edges[a] += Edge(b, d)
        edges[b] += Edge(a, d)
    }

    private fun dist(aLat: Double, aLon: Double, bLat: Double, bLon: Double) =
        hypot((aLat - bLat) * mPerDegLat, (aLon - bLon) * mPerDegLon)

    private fun cell(a: Double, b: Double): Long {
        val r = floor(a / CELL_DEG).toLong()
        val c = floor(b / CELL_DEG).toLong()
        return (r shl 32) xor (c and 0xffffffffL)
    }

    /** Network nodes within [radiusM] of a spot, with their distance. */
    private fun near(a: Double, b: Double, radiusM: Double): List<Pair<Int, Double>> {
        val span = (radiusM / (CELL_DEG * mPerDegLat)).toInt() + 1
        val r0 = floor(a / CELL_DEG).toLong()
        val c0 = floor(b / CELL_DEG).toLong()
        val out = ArrayList<Pair<Int, Double>>()
        for (dr in -span..span) for (dc in -span..span) {
            val key = ((r0 + dr) shl 32) xor ((c0 + dc) and 0xffffffffL)
            grid[key]?.forEach { i ->
                val d = dist(a, b, lat[i], lon[i])
                if (d <= radiusM) out += i to d
            }
        }
        return out
    }

    /**
     * Distances along the network from [from] to everything within [limitM], and how each was reached. Stops
     * early once every node in [targets] is settled.
     */
    private fun search(from: Int, limitM: Double, targets: Set<Int>): Pair<HashMap<Int, Double>, HashMap<Int, Int>> {
        val best = HashMap<Int, Double>()
        val back = HashMap<Int, Int>()
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        best[from] = 0.0
        queue += 0.0 to from
        var left = targets.size
        val done = HashSet<Int>()
        while (queue.isNotEmpty()) {
            val (d, n) = queue.poll()
            if (!done.add(n)) continue
            if (n in targets && --left == 0) break
            for (e in edges[n]) {
                val nd = d + e.m
                if (nd > limitM) continue
                if (nd < (best[e.to] ?: Double.MAX_VALUE)) {
                    best[e.to] = nd
                    back[e.to] = n
                    queue += nd to e.to
                }
            }
        }
        return best to back
    }

    /**
     * Snaps a recorded trace ([lat], [lon]) onto the network: interleaved [lat, lon, ...] along the lines, or
     * null if too much of the trace is nowhere near them or the lines don't join up.
     */
    fun snap(lat: DoubleArray, lon: DoubleArray, maxSnapM: Double): DoubleArray? {
        if (lat.size < 2 || size == 0) return null
        // Samples about every SAMPLE_M along the trace, always with both ends.
        val samples = ArrayList<Int>()
        var since = 0.0
        samples += 0
        for (i in 1 until lat.size) {
            since += dist(lat[i - 1], lon[i - 1], lat[i], lon[i])
            if (since >= SAMPLE_M || i == lat.size - 1) {
                samples += i
                since = 0.0
            }
        }
        return follow(samples.map { lat[it] to lon[it] }, maxSnapM, requireAll = false)
    }

    /** The way along the network through [stops] in order, or null if one is off the network or they don't join up. */
    fun route(stops: List<Pair<Double, Double>>, maxSnapM: Double): DoubleArray? =
        if (stops.size < 2) null else follow(stops, maxSnapM, requireAll = true)

    private fun follow(points: List<Pair<Double, Double>>, maxSnapM: Double, requireAll: Boolean): DoubleArray? {
        val out = ArrayList<Double>()
        var current: Int? = null
        var missed = 0
        var prevPoint: Pair<Double, Double>? = null
        for (p in points) {
            val candidates = near(p.first, p.second, maxSnapM)
            if (candidates.isEmpty()) {
                if (requireAll) return null
                missed++
                continue
            }
            val here = current
            if (here == null) {
                val start = candidates.minBy { it.second }.first
                current = start
                out += lat[start]
                out += lon[start]
                prevPoint = p
                continue
            }
            // Reach the candidate that's cheapest to get to along the lines, counting how far it is from the dot,
            // so the snap keeps to one track instead of hopping between parallel ones.
            val straight = prevPoint?.let { dist(it.first, it.second, p.first, p.second) } ?: 0.0
            val limit = straight * DETOUR_RATIO + DETOUR_SLACK_M + maxSnapM * 2
            val targets = candidates.map { it.first }.toSet()
            val (reach, back) = search(here, limit, targets)
            val pick = candidates.filter { it.first in reach }
                .minByOrNull { reach.getValue(it.first) + it.second * OFFSET_WEIGHT }
            if (pick == null) {
                if (requireAll) return null
                missed++
                continue
            }
            // Walk back from the pick to where we were, then add those nodes in order.
            val path = ArrayList<Int>()
            var n = pick.first
            while (n != here) {
                path += n
                n = back[n] ?: break
            }
            for (k in path.indices.reversed()) {
                out += lat[path[k]]
                out += lon[path[k]]
            }
            current = pick.first
            prevPoint = p
        }
        if (out.size < 4) return null
        if (missed > points.size * MAX_MISSED_SHARE) return null
        return out.toDoubleArray()
    }

    companion object {
        const val STEP_M = 40.0
        const val SAMPLE_M = 150.0
        const val CELL_DEG = 0.002
        const val DETOUR_RATIO = 3.0
        const val DETOUR_SLACK_M = 400.0
        const val OFFSET_WEIGHT = 3.0
        const val MAX_MISSED_SHARE = 0.3
    }
}
