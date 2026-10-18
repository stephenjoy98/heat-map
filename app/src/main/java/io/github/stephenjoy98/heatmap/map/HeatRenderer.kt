package io.github.stephenjoy98.heatmap.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import io.github.stephenjoy98.heatmap.data.DisplayMode
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.TrackRules
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Renders recorded points as a Strava-style heatmap.
 *
 * Every route is stroked onto an offscreen buffer with a low, fixed alpha. Overlapping routes stack up
 * (1 - (1 - a)^n), so the buffer's alpha channel ends up as a "how often have I been here" density.
 * That density is then mapped through a colour ramp: dark red for roads used once, through orange
 * and yellow, to white for the routes travelled the most.
 */
object HeatRenderer {
    /** Above this many points in view, draw a density grid instead of individual routes. */
    private const val ROUTE_POINT_LIMIT = 400_000

    /** Cap on rendered pixels; the bitmap is scaled up to the screen, which also softens the glow. */
    private const val PIXEL_BUDGET = 1_600_000

    /** Alpha a single pass adds; about 20 overlapping visits saturate to white. */
    private const val PASS_ALPHA = 0.12f

    class Request(
        val view: GeoBox,
        val widthPx: Int,
        val heightPx: Int,
        val zoom: Double,
        val density: Float,
        val mode: DisplayMode,
    )

    /** A rendered bitmap covering [box]. */
    class Result(val bitmap: Bitmap, val box: GeoBox)

    private val ramp: IntArray = buildRamp()

    suspend fun render(store: PointStore, req: Request): Result? {
        if (req.widthPx <= 0 || req.heightPx <= 0) return null

        // Render a margin around the view so short pans don't reveal empty edges before the next render.
        val viewTop = mercY(req.view.north)
        val viewBottom = mercY(req.view.south)
        val viewSpanY = viewTop - viewBottom
        val viewSpanX = req.view.east - req.view.west
        if (viewSpanY <= 0 || viewSpanX <= 0) return null

        val top = min(viewTop + viewSpanY * MARGIN, MAX_MERC_Y)
        val bottom = max(viewBottom - viewSpanY * MARGIN, -MAX_MERC_Y)
        val west = max(req.view.west - viewSpanX * MARGIN, -180.0)
        val east = min(req.view.east + viewSpanX * MARGIN, 180.0)
        val box = GeoBox(north = latFromMercY(top), south = latFromMercY(bottom), east = east, west = west)

        val fullW = req.widthPx * (east - west) / viewSpanX
        val fullH = req.heightPx * (top - bottom) / viewSpanY
        val scale = min(1.0, sqrt(PIXEL_BUDGET / (fullW * fullH)))
        val w = (fullW * scale).roundToInt().coerceAtLeast(1)
        val h = (fullH * scale).roundToInt().coerceAtLeast(1)

        val px = Projector(box, top, bottom, w, h)
        val strokeDp = (0.8 + (req.zoom - 12) * 0.5).coerceIn(1.0, 4.0)
        val stroke = (strokeDp * req.density * scale).toFloat().coerceAtLeast(1f)

        val buffer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(buffer)

        // Query a little beyond the bitmap so routes leaving its edge are still drawn up to the edge.
        val query = GeoBox(
            north = min(box.north + (box.north - box.south) * 0.1, 90.0),
            south = max(box.south - (box.north - box.south) * 0.1, -90.0),
            east = box.east + (box.east - box.west) * 0.1,
            west = box.west - (box.east - box.west) * 0.1,
        )
        val count = store.countIn(query)
        if (req.mode == DisplayMode.ROUTES && count <= ROUTE_POINT_LIMIT) {
            drawRoutes(store, query, px, canvas, stroke)
        } else {
            drawGrid(store, query, px, canvas, stroke, w, h)
        }
        currentCoroutineContext().ensureActive()

        colorize(buffer)
        return Result(buffer, box)
    }

