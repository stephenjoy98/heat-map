package io.github.stephenjoy98.heatmap.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import io.github.stephenjoy98.heatmap.BuildConfig
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Keyless base maps from OpenStreetMap's standard tiles, tinted in the app: a muted near-black for the
 * dark theme (like Strava's heatmap) and a soft, low-contrast white for the light theme (like an in-car
 * navigation map). If the build has a CARTO basemaps key, CARTO's own Dark Matter and Positron styles
 * are used instead.
 */
object OsmTiles {
    private val cartoKey = BuildConfig.CARTO_API_KEY

    private fun carto(name: String, path: String) = XYTileSource(
        name, 0, 20, 256, ".png?key=$cartoKey",
        arrayOf("a", "b", "c", "d").map { "https://$it.basemaps.cartocdn.com/$path/" }.toTypedArray(),
        "© OpenStreetMap contributors © CARTO",
    )

    val usesCarto: Boolean get() = cartoKey.isNotEmpty()

    fun source(dark: Boolean): ITileSource = when {
        !usesCarto -> TileSourceFactory.MAPNIK
        dark -> carto("CartoDarkMatterKeyed", "dark_all")
        else -> carto("CartoPositronKeyed", "light_all")
    }

    /** Recolours the standard OSM tiles; none needed for CARTO's styles. */
    fun filter(dark: Boolean): ColorFilter? {
        if (usesCarto) return null
        // Greyscale luminance, then: dark = 157.5 - 0.5·L (land ≈ 38, labels light), light = 102 + 0.6·L (land ≈ 245).
        val (scale, offset) = if (dark) -0.5f to 157.5f else 0.6f to 102f
        val r = 0.299f * scale
        val g = 0.587f * scale
        val b = 0.114f * scale
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    r, g, b, 0f, offset,
                    r, g, b, 0f, offset,
                    r, g, b, 0f, offset,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }

    val attribution: String get() = if (usesCarto) "© OpenStreetMap contributors © CARTO" else "© OpenStreetMap contributors"
}

/**
 * Draws a [TileBitmapSource] on an osmdroid map, rendering tiles on background threads.
 * After [refresh], existing tiles keep showing until their replacements are ready, so new points
 * don't make the map flicker.
 */
class OsmTileLayer(source: TileBitmapSource, alpha: Float = 1f) : Overlay() {
    private class Entry(val bitmap: Bitmap?, val generation: Int)

    var source: TileBitmapSource = source
        set(value) {
            field = value
            refresh()
        }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (alpha * 255).toInt() }
    private val cache = object : LruCache<Long, Entry>(48 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Entry) = value.bitmap?.byteCount ?: 64
    }
    private val pending = HashSet<Long>()
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private val tl = Point()
    private val br = Point()
    private val dst = Rect()

    fun setAlpha(alpha: Float) {
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    }

    /** Re-renders every tile (call when the underlying data changes). */
    fun refresh() {
        generation++
        pending.clear()
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pj = mapView.projection
        val zoom = floor(pj.zoomLevel).toInt().coerceIn(0, 20)
        val bb = mapView.boundingBox
        val n = 1 shl zoom
        val x0 = tileX(bb.lonWest, n)
        val x1 = tileX(bb.lonEast, n)
        val y0 = tileY(bb.latNorth, n)
        val y1 = tileY(bb.latSouth, n)
        if ((x1 - x0 + 1) * (y1 - y0 + 1) > 64) return // Absurdly zoomed out; skip.

        for (x in x0..x1) for (y in y0..y1) {
            val key = (zoom.toLong() shl 48) or (x.toLong() shl 24) or y.toLong()
            val entry = cache.get(key)
            if ((entry == null || entry.generation != generation) && key !in pending) request(key, x, y, zoom, mapView)
            val bitmap = entry?.bitmap ?: continue
            if (bitmap.isRecycled) continue
            val box = TileMath.box(x, y, zoom)
            pj.toPixels(GeoPoint(box.north, box.west), tl)
            pj.toPixels(GeoPoint(box.south, box.east), br)
            dst.set(tl.x, tl.y, br.x, br.y)
            canvas.drawBitmap(bitmap, null, dst, paint)
        }
    }

    private fun request(key: Long, x: Int, y: Int, zoom: Int, mapView: MapView) {
        pending += key
        val gen = generation
        val src = source
        executor.execute {
            val bitmap = runCatching { src.render(x, y, zoom) }.getOrNull()
            main.post {
                if (gen != generation) return@post
                pending -= key
                cache.put(key, Entry(bitmap, gen))
                mapView.postInvalidate()
            }
        }
    }

    override fun onDetach(mapView: MapView?) {
        executor.shutdownNow()
        cache.evictAll()
        super.onDetach(mapView)
    }

    private fun tileX(lon: Double, n: Int) = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)

    private fun tileY(lat: Double, n: Int): Int {
        val r = lat.coerceIn(-85.05, 85.05) * PI / 180
        return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toInt().coerceIn(0, n - 1)
    }
}

/** A blue "you are here" dot at the latest recorded point (osmdroid has no built-in one without GPS listening). */
class LatestPointOverlay(private val density: Float) : Overlay() {
    var point: GeoPoint? = null
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 66, 133, 244) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(66, 133, 244) }
    private val p = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        val gp = point ?: return
        if (shadow) return
        mapView.projection.toPixels(gp, p)
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 14 * density, halo)
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 7 * density, ring)
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), 5 * density, dot)
    }
}
