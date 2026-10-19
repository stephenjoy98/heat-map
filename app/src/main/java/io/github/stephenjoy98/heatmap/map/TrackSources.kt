package io.github.stephenjoy98.heatmap.map

import android.util.LruCache
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.GridData
import io.github.stephenjoy98.heatmap.data.Polyline
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TrackRules
import io.github.stephenjoy98.heatmap.data.TravelMode

/** Your own recorded points, limited to the travel modes being shown. */
class OwnTracks(private val store: PointStore, private val modes: Set<TravelMode>) : TrackSource {
    override fun count(box: GeoBox) = store.countIn(box, modes)

    override fun forEachTrack(box: GeoBox, block: (DoubleArray, DoubleArray, Int, Int) -> Unit) {
        val smooth = Prefs.smoothRoutes.value
        val snapped = Prefs.snapToRoads.value
        // Trips matched to roads are drawn from their snapped shape; everything else from the recorded points.
        val t = store.tracksIn(box, modes, skipSnapped = snapped)
        var start = 0
        for (i in 1..t.size) {
            val ends = i == t.size || TrackRules.isBreak(
                t.time[i - 1], t.lat[i - 1], t.lon[i - 1], t.time[i], t.lat[i], t.lon[i], t.intervalMs[i],
            )
            if (ends) {
                if (smooth && i - start >= 3) {
                    val (la, lo) = Smoothing.smooth(t.lat, t.lon, start, i)
                    block(la, lo, 0, la.size)
                } else {
                    block(t.lat, t.lon, start, i)
                }
                start = i
            }
        }
        if (!snapped) return
        for (poly in store.snappedPathsIn(box, modes)) {
            val decoded = snappedCache.get(poly) ?: decode(poly).also { snappedCache.put(poly, it) }
            if (decoded.first.size >= 2) block(decoded.first, decoded.second, 0, decoded.first.size)
        }
    }

    private fun decode(poly: String): Pair<DoubleArray, DoubleArray> {
        val pts = Polyline.decode(poly)
        val n = pts.size / 2
        return DoubleArray(n) { pts[it * 2] } to DoubleArray(n) { pts[it * 2 + 1] }
    }

    companion object {
        private val snappedCache = LruCache<String, Pair<DoubleArray, DoubleArray>>(2000)
    }

    override fun grid(box: GeoBox, cellLat: Double, cellLon: Double): GridData = store.gridIn(box, cellLat, cellLon, modes)
}

/** A friend's shared routes (no timestamps; each route is already continuous). */
class FriendTracks(
    private val store: PointStore,
    private val friendUid: String,
    private val modes: Set<TravelMode>,
) : TrackSource {
    override fun count(box: GeoBox) = 0

    override fun forEachTrack(box: GeoBox, block: (DoubleArray, DoubleArray, Int, Int) -> Unit) {
        for (route in store.friendRoutes(friendUid, box, modes)) {
            val key = "$friendUid/${route.docId}"
            val decoded = cache.get(key) ?: decode(route.poly).also { cache.put(key, it) }
            if (decoded.first.isNotEmpty()) block(decoded.first, decoded.second, 0, decoded.first.size)
        }
    }

    override fun grid(box: GeoBox, cellLat: Double, cellLon: Double): GridData? = null

    private fun decode(poly: String): Pair<DoubleArray, DoubleArray> {
        val pts = Polyline.decode(poly)
        val n = pts.size / 2
        return DoubleArray(n) { pts[it * 2] } to DoubleArray(n) { pts[it * 2 + 1] }
    }

    companion object {
        private val cache = LruCache<String, Pair<DoubleArray, DoubleArray>>(3000)
    }
}
