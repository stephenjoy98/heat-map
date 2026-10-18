package io.github.stephenjoy98.heatmap.map

import io.github.stephenjoy98.heatmap.data.DisplayMode
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.PointStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.MapView

/** Re-renders the heatmap in the background whenever the map settles or the data changes. */
class HeatmapController(
    private val mapView: MapView,
    private val store: PointStore,
    private val scope: CoroutineScope,
) {
    private val density = mapView.resources.displayMetrics.density
    val overlay = HeatmapOverlay(density)
    private var job: Job? = null

    var mode: DisplayMode = DisplayMode.ROUTES
        set(value) {
            if (field != value) {
                field = value
                requestRender(0)
            }
        }

    init {
        mapView.overlays.add(overlay)
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                requestRender()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                requestRender()
                return false
            }
        })
        mapView.addOnFirstLayoutListener { _, _, _, _, _ -> requestRender(0) }
    }

    /** Debounced: rapid calls while panning collapse into one render once the map stops moving. */
    fun requestRender(delayMs: Long = 250) {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            val w = mapView.width
            val h = mapView.height
            if (w == 0 || h == 0) return@launch
            val bb = mapView.boundingBox
            if (bb.lonWest >= bb.lonEast) return@launch // Spans the antimeridian; not supported.
            val request = HeatRenderer.Request(
                view = GeoBox(bb.latNorth, bb.latSouth, bb.lonEast, bb.lonWest),
                widthPx = w,
                heightPx = h,
                zoom = mapView.zoomLevelDouble,
                density = density,
                mode = mode,
            )
            val latest = withContext(Dispatchers.IO) { store.latest() }
            val result = withContext(Dispatchers.Default) { HeatRenderer.render(store, request) }
            overlay.latest = latest
            if (result != null) overlay.rendered = result
            mapView.invalidate()
        }
    }
}
