package io.github.stephenjoy98.heatmap.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.CurrentTrip
import io.github.stephenjoy98.heatmap.data.HeatPalette
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TrackRules
import io.github.stephenjoy98.heatmap.review.Insights
import io.github.stephenjoy98.heatmap.ui.FollowRequest
import io.github.stephenjoy98.heatmap.ui.MainActivity
import kotlin.math.roundToInt

/**
 * The follow me widget (2 wide, 1 high): the current trip's distance on the left (or the last trip's when you're
 * not on one), and on the right a button that turns follow me on (opening the map following you) and off.
 *
 * Android refreshes it about every 30 minutes; the app also asks for a refresh as dots are processed (at most
 * every [MIN_GAP_MS]) and straight away when follow me turns on or off. Drawing happens in [Render], off the main
 * thread.
 */
class FollowWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        requestUpdate(context, force = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP_FOLLOW) {
            // Follow me lives on the map; if the app was closed since, it's already off and this just redraws.
            if (FollowRequest.active.value) FollowRequest.stop.value = true
            requestUpdate(context, force = true)
            return
        }
        super.onReceive(context, intent)
    }

    /** Draws every placed widget. */
    class Render(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val manager = AppWidgetManager.getInstance(applicationContext)
            val ids = manager.getAppWidgetIds(ComponentName(applicationContext, FollowWidget::class.java))
            if (ids.isNotEmpty()) {
                val views = runCatching { views(applicationContext) }.getOrNull()
                if (views != null) for (id in ids) runCatching { manager.updateAppWidget(id, views) }
            }
            lastRender = System.currentTimeMillis()
            return Result.success()
        }
    }

    companion object {
        private const val MIN_GAP_MS = 60_000L
        private const val ACTION_STOP_FOLLOW = "io.github.stephenjoy98.heatmap.STOP_FOLLOW"

        @Volatile
        private var lastRender = 0L

        /** Redraws the widgets if any are placed; without [force], at most every [MIN_GAP_MS]. */
        fun requestUpdate(context: Context, force: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, FollowWidget::class.java)) }.getOrNull()
            if (ids == null || ids.isEmpty()) return
            if (!force && System.currentTimeMillis() - lastRender < MIN_GAP_MS) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                "follow-widget",
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<Render>().build(),
            )
        }

        private fun views(context: Context): RemoteViews {
            val density = context.resources.displayMetrics.density
            val store = PointStore.get(context)
            val views = RemoteViews(context.packageName, R.layout.widget_follow)

            val current = currentTripMeters(store)
            if (current != null) {
                views.setTextViewText(R.id.widget_label, "Current trip")
                views.setTextViewText(R.id.widget_distance, Insights.km(current / 1000))
            } else {
                val last = runCatching { store.latestTrip() }.getOrNull()
                views.setTextViewText(R.id.widget_label, if (last != null) "Last trip" else "No trips yet")
                views.setTextViewText(R.id.widget_distance, last?.let { Insights.km(it.distanceM / 1000) } ?: "–")
            }
            views.setOnClickPendingIntent(R.id.widget_trip, MainActivity.openIntent(context))

            val on = FollowRequest.active.value
            views.setImageViewBitmap(R.id.widget_follow, followButton(Prefs.heatPalette.value, on, (48 * density).roundToInt()))
            views.setContentDescription(R.id.widget_follow, if (on) "Follow me is on; tap to stop" else "Follow me is off; tap to follow you on the map")
            val tap = if (on) {
                PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, FollowWidget::class.java).setAction(ACTION_STOP_FOLLOW),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                MainActivity.openIntent(context, MainActivity.PAGE_FOLLOW)
            }
            views.setOnClickPendingIntent(R.id.widget_follow, tap)
            return views
        }

        /** Metres since you last stopped, along the newest run of dots, while recording; null when not on a trip. */
        private fun currentTripMeters(store: PointStore): Double? {
            if (!Prefs.tracking.value) return null
            val pts = runCatching { store.pendingPoints() }.getOrNull() ?: return null
            val last = pts.lastOrNull() ?: return null
            if (System.currentTimeMillis() - last.time > TrackRules.maxGapMs(last.intervalMs)) return null
            var s = pts.size - 1
            while (s > 0 && !TrackRules.isBreak(pts[s - 1].time, pts[s - 1].lat, pts[s - 1].lon, pts[s].time, pts[s].lat, pts[s].lon, pts[s].intervalMs)) s--
            val run = pts.subList(s, pts.size)
            return CurrentTrip.meters(
                LongArray(run.size) { run[it].time },
                DoubleArray(run.size) { run[it].lat },
                DoubleArray(run.size) { run[it].lon },
            )
        }

        /**
         * The follow me button. On: the map's follow marker (dark disc, glowing ring in your route colour, grey arrow).
         * Off: a plain dark disc with a dim arrow and no glow.
         */
        private fun followButton(palette: HeatPalette, on: Boolean, px: Int): Bitmap {
            val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val glow = when (palette) {
                HeatPalette.FIRE -> Color.rgb(128, 10, 44)
                HeatPalette.ICE -> Color.rgb(18, 55, 165)
                HeatPalette.WHITE -> Color.rgb(150, 150, 155)
            }
            val cx = px / 2f
            val r = px * 0.34f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            if (on) {
                p.color = glow
                for (i in 12 downTo 1) {
                    p.alpha = 18
                    c.drawCircle(cx, cx, r + i * px * 0.011f, p)
                }
                p.alpha = 255
            }
            p.color = if (on) Color.rgb(36, 33, 38) else Color.rgb(44, 45, 50)
            c.drawCircle(cx, cx, r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = px * 0.05f
            p.color = if (on) glow else Color.rgb(70, 72, 78)
            c.drawCircle(cx, cx, r - p.strokeWidth / 2, p)
            p.style = Paint.Style.FILL
            p.color = if (on) Color.rgb(200, 200, 205) else Color.rgb(140, 142, 148)
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