    private suspend fun drawRoutes(store: PointStore, query: GeoBox, px: Projector, canvas: Canvas, stroke: Float) {
        val tracks = store.tracksIn(query)
        currentCoroutineContext().ensureActive()

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
        var start = 0
        for (i in 1..tracks.size) {
            val endsHere = i == tracks.size || TrackRules.isBreak(
                tracks.time[i - 1], tracks.lat[i - 1], tracks.lon[i - 1],
                tracks.time[i], tracks.lat[i], tracks.lon[i],
                tracks.intervalMs[i],
            )
            if (!endsHere) continue

            if (i - start == 1) {
                // A lone fix (e.g. hourly sampling): show it as a dot.
                canvas.drawCircle(px.x(tracks.lon[start]), px.y(tracks.lat[start]), stroke * 1.2f, dot)
            } else {
                path.rewind()
                var lastX = px.x(tracks.lon[start])
                var lastY = px.y(tracks.lat[start])
                path.moveTo(lastX, lastY)
                for (j in start + 1 until i) {
                    val x = px.x(tracks.lon[j])
                    val y = px.y(tracks.lat[j])
                    // Skip sub-pixel steps; standing still for an hour shouldn't cost thousands of segments.
                    if (kotlin.math.abs(x - lastX) + kotlin.math.abs(y - lastY) < 0.75f) continue
                    path.lineTo(x, y)
                    lastX = x
                    lastY = y
                }
                canvas.drawPath(path, glow)
                canvas.drawPath(path, core)
            }
            start = i
            if (i % 2048 == 0) currentCoroutineContext().ensureActive()
        }
    }

    private suspend fun drawGrid(
        store: PointStore, query: GeoBox, px: Projector, canvas: Canvas, stroke: Float, w: Int, h: Int,
    ) {
        // Roughly one cell per 1.5 output pixels.
        val cellLon = (px.box.east - px.box.west) / w * 1.5
        val cellLat = (px.box.north - px.box.south) / h * 1.5
        val grid = store.gridIn(query, cellLat, cellLon)
        currentCoroutineContext().ensureActive()

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
    }

    /** Replaces the white density buffer with ramp colours, in place. */
    private fun colorize(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) row[x] = ramp[row[x] ushr 24]
            bitmap.setPixels(row, 0, w, 0, y, w, 1)
        }
    }

    private fun buildRamp(): IntArray {
        // (density, r, g, b)
        val stops = arrayOf(
            floatArrayOf(0.00f, 90f, 10f, 40f),
            floatArrayOf(PASS_ALPHA, 150f, 22f, 48f),
            floatArrayOf(0.35f, 225f, 62f, 22f),
            floatArrayOf(0.60f, 255f, 140f, 0f),
            floatArrayOf(0.82f, 255f, 212f, 90f),
            floatArrayOf(1.00f, 255f, 255f, 238f),
        )
        return IntArray(256) { a -> if (a == 0) Color.TRANSPARENT else rampColor(a / 255f, stops) }
    }

    private fun rampColor(t: Float, stops: Array<FloatArray>): Int {
        var k = 1
        while (k < stops.size - 1 && t > stops[k][0]) k++
        val lo = stops[k - 1]
        val hi = stops[k]
        val f = ((t - lo[0]) / (hi[0] - lo[0])).coerceIn(0f, 1f)
        // Fade in up to one full pass so antialiased edges stay smooth.
        val opacity = min(1f, t / PASS_ALPHA)
        return Color.argb(
            (opacity * 255).roundToInt(),
            (lo[1] + (hi[1] - lo[1]) * f).roundToInt(),
            (lo[2] + (hi[2] - lo[2]) * f).roundToInt(),
            (lo[3] + (hi[3] - lo[3]) * f).roundToInt(),
        )
    }

    /** Maps lat/lon to bitmap pixels; linear in Web Mercator, matching the map tiles. */
    private class Projector(val box: GeoBox, val top: Double, bottom: Double, w: Int, h: Int) {
        private val sx = w / (box.east - box.west)
        private val sy = h / (top - bottom)
        fun x(lon: Double) = ((lon - box.west) * sx).toFloat()
        fun y(lat: Double) = ((top - mercY(lat)) * sy).toFloat()
    }

    private const val MARGIN = 0.25
    private val MAX_MERC_Y = mercY(85.05)

    private fun mercY(lat: Double): Double {
        val clamped = lat.coerceIn(-85.05, 85.05)
        return ln(tan(PI / 4 + clamped * PI / 360)) * 180 / PI
    }

    private fun latFromMercY(y: Double): Double =
        (2 * kotlin.math.atan(kotlin.math.exp(y * PI / 180)) - PI / 2) * 180 / PI
}
