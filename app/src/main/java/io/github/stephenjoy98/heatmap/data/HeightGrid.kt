package io.github.stephenjoy98.heatmap.data

import kotlin.math.floor

/**
 * One height per small patch of map (about 55 × 36 m), agreed across every time you've been there, so
 * going the same way again and again draws one route at one height instead of a stack of slightly different
 * ones (GPS heights wander by 10 m or more from one trip to the next).
 *
 * Each pass through a patch (a trip, or a day's stay) gets its own middle height there, and the patch's
 * height is the middle of those. Where the passes split clearly into two levels, more than [LEVEL_GAP_M]
 * apart (a bridge over a road, different floors of a tall building), the patch is a level question for the
 * user: until they answer it uses the shared height; "different levels" keeps each pass at its own height.
 */
class HeightGrid private constructor(
    private val cells: Map<Long, Cell>,
    private val decisions: Map<Long, Int>,
    val candidates: List<LevelCandidate>,
) {
    private class Cell(val consensus: Double, val passMedians: Map<Long, Double>)

    /** Two clearly different heights found around an area ([Cells] key), not yet asked about. */
    class LevelCandidate(val area: Long, val lat: Double, val lon: Double, val low: Double, val high: Double)

    /** The height to draw for a dot on [pass] with its own recorded height [own]. */
    fun height(lat: Double, lon: Double, pass: Long, own: Double): Double {
        val cell = cells[cellOf(lat, lon)] ?: return own
        return if (decisions[Cells.key(lat, lon)] == DIFFERENT) {
            // Separate levels: each pass keeps its own (steadied) height here.
            cell.passMedians[pass] ?: own
        } else {
            cell.consensus
        }
    }

    companion object {
        const val ASK = 0
        const val SAME = 1
        const val DIFFERENT = 2

        private const val PER_DEGREE = 2000.0
        private const val LEVEL_GAP_M = 15.0

        fun cellOf(lat: Double, lon: Double): Long =
            Cells.pack(floor((lat + 90.0) * PER_DEGREE).toInt(), floor((lon + 180.0) * PER_DEGREE).toInt())

        /** The pass a dot belongs to: its trip, or for dots outside trips, the day (so stays on different days count apart). */
        fun passOf(tripId: Long, time: Long): Long = if (tripId > 0) tripId else -(time / 86_400_000L) - 1

        /**
         * [samples] are (lat, lon, pass, height) for every dot with a height in the area; [decisions] are the
         * user's answers by [Cells] key.
         */
        fun build(samples: Sequence<Sample>, decisions: Map<Long, Int>): HeightGrid {
            val byCell = HashMap<Long, HashMap<Long, MutableList<Double>>>()
            val where = HashMap<Long, DoubleArray>()
            for (s in samples) {
                val key = cellOf(s.lat, s.lon)
                byCell.getOrPut(key) { HashMap() }.getOrPut(s.pass) { ArrayList() } += s.altitude
                where.getOrPut(key) { doubleArrayOf(s.lat, s.lon) }
            }
            val cells = HashMap<Long, Cell>(byCell.size)
            val candidates = HashMap<Long, LevelCandidate>()
            for ((key, passes) in byCell) {
                val medians = passes.mapValues { median(it.value) }
                val sorted = medians.values.sorted()
                cells[key] = Cell(median(sorted), medians)
                if (sorted.size < 2) continue
                // The biggest step between neighbouring pass heights: two levels if it's large.
                var gapAt = -1
                var gap = 0.0
                for (i in 1 until sorted.size) {
                    if (sorted[i] - sorted[i - 1] > gap) {
                        gap = sorted[i] - sorted[i - 1]
                        gapAt = i
                    }
                }
                if (gap < LEVEL_GAP_M) continue
                val (lat, lon) = where.getValue(key).let { it[0] to it[1] }
                val area = Cells.key(lat, lon)
                if (area in decisions) continue
                val low = median(sorted.subList(0, gapAt))
                val high = median(sorted.subList(gapAt, sorted.size))
                val prev = candidates[area]
                if (prev == null || high - low > prev.high - prev.low) candidates[area] = LevelCandidate(area, lat, lon, low, high)
            }
            return HeightGrid(cells, decisions, candidates.values.sortedByDescending { it.high - it.low })
        }

        private fun median(values: List<Double>): Double {
            val s = values.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }
    }

    class Sample(val lat: Double, val lon: Double, val pass: Long, val altitude: Double)
}
