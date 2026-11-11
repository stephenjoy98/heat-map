package io.github.stephenjoy98.heatmap.data

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Bundles passes along the same lane into one route for the 3D view, so ten trips down a road make one ribbon
 * at their average line and height. Only passes within a couple of metres pool (matched trips follow the same
 * road shape), so separate lanes and carriageways keep ribbons of their own.
 *
 * Every dot counts: each route is walked in small steps, the steps are pooled into ~10 m squares, each square's
 * position and height is pulled towards its busy neighbours (weighted by how many passes went through them) a few
 * times so passes a lane or two apart meet, squares that end up within a few metres are merged, and the routes'
 * order between squares gives the streets. Places at clearly different heights (a bridge over a road) are kept
 * apart. The result is a network of lines that meet at junctions.
 */
object RouteBundle {
    private const val METERS_PER_DEGREE = 111_320.0
    private const val STEP_M = 2.0
    private const val CELL_M = 4.0
    private const val BAND_M = 12.0
    private const val SIGMA_M = 2.0
    private const val RADIUS_CELLS = 1
    private const val ROUNDS = 2
    private const val MERGE_M = 3.0
    // Heights further apart than this are different levels and never pooled.
    private const val LEVEL_M = 5.0
    // Side branches shorter than this off a street are leftovers of the pooling, not streets.
    private const val STUB_M = 12.0

