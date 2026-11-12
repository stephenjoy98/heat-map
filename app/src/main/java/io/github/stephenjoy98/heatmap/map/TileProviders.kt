package io.github.stephenjoy98.heatmap.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import io.github.stephenjoy98.heatmap.data.Cells
import io.github.stephenjoy98.heatmap.data.DisplayMode
import kotlin.math.max

const val TILE_PX = 512

/** Tile size that matches the screen's pixel density (tiles cover ~256 dp), for sharp heat lines. */
fun heatTilePx(density: Float): Int = (256 * density).toInt().coerceIn(256, 1024)

/** Renders one Web Mercator tile (x, y at zoom), or null if it's empty. Called on background threads. */
fun interface TileBitmapSource {
    fun render(x: Int, y: Int, zoom: Int): Bitmap?
}

/**
 * Fog of war: covers the map in [fogColor] and clears it, with soft edges, wherever you have dots, so only places
 * you've been show through. Far out, each place clears at least a few pixels so your routes still read as paths.
 */
class FogTiles(
    private val store: io.github.stephenjoy98.heatmap.data.PointStore,
    private val modes: Set<io.github.stephenjoy98.heatmap.data.TravelMode>,
    private val fogColor: Int,
    private val sizePx: Int = TILE_PX,
) : TileBitmapSource {
    override fun render(x: Int, y: Int, zoom: Int): Bitmap? = runCatching {
        val box = TileMath.box(x, y, zoom)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(fogColor)
        val midLat = (box.north + box.south) / 2
        val metersPerPx = 40_075_016.0 * kotlin.math.cos(Math.toRadians(midLat)) / (1 shl zoom) / sizePx
        val radius = (REVEAL_M / metersPerPx).toFloat().coerceAtLeast(MIN_REVEAL_PX)
        // Dots are pooled into cells about half a reveal across, so a day at home doesn't mean thousands of circles.
        val cellPx = (radius / 2f).coerceAtLeast(1.5f)
        val padLat = (box.north - box.south) * radius / sizePx
        val padLon = (box.east - box.west) * radius / sizePx
        val query = io.github.stephenjoy98.heatmap.data.GeoBox(box.north + padLat, box.south - padLat, box.east + padLon, box.west - padLon)
        val cellLat = (box.north - box.south) / sizePx * cellPx
        val cellLon = (box.east - box.west) / sizePx * cellPx
        val grid = store.gridIn(query, cellLat, cellLon, modes)
        val top = TileMath.mercY(box.north)
        val sy = sizePx / (top - TileMath.mercY(box.south))
        val sx = sizePx / (box.east - box.west)
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT) }
        for (i in 0 until grid.size) {
            val lat = query.south + (grid.row[i] + 0.5) * cellLat
            val lon = query.west + (grid.col[i] + 0.5) * cellLon
            val cx = ((lon - box.west) * sx).toFloat()
            val cy = ((top - TileMath.mercY(lat)) * sy).toFloat()
            clear.shader = android.graphics.RadialGradient(
                cx, cy, radius,
                intArrayOf(android.graphics.Color.BLACK, android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, radius, clear)
        }
        bitmap
    }.getOrNull()

    companion object {
        /** How far around each dot the fog lifts. */
        const val REVEAL_M = 45.0
        const val MIN_REVEAL_PX = 3f
    }
}

/** Heatmap tiles for a set of routes. */
class HeatTiles(
    private val source: TrackSource,
    private val ramp: IntArray,
    private val mode: DisplayMode,
    private val sizePx: Int = TILE_PX,
) : TileBitmapSource {
    override fun render(x: Int, y: Int, zoom: Int): Bitmap? = runCatching {
        HeatRenderer.renderTile(source, TileMath.box(x, y, zoom), sizePx, zoom.toDouble(), ramp, mode)
    }.getOrNull()
}

/** Fills ~220 m [Cells] with a colour; used to show shared and unexplored areas when comparing with a friend. */
class CellTiles(
    private val cells: Collection<Long>,
    private val color: Int,
) : TileBitmapSource {
    override fun render(x: Int, y: Int, zoom: Int): Bitmap? {
        val box = TileMath.box(x, y, zoom)
        val minRow = Cells.row(box.south)
        val maxRow = Cells.row(box.north)
        val minCol = Cells.col(box.west)
        val maxCol = Cells.col(box.east)
        val inside = cells.filter {
            val r = Cells.rowOf(it)
            val c = Cells.colOf(it)
            r in minRow..maxRow && c in minCol..maxCol
        }
        if (inside.isEmpty()) return null

        val bitmap = Bitmap.createBitmap(TILE_PX, TILE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { this.color = this@CellTiles.color }
        val sx = TILE_PX / (box.east - box.west)
        val top = TileMath.mercY(box.north)
        val sy = TILE_PX / (top - TileMath.mercY(box.south))
        for (key in inside) {
            val south = Cells.south(Cells.rowOf(key))
            val west = Cells.west(Cells.colOf(key))
            val l = ((west - box.west) * sx).toFloat()
            val r = ((west + 1 / Cells.PER_DEGREE - box.west) * sx).toFloat()
            val t = ((top - TileMath.mercY(south + 1 / Cells.PER_DEGREE)) * sy).toFloat()
            val b = ((top - TileMath.mercY(south)) * sy).toFloat()
            // Keep cells visible when zoomed far out.
            canvas.drawRect(l, t, max(r, l + 2f), max(b, t + 2f), paint)
        }
        return bitmap
    }
}

