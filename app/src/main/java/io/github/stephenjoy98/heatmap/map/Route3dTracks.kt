package io.github.stephenjoy98.heatmap.map

import io.github.stephenjoy98.heatmap.data.ElevationPoint
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.HeightGrid
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Polyline
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.Route3d
import io.github.stephenjoy98.heatmap.data.TravelMode

/**
 * Routes for the 3D view, following the same lines as the flat map: a trip snapped to roads uses its road
 * shape (whole, so it doesn't change as the map moves), other stretches their smoothed line when smooth routes
 * are on.
 *
 * Heights come from a [HeightGrid] over everything recorded nearby, so a street you've travelled many times is
 * drawn at one agreed height rather than a different one each time. Places with two clearly different levels
 * become questions on the Timeline; once answered "different levels", each pass keeps its own height there.
 *
 * The heights returned are above the ground ([GroundHeights]), like the map's 3D buildings, so an elevated
 * train is drawn a few storeys up beside them and a 15th-floor flat about halfway up its tower. Where the
 * ground height can't be had (offline), they're above a low point of everything recorded instead. A street
 * covered by several routes at the same height is returned once. Call off the main thread.
 */
object Route3dTracks {
    private const val MAX_NEW_QUESTIONS = 5

    // [rank]: snapped to roads 0, smoothed 1, as recorded 2; cleaner lines win where routes overlap.
    private class Line(val pass: Long, val dots: List<ElevationPoint>, val lat: DoubleArray?, val lon: DoubleArray?, val rank: Int)

    fun load(store: PointStore, ground: GroundHeights, box: GeoBox, modes: Set<TravelMode>): List<List<ElevationPoint>> {
        val smooth = Prefs.smoothRoutes.value
        val lines = ArrayList<Line>()
        val drawnTrips = HashSet<Long>()
        for (run in store.elevationRunsIn(box, modes)) {
            val poly = if (run.tripId > 0) store.tripPathRow(run.tripId)?.poly else null
            if (poly != null) {
                if (!drawnTrips.add(run.tripId)) continue
                val pts = Polyline.decode(poly)
                val n = pts.size / 2
                if (n < 2) continue
                lines += Line(run.pass, store.tripElevations(run.tripId), DoubleArray(n) { pts[it * 2] }, DoubleArray(n) { pts[it * 2 + 1] }, 0)
            } else if (smooth && run.dots.size >= 3) {
                val lat = DoubleArray(run.dots.size) { run.dots[it].lat }
                val lon = DoubleArray(run.dots.size) { run.dots[it].lon }
                val (la, lo) = Smoothing.smooth(lat, lon, 0, run.dots.size)
                lines += Line(run.pass, run.dots, la, lo, 1)
            } else {
                lines += Line(run.pass, run.dots, null, null, 2)
            }
        }
        if (lines.isEmpty()) return emptyList()

        // Agreed heights from every dot recorded around these routes, on any trip.
        val all = lines.flatMap { it.dots }
        val area = GeoBox(all.maxOf { it.lat }, all.minOf { it.lat }, all.maxOf { it.lon }, all.minOf { it.lon })
        val grid = HeightGrid.build(store.heightSamples(area).asSequence(), store.levelDecisions())
        store.addLevelQuestions(grid.candidates.take(MAX_NEW_QUESTIONS))

        val groundAt = ground.session()
        val fallback by lazy { store.altitudeBaseline() }
        val tracks = lines.sortedBy { it.rank }.map { line ->
            val dots = line.dots.map { ElevationPoint(it.lat, it.lon, grid.height(it.lat, it.lon, line.pass, it.altitude)) }
            val drawn = if (line.lat != null && line.lon != null) Route3d.drape(line.lat, line.lon, dots) else dots
            Route3d.densify(drawn.map { ElevationPoint(it.lat, it.lon, it.altitude - (groundAt(it.lat, it.lon) ?: fallback)) })
        }
        return Route3d.withoutOverlaps(tracks)
    }
}