    fun bundle(lines: List<List<ElevationPoint>>): List<List<ElevationPoint>> {
        val routes = lines.filter { it.size >= 2 }
        if (routes.isEmpty()) return emptyList()
        val lat0 = routes[0][0].lat
        val lon0 = routes[0][0].lon
        val mx = METERS_PER_DEGREE * cos(Math.toRadians(lat0))

        // 1. Walk every route in short steps and pool the steps into squares (split by height band).
        val index = HashMap<Long, Int>()
        val sx = DoubleList(); val sy = DoubleList(); val sh = DoubleList(); val sn = DoubleList()
        val cx = IntList(); val cy = IntList()
        val seqs = ArrayList<IntArray>(routes.size)
        for (route in routes) {
            val seq = IntList()
            fun add(x: Double, y: Double, h: Double) {
                val gx = floor(x / CELL_M).toInt()
                val gy = floor(y / CELL_M).toInt()
                val band = floor(h / BAND_M).toInt()
                val key = (gx.toLong() and 0x1FFFFF) or ((gy.toLong() and 0x1FFFFF) shl 21) or ((band.toLong() and 0x3FFFFF) shl 42)
                val i = index.getOrPut(key) {
                    sx.add(0.0); sy.add(0.0); sh.add(0.0); sn.add(0.0); cx.add(gx); cy.add(gy)
                    sn.size - 1
                }
                sx[i] += x; sy[i] += y; sh[i] += h; sn[i] += 1.0
                if (seq.size == 0 || seq[seq.size - 1] != i) seq.add(i)
            }
            var px = (route[0].lon - lon0) * mx
            var py = (route[0].lat - lat0) * METERS_PER_DEGREE
            var ph = route[0].altitude
            add(px, py, ph)
            for (k in 1 until route.size) {
                val x = (route[k].lon - lon0) * mx
                val y = (route[k].lat - lat0) * METERS_PER_DEGREE
                val h = route[k].altitude
                val len = sqrt((x - px) * (x - px) + (y - py) * (y - py))
                val steps = ceil(len / STEP_M).toInt().coerceIn(1, 5_000)
                for (s in 1..steps) {
                    val t = s.toDouble() / steps
                    add(px + (x - px) * t, py + (y - py) * t, ph + (h - ph) * t)
                }
                px = x; py = y; ph = h
            }
            if (seq.size >= 2) seqs += seq.toArray()
        }
        val n = sn.size
        var x = DoubleArray(n) { sx[it] / sn[it] }
        var y = DoubleArray(n) { sy[it] / sn[it] }
        var h = DoubleArray(n) { sh[it] / sn[it] }
        val weight = DoubleArray(n) { sn[it] }

        // 2. Pull each square towards its busy neighbours at the same level, a few rounds, so nearby passes meet.
        val byCell = HashMap<Long, IntList>()
        for (i in 0 until n) byCell.getOrPut(pack(cx[i], cy[i])) { IntList() }.add(i)
        val twoSigma2 = 2 * SIGMA_M * SIGMA_M
        repeat(ROUNDS) {
            val nx = DoubleArray(n); val ny = DoubleArray(n); val nh = DoubleArray(n)
            for (i in 0 until n) {
                var wSum = 0.0; var ax = 0.0; var ay = 0.0; var ah = 0.0
                for (dx in -RADIUS_CELLS..RADIUS_CELLS) for (dy in -RADIUS_CELLS..RADIUS_CELLS) {
                    val list = byCell[pack(cx[i] + dx, cy[i] + dy)] ?: continue
                    for (k in 0 until list.size) {
                        val j = list[k]
                        if (kotlin.math.abs(h[j] - h[i]) > LEVEL_M) continue
                        val d2 = (x[j] - x[i]) * (x[j] - x[i]) + (y[j] - y[i]) * (y[j] - y[i])
                        val w = weight[j] * exp(-d2 / twoSigma2)
                        wSum += w; ax += w * x[j]; ay += w * y[j]; ah += w * h[j]
                    }
                }
                nx[i] = ax / wSum; ny[i] = ay / wSum; nh[i] = ah / wSum
            }
            x = nx; y = ny; h = nh
        }

        // 3. Merge squares that now sit within a few metres of each other at the same level: the busiest square
        // takes in everything close to it, then the next busiest that's left, and so on (no chaining along a road,
        // which would fold a whole street into one point).
        val parent = IntArray(n) { -1 }
        val buckets = HashMap<Long, IntList>()
        for (i in 0 until n) buckets.getOrPut(pack(floor(x[i] / MERGE_M).toInt(), floor(y[i] / MERGE_M).toInt())) { IntList() }.add(i)
        for (i in (0 until n).sortedByDescending { weight[it] }) {
            if (parent[i] >= 0) continue
            parent[i] = i
            val bx = floor(x[i] / MERGE_M).toInt()
            val by = floor(y[i] / MERGE_M).toInt()
            for (dx in -1..1) for (dy in -1..1) {
                val list = buckets[pack(bx + dx, by + dy)] ?: continue
                for (k in 0 until list.size) {
                    val j = list[k]
                    if (parent[j] >= 0) continue
                    val d2 = (x[j] - x[i]) * (x[j] - x[i]) + (y[j] - y[i]) * (y[j] - y[i])
                    if (d2 < MERGE_M * MERGE_M && kotlin.math.abs(h[j] - h[i]) <= LEVEL_M) parent[j] = i
                }
            }
        }
        fun find(a: Int) = parent[a]
        val nodeOf = IntArray(n)
        val nodeIndex = HashMap<Int, Int>()
        val qx = DoubleList(); val qy = DoubleList(); val qh = DoubleList(); val qw = DoubleList()
        for (i in 0 until n) {
            val root = find(i)
            val node = nodeIndex.getOrPut(root) { qx.add(0.0); qy.add(0.0); qh.add(0.0); qw.add(0.0); qw.size - 1 }
            nodeOf[i] = node
            qx[node] += x[i] * weight[i]; qy[node] += y[i] * weight[i]; qh[node] += h[i] * weight[i]; qw[node] += weight[i]
        }
        val m = qw.size
        val px = DoubleArray(m) { qx[it] / qw[it] }
        val py = DoubleArray(m) { qy[it] / qw[it] }
        val ph = DoubleArray(m) { qh[it] / qw[it] }

        // 4. Streets: every step from one merged square to the next, each drawn once.
        val adjacency = HashMap<Int, MutableSet<Int>>()
        for (seq in seqs) {
            var prev = nodeOf[seq[0]]
            for (k in 1 until seq.size) {
                val cur = nodeOf[seq[k]]
                if (cur != prev) {
                    adjacency.getOrPut(prev) { HashSet() }.add(cur)
                    adjacency.getOrPut(cur) { HashSet() }.add(prev)
                }
                prev = cur
            }
        }

        // Tidy the street network: passes that step between nearby points in a different order leave triangles
        // (drop the long side) and short stubs off a street (drop them).
        fun dist(a: Int, b: Int) = sqrt((px[a] - px[b]) * (px[a] - px[b]) + (py[a] - py[b]) * (py[a] - py[b]))
        fun unlink(a: Int, b: Int) { adjacency[a]?.remove(b); adjacency[b]?.remove(a) }
        repeat(2) {
            val drop = ArrayList<Long>()
            for ((a, around) in adjacency) for (b in around) {
                if (b <= a) continue
                val ab = dist(a, b)
                val other = adjacency[b] ?: continue
                if (around.any { c -> c != b && c in other && ab > maxOf(dist(a, c), dist(c, b)) }) drop += (a.toLong() shl 32) or b.toLong()
            }
            for (e in drop) unlink((e shr 32).toInt(), e.toInt())
        }
        repeat(3) {
            val stubs = adjacency.filter { (node, around) ->
                around.size == 1 && (adjacency[around.first()]?.size ?: 0) >= 3 && dist(node, around.first()) < STUB_M
            }.keys.toList()
            for (node in stubs) adjacency[node]?.toList()?.forEach { unlink(node, it) }
        }
        adjacency.entries.removeAll { it.value.isEmpty() }

        // 5. Join the steps into lines that run from junction (or end) to junction.
        val used = HashSet<Long>()
        fun edge(a: Int, b: Int) = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()
        val chains = ArrayList<IntArray>()
        fun walk(start: Int, next: Int) {
            val chain = IntList()
            chain.add(start)
            var prev = start
            var cur = next
            used += edge(prev, cur)
            while (true) {
                chain.add(cur)
                val around = adjacency[cur] ?: break
                if (around.size != 2) break
                val following = around.firstOrNull { it != prev } ?: break
                if (!used.add(edge(cur, following))) break
                prev = cur
                cur = following
            }
            chains += chain.toArray()
        }
        for ((node, around) in adjacency) {
            if (around.size == 2) continue
            for (other in around) if (edge(node, other) !in used) walk(node, other)
        }
        // Loops with no junction on them.
        for ((node, around) in adjacency) for (other in around) if (edge(node, other) !in used) walk(node, other)

        // 6. A light smoothing along each line so the squares don't show as steps; ends stay put to meet neighbours.
        return chains.map { chain ->
            var cxs = DoubleArray(chain.size) { px[chain[it]] }
            var cys = DoubleArray(chain.size) { py[chain[it]] }
            val chs = DoubleArray(chain.size) { ph[chain[it]] }
            repeat(2) {
                val ax = cxs.copyOf(); val ay = cys.copyOf()
                for (k in 1 until chain.size - 1) {
                    ax[k] = (cxs[k - 1] + 2 * cxs[k] + cxs[k + 1]) / 4
                    ay[k] = (cys[k - 1] + 2 * cys[k] + cys[k + 1]) / 4
                }
                cxs = ax; cys = ay
            }
            List(chain.size) { k -> ElevationPoint(lat0 + cys[k] / METERS_PER_DEGREE, lon0 + cxs[k] / mx, chs[k]) }
        }
    }

    private fun pack(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)

    private class DoubleList {
        private var data = DoubleArray(1024)
        var size = 0
            private set
        fun add(v: Double) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
        operator fun get(i: Int) = data[i]
        operator fun set(i: Int, v: Double) { data[i] = v }
    }

    private class IntList {
        private var data = IntArray(8)
        var size = 0
            private set
        fun add(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
        operator fun get(i: Int) = data[i]
        fun toArray(): IntArray = data.copyOf(size)
    }
}
