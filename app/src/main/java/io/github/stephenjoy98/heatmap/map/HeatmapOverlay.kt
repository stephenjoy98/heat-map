package io.github.stephenjoy98.heatmap.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import io.github.stephenjoy98.heatmap.data.LocationPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** Draws the latest rendered heatmap, stretched to wherever its area is on screen right now. */
class HeatmapOverlay(private val density: Float) : Overlay() {

    /** Set on the main thread only. */
    var rendered: HeatRenderer.Result? = null
        set(value) {
            val old = field
            field = value
            // Drawing happens on the main thread too, so the old bitmap is no longer in use.
            if (old != null && old.bitmap !== value?.bitmap) old.bitmap.recycle()
        }

    var latest: LocationPoint? = null

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 66, 165, 245) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(66, 165, 245) }
    private val dst = Rect()
    private val p1 = Point()
    private val p2 = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pj = mapView.projection

        rendered?.let { r ->
            pj.toPixels(GeoPoint(r.box.north, r.box.west), p1)
            pj.toPixels(GeoPoint(r.box.south, r.box.east), p2)
            dst.set(p1.x, p1.y, p2.x, p2.y)
            canvas.drawBitmap(r.bitmap, null, dst, bitmapPaint)
        }

        latest?.let { p ->
            pj.toPixels(GeoPoint(p.lat, p.lon), p1)
            val x = p1.x.toFloat()
            val y = p1.y.toFloat()
            canvas.drawCircle(x, y, 14 * density, haloPaint)
            canvas.drawCircle(x, y, 7 * density, ringPaint)
            canvas.drawCircle(x, y, 5 * density, dotPaint)
        }
    }

    override fun onDetach(mapView: MapView?) {
        rendered = null
        super.onDetach(mapView)
    }
}
