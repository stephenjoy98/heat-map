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
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.HeatPalette
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.tracking.FollowMode
import io.github.stephenjoy98.heatmap.ui.MainActivity
import io.github.stephenjoy98.heatmap.ui.formatInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The follow me widget (2 wide, 1 high): whether you're recording and how often on the left (tap: the map), and on
 * the right a button that turns follow me ([FollowMode]: recording every second) on and off without opening the app.
 *
 * Android refreshes it about every 30 minutes; the app redraws it straight away when the recording status changes
 * ([watchStatus]) or follow me turns on or off. Drawing happens in [Render], off the main thread.
 */
class FollowWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        requestUpdate(context, force = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_FOLLOW) {
            // Straight from the home screen, without opening the app: on = record every second (starting recording
            // if it was off), off = back to the usual interval.
            val on = !FollowMode.on.value
            FollowMode.set(context, on, startRecording = true)
            Toast.makeText(context, if (on) "Follow me on: recording every second" else "Follow me off", Toast.LENGTH_SHORT).show()
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
        // Auto recording can change its interval every few seconds; don't redraw more often than this unasked.
        private const val MIN_GAP_MS = 60_000L
        private const val ACTION_TOGGLE_FOLLOW = "io.github.stephenjoy98.heatmap.TOGGLE_FOLLOW"

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

        /** Watches recording, its interval and saved places for the life of the app, redrawing when the status text changes. */
        fun watchStatus(context: Context) {
            val app = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                combine(Prefs.tracking, Prefs.liveIntervalSec, Prefs.autoInterval, Prefs.atPlace, Prefs.followBoost) { _, _, _, _, _ -> status() }
                    .distinctUntilChanged()
                    .collect { requestUpdate(app, force = true) }
            }
        }

        /** The two lines on the left: e.g. "Recording · Auto" over "every 15 s", "Recording at Home", or "Paused". */
        private fun status(): Pair<String, String> {
            if (!Prefs.tracking.value) return "Recording" to "Paused"
            val every = "every ${formatInterval(Prefs.liveIntervalSec.value)}"
            val place = Prefs.atPlace.value
            return when {
                FollowMode.on.value -> "Following you" to every
                place != null -> "Recording at ${place.name}" to every
                Prefs.autoInterval.value -> "Recording · Auto" to every
                else -> "Recording" to every
            }
        }

        private fun views(context: Context): RemoteViews {
            val density = context.resources.displayMetrics.density
            val views = RemoteViews(context.packageName, R.layout.widget_follow)
            val (label, value) = status()
            views.setTextViewText(R.id.widget_label, label)
            views.setTextViewText(R.id.widget_status, value)
            views.setOnClickPendingIntent(R.id.widget_trip, MainActivity.openIntent(context, MainActivity.PAGE_MAP))

            val on = FollowMode.on.value
            views.setImageViewBitmap(R.id.widget_follow, followButton(Prefs.heatPalette.value, on, (68 * density).roundToInt()))
            views.setContentDescription(R.id.widget_follow, if (on) "Follow me is on; tap to stop" else "Follow me is off; tap to record every second")
            val toggle = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, FollowWidget::class.java).setAction(ACTION_TOGGLE_FOLLOW),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_follow, toggle)
            return views
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
