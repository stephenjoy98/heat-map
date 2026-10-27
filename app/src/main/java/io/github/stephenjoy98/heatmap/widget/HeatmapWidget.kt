package io.github.stephenjoy98.heatmap.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.DisplayMode
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.HeatPalette
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.map.HeatRamps
import io.github.stephenjoy98.heatmap.map.HeatRenderer
import io.github.stephenjoy98.heatmap.map.OwnTracks
import io.github.stephenjoy98.heatmap.review.Insights
import io.github.stephenjoy98.heatmap.ui.MainActivity
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The mini heatmap widget: your heatmap (in your heat colours) around your latest position, today's km, and a
 * follow-me button that opens the map already following you. Tapping the map opens the app.
 *
 * Android refreshes it about every 30 minutes; the app also asks for a refresh as trips are worked out, at
 * most every [MIN_GAP_MS]. Drawing happens in [Render], off the main thread.
 */
class HeatmapWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        requestUpdate(context, force = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        // Resized: draw it again at the new size.
        requestUpdate(context, force = true)
    }

    /** Draws every placed widget. */
    class Render(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val manager = AppWidgetManager.getInstance(applicationContext)
            val ids = manager.getAppWidgetIds(ComponentName(applicationContext, HeatmapWidget::class.java))
            for (id in ids) {
                runCatching { manager.updateAppWidget(id, views(applicationContext, manager, id)) }
            }
            lastRender = System.currentTimeMillis()
            return Result.success()
        }
    }

    companion object {
        private const val MIN_GAP_MS = 10 * 60_000L
        // How much of the map the widget shows across, in degrees of longitude (about 4–5 km).
        private const val SPAN_LON_DEG = 0.06
        private const val MAX_PX = 720

        @Volatile
        private var lastRender = 0L

        /** Redraws the widgets if any are placed; without [force], at most every [MIN_GAP_MS]. */
        fun requestUpdate(context: Context, force: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, HeatmapWidget::class.java)) }.getOrNull()
            if (ids == null || ids.isEmpty()) return
            if (!force && System.currentTimeMillis() - lastRender < MIN_GAP_MS) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                "heatmap-widget",
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<Render>().build(),
            )
        }

        private fun views(context: Context, manager: AppWidgetManager, id: Int): RemoteViews {
            val density = context.resources.displayMetrics.density
            val options = manager.getAppWidgetOptions(id)
            val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0).takeIf { it > 0 } ?: 180
            val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: 180
            var w = (wDp * density).roundToInt()
            var h = (hDp * density).roundToInt()
            val scale = min(1f, MAX_PX.toFloat() / max(w, h))
            w = (w * scale).roundToInt().coerceAtLeast(64)
            h = (h * scale).roundToInt().coerceAtLeast(64)

            val store = PointStore.get(context)
            val palette = Prefs.heatPalette.value
            val views = RemoteViews(context.packageName, R.layout.widget_heatmap)
            views.setImageViewBitmap(R.id.widget_heat, heatmap(store, palette, w, h, 24 * density * scale))
            views.setImageViewBitmap(R.id.widget_follow, followButton(palette, (44 * density).roundToInt()))
            val todayKm = runCatching {
                store.kmByArea(PointStore.startOfToday(), System.currentTimeMillis()).values.sum()
            }.getOrDefault(0.0)
            views.setTextViewText(R.id.widget_km, "Today ${Insights.km(todayKm)}")
            views.setOnClickPendingIntent(R.id.widget_heat, MainActivity.openIntent(context))
            views.setOnClickPendingIntent(R.id.widget_follow, MainActivity.openIntent(context, MainActivity.PAGE_FOLLOW))
            return views
        }

        /** Your heatmap around the newest dot, on a dark background with rounded corners, and a blue dot where you are. */
        private fun heatmap(store: PointStore, palette: HeatPalette, w: Int, h: Int, radius: Float): Bitmap {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW) }
            canvas.clipPath(clip)
            canvas.drawColor(Color.rgb(18, 19, 22))
            val latest = store.latest() ?: return out
            // A square tile around you, as wide as the widget's longer side, cut to the widget's shape.
            val size = max(w, h)
            val lonSpan = SPAN_LON_DEG * size / w.coerceAtLeast(1)
            val latSpan = lonSpan * cos(Math.toRadians(latest.lat))
            val box = GeoBox(latest.lat + latSpan / 2, latest.lat - latSpan / 2, latest.lon + lonSpan / 2, latest.lon - lonSpan / 2)
            val zoom = ln(360.0 / lonSpan) / ln(2.0)
            val tile = runCatching {
                HeatRenderer.renderTile(OwnTracks(store, TravelMode.entries.toSet()), box, size, zoom, HeatRamps.own(palette, true), DisplayMode.ROUTES)
            }.getOrNull()
            if (tile != null) {
                canvas.drawBitmap(tile, (w - size) / 2f, (h - size) / 2f, null)
                tile.recycle()
            }
            // Where you are (the newest dot), in the middle.
            val r = size / 90f + 4f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.argb(70, 66, 133, 244)
            canvas.drawCircle(w / 2f, h / 2f, r * 2.2f, p)
            p.color = Color.WHITE
            canvas.drawCircle(w / 2f, h / 2f, r * 1.25f, p)
            p.color = Color.rgb(66, 133, 244)
            canvas.drawCircle(w / 2f, h / 2f, r, p)
            return out
        }

        /** The follow me marker as a button: dark disc, glowing ring in your route colour, grey arrow. */
        private fun followButton(palette: HeatPalette, px: Int): Bitmap {
            val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val glow = when (palette) {
                HeatPalette.FIRE -> Color.rgb(128, 10, 44)
                HeatPalette.ICE -> Color.rgb(18, 55, 165)
                HeatPalette.WHITE -> Color.rgb(150, 150, 155)
            }
            val cx = px / 2f
            val r = px * 0.32f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = glow
            for (i in 12 downTo 1) {
                p.alpha = 16
                c.drawCircle(cx, cx, r + i * px * 0.012f, p)
            }
            p.alpha = 255
            p.color = Color.rgb(36, 33, 38)
            c.drawCircle(cx, cx, r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = px * 0.05f
            p.color = glow
            c.drawCircle(cx, cx, r - p.strokeWidth / 2, p)
            p.style = Paint.Style.FILL
            p.color = Color.rgb(200, 200, 205)
            val a = r * 0.55f
            val arrow = Path().apply {
                moveTo(cx, cx - a)
                lineTo(cx + a * 0.8f, cx + a * 0.8f)
                lineTo(cx, cx + a * 0.35f)
                lineTo(cx - a * 0.8f, cx + a * 0.8f)
                close()
            }
            c.drawPath(arrow, p)
            return out
        }
    }
}
