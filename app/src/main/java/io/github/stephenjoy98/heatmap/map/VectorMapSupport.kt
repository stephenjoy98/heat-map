package io.github.stephenjoy98.heatmap.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.maps.Style
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Keyless vector maps from OpenFreeMap (OpenStreetMap data), drawn by MapLibre at the screen's full
 * resolution. Light is OpenFreeMap's clean Positron style (close to an in-car navigation map); dark is
 * the same style recoloured to the near-black greys of Google's dark theme.
 */
object VectorMaps {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"
    const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles · © OpenStreetMap contributors"

    // Google-style dark palette.
    private val background = Color.parseColor("#121212")
    private val landuse = Color.parseColor("#161616")
    private val park = Color.parseColor("#141A15")
    private val water = Color.parseColor("#0A0E13")
    private val building = Color.parseColor("#1C1C1C")
    private val minorRoad = Color.parseColor("#262626")
    private val majorRoad = Color.parseColor("#363636")
    private val rail = Color.parseColor("#242424")
    private val border = Color.parseColor("#3C3C3C")
    private val label = Color.parseColor("#8E9196")

    /** Recolours every layer of the light style for dark mode, matching on layer type and name. */
    fun applyDark(style: Style) {
        for (layer in style.layers) {
            val id = layer.id.lowercase()
            when (layer) {
                is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(background))
                is FillLayer -> layer.setProperties(
                    PropertyFactory.fillColor(
                        when {
                            "water" in id -> water
                            "building" in id -> building
                            listOf("park", "wood", "grass", "forest", "landcover", "green").any { it in id } -> park
                            else -> landuse
                        }
                    ),
                    PropertyFactory.fillOutlineColor(background),
                )
                is LineLayer -> layer.setProperties(
                    PropertyFactory.lineColor(
                        when {
                            "water" in id || "river" in id -> water
                            "casing" in id -> background
                            "boundary" in id || "admin" in id -> border
                            "rail" in id -> rail
                            listOf("motorway", "trunk", "primary", "major").any { it in id } -> majorRoad
                            else -> minorRoad
                        }
                    )
                )
                is SymbolLayer -> layer.setProperties(
                    PropertyFactory.textColor(label),
                    PropertyFactory.textHaloColor(background),
                    PropertyFactory.iconOpacity(0.6f),
                )
            }
        }
    }
}

/**
 * Renders a [TileBitmapSource] for whatever map engine is on screen: tiles are rendered on background
 * threads and cached. After [refresh], existing tiles keep showing until replacements are ready, so new
 * points don't make the heatmap flicker.
 */
class TileLayerRenderer(source: TileBitmapSource, alpha: Float, private val onTileReady: () -> Unit) {
    private class Entry(val bitmap: Bitmap?, val generation: Int)

    var source: TileBitmapSource = source
        set(value) {
            field = value
            refresh()
        }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (alpha * 255).toInt() }
    private val cache = object : LruCache<Long, Entry>(64 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Entry) = value.bitmap?.byteCount ?: 64
    }
    private val pending = HashSet<Long>()
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private val corners = FloatArray(8)
    private val src = FloatArray(8)
    private val matrix = Matrix()

    fun setAlpha(alpha: Float) {
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    }

    fun refresh() {
        generation++
        pending.clear()
    }

    /**
     * Draws the tiles at [zoom] covering the given bounds. [place] writes a tile's four corners in screen
     * pixels (top-left, top-right, bottom-right, bottom-left) so tiles follow a rotated map.
     */
    fun draw(
        canvas: Canvas,
        zoom: Int,
        north: Double, south: Double, east: Double, west: Double,
        place: (north: Double, west: Double, south: Double, east: Double, out: FloatArray) -> Unit,
    ) {
        val n = 1 shl zoom
        val x0 = tileX(west, n)
        val x1 = tileX(east, n)
        val y0 = tileY(north, n)
        val y1 = tileY(south, n)
        if ((x1 - x0 + 1) * (y1 - y0 + 1) > 64) return

        for (x in x0..x1) for (y in y0..y1) {
            val key = (zoom.toLong() shl 48) or (x.toLong() shl 24) or y.toLong()
            val entry = cache.get(key)
            if ((entry == null || entry.generation != generation) && key !in pending) request(key, x, y, zoom)
            val bitmap = entry?.bitmap ?: continue
            if (bitmap.isRecycled) continue
            val box = TileMath.box(x, y, zoom)
            place(box.north, box.west, box.south, box.east, corners)
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            src[2] = w; src[4] = w; src[5] = h; src[7] = h
            matrix.setPolyToPoly(src, 0, corners, 0, 4)
            canvas.drawBitmap(bitmap, matrix, paint)
        }
    }

    private fun request(key: Long, x: Int, y: Int, zoom: Int) {
        pending += key
        val gen = generation
        val src = source
        executor.execute {
            val bitmap = runCatching { src.render(x, y, zoom) }.getOrNull()
            main.post {
                if (gen != generation) return@post
                pending -= key
                cache.put(key, Entry(bitmap, gen))
                onTileReady()
            }
        }
    }

    fun release() {
        executor.shutdownNow()
        cache.evictAll()
    }

    private fun tileX(lon: Double, n: Int) = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)

    private fun tileY(lat: Double, n: Int): Int {
        val r = lat.coerceIn(-85.05, 85.05) * PI / 180
        return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toInt().coerceIn(0, n - 1)
    }
}

