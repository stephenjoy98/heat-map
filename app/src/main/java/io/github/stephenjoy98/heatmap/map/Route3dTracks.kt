package io.github.stephenjoy98.heatmap.map

import io.github.stephenjoy98.heatmap.data.ElevationPoint
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Polyline
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.Route3d
import io.github.stephenjoy98.heatmap.data.TravelMode

/**
 * Routes for the 3D view, following the same lines as the flat map: a trip snapped to roads uses its road
 * shape (whole, so it doesn't change as the map moves), other stretches their smoothed line when smooth routes
 * are on. Heights come from the recorded dots, laid along that line. Call off the main thread.
 */
object Route3dTracks {
    fun load(store: PointStore, box: GeoBox, modes: Set<TravelMode>): List<List<ElevationPoint>> {
        val smooth = Prefs.smoothRoutes.value
        val out = ArrayList<List<ElevationPoint>>()
        val drawnTrips = HashSet<Long>()
        for ((tripId, dots) in store.elevationRunsIn(box, modes)) {
            val poly = if (tripId > 0) store.tripPathRow(tripId)?.poly else null
            if (poly != null) {
                if (!drawnTrips.add(tripId)) continue
                val pts = Polyline.decode(poly)
                val n = pts.size / 2
                if (n < 2) continue
                out += Route3d.drape(DoubleArray(n) { pts[it * 2] }, DoubleArray(n) { pts[it * 2 + 1] }, store.tripElevations(tripId))
                continue
            }
            if (smooth && dots.size >= 3) {
                val lat = DoubleArray(dots.size) { dots[it].lat }
                val lon = DoubleArray(dots.size) { dots[it].lon }
                val (la, lo) = Smoothing.smooth(lat, lon, 0, dots.size)
                out += Route3d.drape(la, lo, dots)
            } else {
                out += dots
            }
        }
        return out
    }
}
