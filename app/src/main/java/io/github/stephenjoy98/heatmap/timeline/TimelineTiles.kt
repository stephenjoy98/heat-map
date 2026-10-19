package io.github.stephenjoy98.heatmap.timeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import io.github.stephenjoy98.heatmap.map.TileBitmapSource
import io.github.stephenjoy98.heatmap.map.TileMath
import kotlin.math.abs

/** Draws a period's routes as solid lines in each travel mode's colour, like a timeline map. */
class TimelineTiles(
    private val routes: List<TimelineRoute>,
    private val sizePx: Int,
    private val casingColor: Int,
) : TileBitmapSource {
    override fun render(x: Int, y: Int, zoom: Int): Bitmap? {
        val box = TileMath.box(x, y, zoom)
        val padLat = (box.north - box.south) * 0.1
        val padLon = (box.east - box.west) * 0.1
        val visible = routes.filter {
            it.box.intersects(box.south - padLat, box.north + padLat, box.west - padLon, box.east + padLon)
        }
        if (visible.isEmpty()) return null

        val pxPerDp = sizePx / 256f
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val casing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 6f * pxPerDp
            color = casingColor
        }
        val line = Paint(casing).apply { strokeWidth = 3.5f * pxPerDp }
        val top = TileMath.mercY(box.north)
        val sx = sizePx / (box.east - box.west)
        val sy = sizePx / (top - TileMath.mercY(box.south))
        val path = Path()
        for (pass in 0..1) {
            for (r in visible) {
                path.rewind()
                var lastX = ((r.lon[0] - box.west) * sx).toFloat()
                var lastY = ((top - TileMath.mercY(r.lat[0])) * sy).toFloat()
                path.moveTo(lastX, lastY)
                for (i in 1 until r.lat.size) {
                    val px = ((r.lon[i] - box.west) * sx).toFloat()
                    val py = ((top - TileMath.mercY(r.lat[i])) * sy).toFloat()
                    if (abs(px - lastX) + abs(py - lastY) < 0.75f) continue
                    path.lineTo(px, py)
                    lastX = px
                    lastY = py
                }
                if (pass == 0) {
                    canvas.drawPath(path, casing)
                } else {
                    line.color = r.color.toInt()
                    canvas.drawPath(path, line)
                }
            }
        }
        return bitmap
    }
}
