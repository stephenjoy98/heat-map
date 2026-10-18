package io.github.stephenjoy98.heatmap.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import io.github.stephenjoy98.heatmap.data.Cells
import io.github.stephenjoy98.heatmap.data.DisplayMode
import java.io.ByteArrayOutputStream
import kotlin.math.max

private const val TILE_PX = 512

private fun Bitmap.toTile(): Tile {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.PNG, 100, out)
    recycle()
    return Tile(TILE_PX, TILE_PX, out.toByteArray())
}

/** Serves heatmap tiles to Google Maps; called on the map's background threads. */
class HeatTileProvider(
    private val source: TrackSource,
    private val ramp: IntArray,
    private val mode: DisplayMode,
) : TileProvider {
    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        val bitmap = runCatching {
            HeatRenderer.renderTile(source, TileMath.box(x, y, zoom), TILE_PX, zoom.toDouble(), ramp, mode)
        }.getOrNull() ?: return TileProvider.NO_TILE
        return bitmap.toTile()
    }
}

/** Fills ~220 m [Cells] with a colour; used to show shared and unexplored areas when comparing with a friend. */
class CellTileProvider(
    private val cells: Collection<Long>,
    private val color: Int,
) : TileProvider {
    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
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
        if (inside.isEmpty()) return TileProvider.NO_TILE

        val bitmap = Bitmap.createBitmap(TILE_PX, TILE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { this.color = this@CellTileProvider.color }
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
        return bitmap.toTile()
    }
}