/**
 * A transparent view laid over a MapLibre map that draws heat (and other) tile layers plus a "latest
 * position" dot, following the camera, including rotation. Tilt is disabled so tiles stay flat.
 */
@SuppressLint("ViewConstructor")
class TileOverlayView(context: Context) : View(context) {
    private var map: MapLibreMap? = null
    private val renderers = LinkedHashMap<String, Pair<Float, TileLayerRenderer>>() // id -> (zIndex, renderer)
    private val density = resources.displayMetrics.density
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 66, 133, 244) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(66, 133, 244) }

    var latest: LatLng? = null
        set(value) {
            field = value
            invalidate()
        }

    init {
        // Touches go through to the map underneath.
        isClickable = false
        isFocusable = false
    }

    fun attach(map: MapLibreMap) {
        this.map = map
        map.addOnCameraMoveListener { invalidate() }
        map.addOnCameraIdleListener { invalidate() }
        invalidate()
    }

    /** Adds, updates and removes layers to match; each entry is (id, source, alpha, zIndex). */
    fun setLayers(layers: List<LayerSpec>) {
        val wanted = layers.associateBy { it.id }
        renderers.keys.filter { it !in wanted }.forEach { renderers.remove(it)?.second?.release() }
        for (l in layers) {
            val existing = renderers[l.id]?.second
            if (existing == null) {
                renderers[l.id] = l.zIndex to TileLayerRenderer(l.source, l.alpha) { invalidate() }
            } else {
                if (existing.source !== l.source) existing.source = l.source
                existing.setAlpha(l.alpha)
                renderers[l.id] = l.zIndex to existing
            }
        }
        invalidate()
    }

    fun refresh() {
        renderers.values.forEach { it.second.refresh() }
        invalidate()
    }

    fun release() {
        renderers.values.forEach { it.second.release() }
        renderers.clear()
    }

    override fun onDraw(canvas: Canvas) {
        val map = map ?: return
        val projection = map.projection
        val bounds = projection.visibleRegion.latLngBounds
        // MapLibre zoom is in 512 px tiles; tile zoom z+1 puts one heat tile on ~256 dp of screen.
        val zoom = floor(map.cameraPosition.zoom + 1).toInt().coerceIn(0, 21)
        val place = { north: Double, west: Double, south: Double, east: Double, out: FloatArray ->
            val tl = projection.toScreenLocation(LatLng(north, west))
            val tr = projection.toScreenLocation(LatLng(north, east))
            val br = projection.toScreenLocation(LatLng(south, east))
            val bl = projection.toScreenLocation(LatLng(south, west))
            out[0] = tl.x; out[1] = tl.y
            out[2] = tr.x; out[3] = tr.y
            out[4] = br.x; out[5] = br.y
            out[6] = bl.x; out[7] = bl.y
        }
        for ((_, renderer) in renderers.values.sortedBy { it.first }) {
            renderer.draw(canvas, zoom, bounds.latitudeNorth, bounds.latitudeSouth, bounds.longitudeEast, bounds.longitudeWest, place)
        }
        latest?.let {
            val p = projection.toScreenLocation(it)
            canvas.drawCircle(p.x, p.y, 14 * density, halo)
            canvas.drawCircle(p.x, p.y, 7 * density, ring)
            canvas.drawCircle(p.x, p.y, 5 * density, dot)
        }
    }

    class LayerSpec(val id: String, val source: TileBitmapSource, val alpha: Float, val zIndex: Float)
}
