package io.github.stephenjoy98.heatmap.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import io.github.stephenjoy98.heatmap.data.DisplayMode
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.GridData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/** Anything the heatmap can draw: continuous routes, optionally summarised as a density grid. */
interface TrackSource {
    /** Number of points in [box]; above [HeatRenderer.ROUTE_POINT_LIMIT] the grid is used instead. */
    fun count(box: GeoBox): Int

    /** Calls [block] once per continuous route overlapping [box], with points [from, to) of the arrays. */
    fun forEachTrack(box: GeoBox, block: (lat: DoubleArray, lon: DoubleArray, from: Int, to: Int) -> Unit)

    /** Points per cell, or null if this source can't aggregate. */
    fun grid(box: GeoBox, cellLat: Double, cellLon: Double): GridData?
}

/**
 * Renders routes as a Strava-style heatmap tile.
 *
 * Every route is stroked onto an offscreen buffer with a low, fixed alpha. Overlapping routes stack up
 * (1 - (1 - a)^n), so the buffer's alpha channel ends up as a "how often have I been here" density.
 * That density is then mapped through a colour ramp, from the colour of a road used once to the colour
 * of the routes travelled the most.
 */
object HeatRenderer {
    /** Above this many points in a tile, draw a density grid instead of individual routes. */
    const val ROUTE_POINT_LIMIT = 300_000

    /** Alpha a single pass adds; about 20 overlapping visits saturate the ramp. */
    const val PASS_ALPHA = 0.12f

    /**
     * Renders the [box] area into a [size]×[size] bitmap, or returns null if nothing is there.
     * [isCancelled] is polled so abandoned tiles stop early.
     */
    fun renderTile(
        source: TrackSource,
        box: GeoBox,
        size: Int,
        zoom: Double,
        ramp: IntArray,
        mode: DisplayMode,
        isCancelled: () -> Boolean = { false },
    ): Bitmap? {
        val px = Projector(box, size, size)
        // Tiles are 256 "dp" wide in map units.
        val pxPerDp = size / 256f
        val strokeDp = (0.8 + (zoom - 12) * 0.5).coerceIn(1.0, 4.0)
        val stroke = (strokeDp * pxPerDp).toFloat()

        // Include routes that pass through the tile even when their fixes are outside it.
        val latPad = max((box.north - box.south) * 0.5, 0.02)
        val lonPad = max((box.east - box.west) * 0.5, 0.02)
        val query = GeoBox(
            north = min(box.north + latPad, 90.0),
            south = max(box.south - latPad, -90.0),
            east = box.east + lonPad,
            west = box.west - lonPad,
        )

        val buffer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(buffer)
        val count = source.count(query)
        val useGrid = mode == DisplayMode.POINTS || count > ROUTE_POINT_LIMIT
        val drew = if (useGrid) {
            val grid = source.grid(query, (box.north - box.south) / size * 1.5, (box.east - box.west) / size * 1.5)
            if (grid != null) drawGrid(grid, query, (box.north - box.south) / size * 1.5, (box.east - box.west) / size * 1.5, px, canvas, stroke)
            else drawRoutes(source, query, px, canvas, stroke, isCancelled)
        } else {
            drawRoutes(source, query, px, canvas, stroke, isCancelled)
        }
        if (!drew || isCancelled()) {
            buffer.recycle()
            return null
        }
        colorize(buffer, ramp)
        return buffer
    }

    private fun drawRoutes(
        source: TrackSource, query: GeoBox, px: Projector, canvas: Canvas, stroke: Float, isCancelled: () -> Boolean,
    ): Boolean {
        val alpha = (PASS_ALPHA * 255).roundToInt()
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = stroke * 3.5f
            color = Color.argb(alpha / 4, 255, 255, 255)
        }
        val core = Paint(glow).apply {
            strokeWidth = stroke
            color = Color.argb(alpha, 255, 255, 255)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = core.color }

        // Each continuous route is one Path, so a route only counts once where it overlaps itself;
        // only separate trips add up.
        val path = Path()
        var drew = false
        var tracks = 0
        source.forEachTrack(query) { lat, lon, from, to ->
            if (to - from == 1) {
                // A lone fix (e.g. hourly sampling): show it as a dot.
                canvas.drawCircle(px.x(lon[from]), px.y(lat[from]), stroke * 1.2f, dot)
            } else {
                path.rewind()
                var lastX = px.x(lon[from])
                var lastY = px.y(lat[from])
                path.moveTo(lastX, lastY)
                for (j in from + 1 until to) {
                    val x = px.x(lon[j])
                    val y = px.y(lat[j])
                    // Skip sub-pixel steps; standing still for an hour shouldn't cost thousands of segments.
                    if (abs(x - lastX) + abs(y - lastY) < 0.75f) continue
                    path.lineTo(x, y)
                    lastX = x
                    lastY = y
                }
                canvas.drawPath(path, glow)
                canvas.drawPath(path, core)
            }
            drew = true
            if (++tracks % 256 == 0 && isCancelled()) return@forEachTrack
        }
        return drew
    }

    private fun drawGrid(
        grid: GridData, query: GeoBox, cellLat: Double, cellLon: Double, px: Projector, canvas: Canvas, stroke: Float,
    ): Boolean {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = max(stroke * 1.2f, 1f)
        for (i in 0 until grid.size) {
            val lat = query.south + (grid.row[i] + 0.5) * cellLat
            val lon = query.west + (grid.col[i] + 0.5) * cellLon
            // Many points in one cell count for more, but logarithmically, like repeated visits.
            val weight = min(1f, PASS_ALPHA * (1f + ln(grid.count[i].toFloat())))
            paint.color = Color.argb((weight * 255).roundToInt(), 255, 255, 255)
            canvas.drawCircle(px.x(lon), px.y(lat), radius, paint)
        }
        return grid.size > 0
    }

    /** Replaces the white density buffer with ramp colours, in place. */
    private fun colorize(bitmap: Bitmap, ramp: IntArray) {
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) row[x] = ramp[row[x] ushr 24]
            bitmap.setPixels(row, 0, w, 0, y, w, 1)
        }
    }

    /** Maps lat/lon to bitmap pixels; linear in Web Mercator, matching the map tiles. */
    private class Projector(box: GeoBox, w: Int, h: Int) {
        private val west = box.west
        private val top = TileMath.mercY(box.north)
        private val sx = w / (box.east - box.west)
        private val sy = h / (top - TileMath.mercY(box.south))
        fun x(lon: Double) = ((lon - west) * sx).toFloat()
        fun y(lat: Double) = ((top - TileMath.mercY(lat)) * sy).toFloat()
    }
}

