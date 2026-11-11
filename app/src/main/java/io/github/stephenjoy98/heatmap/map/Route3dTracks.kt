package io.github.stephenjoy98.heatmap.map

import io.github.stephenjoy98.heatmap.data.ElevationPoint
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.HeightGrid
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Polyline
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.Route3d
import io.github.stephenjoy98.heatmap.data.RouteBundle
import io.github.stephenjoy98.heatmap.data.TravelMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Routes for the 3D view, following the same lines as the flat map: a trip snapped to roads uses its road
 * shape (whole, so it doesn't change as the map moves), other stretches their smoothed line when smooth routes
 * are on.
 *
 * Heights come from a [HeightGrid] over everything recorded nearby, so a street you've travelled many times is
 * drawn at one agreed height rather than a different one each time. Places with two clearly different levels
 * become questions on the Timeline; once answered "different levels", each pass keeps its own height there.
 * Call off the main thread.
 */
/**
 * The 3D route's state for the layers sheet: when it was last worked out and from how many dots, whether it's
 * being worked out now, and [recalculate] to do it again from scratch. Compose state, so the sheet updates.
 */
object Route3dStatus {
    var builtAt by androidx.compose.runtime.mutableLongStateOf(0L)
    var dots by androidx.compose.runtime.mutableIntStateOf(0)
    var busy by androidx.compose.runtime.mutableStateOf(false)
    /** Goes up on each Recalculate; the map rebuilds the 3D route when it changes. */
    var requested by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun recalculate(store: PointStore) {
        store.clearAltitudeBaseline()
        requested++
    }
}

object Route3dTracks {
    private const val MAX_NEW_QUESTIONS = 5

    private class Line(val pass: Long, val dots: List<ElevationPoint>, val lat: DoubleArray?, val lon: DoubleArray?)

    fun load(store: PointStore, box: GeoBox, modes: Set<TravelMode>): List<List<ElevationPoint>> {
        val lines = ArrayList<Line>()
        val drawnTrips = HashSet<Long>()
        // Only trips matched to a road (or railway / ferry route) are raised: a route that strays from the road,
        // isn't matched yet, or is time spent somewhere stays flat on the heatmap, so 3D shows only clean lines.
        for (run in store.elevationRunsIn(box, modes)) {
            if (run.tripId <= 0 || !drawnTrips.add(run.tripId)) continue
            val poly = store.tripPathRow(run.tripId)?.poly ?: continue
            val pts = Polyline.decode(poly)
            val n = pts.size / 2
            if (n < 2) continue
            lines += Line(run.pass, store.tripElevations(run.tripId), DoubleArray(n) { pts[it * 2] }, DoubleArray(n) { pts[it * 2 + 1] })
        }
        if (lines.isEmpty()) return emptyList()

        // Agreed heights from every dot recorded around these routes, on any trip.
        val all = lines.flatMap { it.dots }
        val area = GeoBox(all.maxOf { it.lat }, all.minOf { it.lat }, all.maxOf { it.lon }, all.minOf { it.lon })
        val grid = HeightGrid.build(store.heightSamples(area).asSequence(), store.levelDecisions())
        store.addLevelQuestions(grid.candidates.take(MAX_NEW_QUESTIONS))

        val drawn = lines.mapNotNull { line ->
            val heights = DoubleArray(line.dots.size) { line.dots[it].let { d -> grid.height(d.lat, d.lon, line.pass, d.altitude) } }
            if (!fillGaps(heights)) return@mapNotNull null
            // Time spent somewhere rather than a trip (pass < 0): one height for the whole stay, so GPS height
            // jitter indoors doesn't stand up as fins and spikes.
            if (line.pass < 0) heights.fill(median(heights))
            val dots = line.dots.mapIndexed { i, d -> ElevationPoint(d.lat, d.lon, heights[i]) }
            if (line.lat != null && line.lon != null) Route3d.drape(line.lat, line.lon, dots) else dots
        }
        // Every pass along a street becomes one route at their average line and height. Whole road shapes can
        // run far outside the view, so only the part around the box is bundled.
        return RouteBundle.bundle(drawn.flatMap { clip(it, box) })
    }

    /** The stretches of [line] inside [box] widened by a quarter each way, with one dot either side of each. */
    private fun clip(line: List<ElevationPoint>, box: GeoBox): List<List<ElevationPoint>> {
        val padLat = (box.north - box.south) / 4
        val padLon = (box.east - box.west) / 4
        fun inside(p: ElevationPoint) = p.lat in (box.south - padLat)..(box.north + padLat) && p.lon in (box.west - padLon)..(box.east + padLon)
        val out = ArrayList<List<ElevationPoint>>()
        var start = -1
        for (i in line.indices) {
            if (inside(line[i])) {
                if (start < 0) start = maxOf(0, i - 1)
            } else if (start >= 0) {
                out += line.subList(start, i + 1)
                start = -1
            }
        }
        if (start >= 0) out += line.subList(start, line.size)
        return out.filter { it.size >= 2 }
    }

    /** Fills NaN heights from the nearest dot before (or after) that has one; false when none has a height. */
    private fun fillGaps(h: DoubleArray): Boolean {
        val first = h.indexOfFirst { !it.isNaN() }
        if (first < 0) return false
        for (i in 0 until first) h[i] = h[first]
        for (i in first + 1 until h.size) if (h[i].isNaN()) h[i] = h[i - 1]
        return true
    }

    private fun median(h: DoubleArray): Double {
        val s = h.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