/** Colour ramps from "travelled once" to "travelled the most". */
object HeatRamps {
    /** Strava-like: dark red, orange, yellow, white. For the dark map. */
    val darkHeat = build(
        floatArrayOf(0.00f, 90f, 10f, 40f),
        floatArrayOf(HeatRenderer.PASS_ALPHA, 150f, 22f, 48f),
        floatArrayOf(0.35f, 225f, 62f, 22f),
        floatArrayOf(0.60f, 255f, 140f, 0f),
        floatArrayOf(0.82f, 255f, 212f, 90f),
        floatArrayOf(1.00f, 255f, 255f, 238f),
    )

    /** Light orange to deep crimson, which reads well on the white map. */
    val lightHeat = build(
        floatArrayOf(0.00f, 255f, 180f, 120f),
        floatArrayOf(HeatRenderer.PASS_ALPHA, 255f, 140f, 60f),
        floatArrayOf(0.35f, 245f, 90f, 20f),
        floatArrayOf(0.60f, 215f, 40f, 30f),
        floatArrayOf(0.82f, 160f, 0f, 50f),
        floatArrayOf(1.00f, 90f, 0f, 70f),
    )

    /** Friends are drawn in blues so they stand apart from your own heat. */
    val darkFriend = build(
        floatArrayOf(0.00f, 20f, 40f, 110f),
        floatArrayOf(HeatRenderer.PASS_ALPHA, 30f, 80f, 200f),
        floatArrayOf(0.35f, 40f, 150f, 255f),
        floatArrayOf(0.60f, 90f, 210f, 255f),
        floatArrayOf(0.82f, 170f, 240f, 255f),
        floatArrayOf(1.00f, 240f, 255f, 255f),
    )

    val lightFriend = build(
        floatArrayOf(0.00f, 150f, 190f, 255f),
        floatArrayOf(HeatRenderer.PASS_ALPHA, 100f, 150f, 255f),
        floatArrayOf(0.35f, 40f, 100f, 240f),
        floatArrayOf(0.60f, 20f, 60f, 200f),
        floatArrayOf(0.82f, 10f, 30f, 150f),
        floatArrayOf(1.00f, 20f, 10f, 90f),
    )

    /** Each stop is (density, r, g, b). */
    private fun build(vararg stops: FloatArray): IntArray =
        IntArray(256) { a -> if (a == 0) Color.TRANSPARENT else color(a / 255f, stops) }

    private fun color(t: Float, stops: Array<out FloatArray>): Int {
        var k = 1
        while (k < stops.size - 1 && t > stops[k][0]) k++
        val lo = stops[k - 1]
        val hi = stops[k]
        val f = ((t - lo[0]) / (hi[0] - lo[0])).coerceIn(0f, 1f)
        // Fade in up to one full pass so antialiased edges stay smooth.
        val opacity = min(1f, t / HeatRenderer.PASS_ALPHA)
        return Color.argb(
            (opacity * 255).roundToInt(),
            (lo[1] + (hi[1] - lo[1]) * f).roundToInt(),
            (lo[2] + (hi[2] - lo[2]) * f).roundToInt(),
            (lo[3] + (hi[3] - lo[3]) * f).roundToInt(),
        )
    }
}

/** Web Mercator helpers for map tiles. */
object TileMath {
    fun box(x: Int, y: Int, zoom: Int): GeoBox {
        val n = (1 shl zoom).toDouble()
        return GeoBox(
            north = lat(y / n),
            south = lat((y + 1) / n),
            west = x / n * 360.0 - 180.0,
            east = (x + 1) / n * 360.0 - 180.0,
        )
    }

    private fun lat(fraction: Double): Double = atan(sinh(PI * (1 - 2 * fraction))) * 180 / PI

    fun mercY(lat: Double): Double {
        val clamped = lat.coerceIn(-85.05, 85.05)
        return ln(tan(PI / 4 + clamped * PI / 360)) * 180 / PI
    }
}
