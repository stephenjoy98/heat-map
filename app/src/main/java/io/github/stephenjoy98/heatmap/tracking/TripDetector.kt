package io.github.stephenjoy98.heatmap.tracking

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.PendingPoint
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.TrackRules
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.data.Trip
import io.github.stephenjoy98.heatmap.ui.MainActivity
import com.google.android.gms.location.DetectedActivity

/**
 * Splits finished stretches of recording into trips and works out how each was travelled, from Android's
 * activity recognition plus speed. When it can't tell (any vehicle trip could be driving, a car share or
 * transit; slow "cycling" could be traffic), the trip is flagged so the Insights page asks the user.
 */
object TripDetector {
    const val QUESTIONS_CHANNEL_ID = "questions"
    private const val QUESTIONS_NOTIFICATION_ID = 2

    private const val MIN_SEGMENT_POINTS = 3
    private const val MIN_SEGMENT_METERS = 150.0
    private const val MIN_TRIP_METERS = 250.0

    private enum class Kind { STILL, WALK, CYCLE, VEHICLE }

    /** Processes all completed runs. Safe to call often; returns the number of new questions. */
    @Synchronized
    fun process(context: Context, now: Long = System.currentTimeMillis()): Int {
        val store = PointStore.get(context)
        var questions = 0
        while (true) {
            val pts = store.pendingPoints()
            if (pts.isEmpty()) break
            var start = 0
            var progressed = false
            for (i in 1..pts.size) {
                val ended = i == pts.size || isBreak(pts[i - 1], pts[i])
                if (!ended) continue
                val run = pts.subList(start, i)
                val last = run.last()
                // The newest run may still be going; wait until it can no longer be continued.
                val complete = i < pts.size || now - last.time > TrackRules.maxGapMs(last.intervalMs)
                if (!complete) break
                questions += processRun(store, run)
                progressed = true
                start = i
            }
            if (!progressed) break
        }
        if (questions > 0) notifyQuestions(context, store.tripsNeedingReview().size)
        return questions
    }

    private fun isBreak(a: PendingPoint, b: PendingPoint) =
        TrackRules.isBreak(a.time, a.lat, a.lon, b.time, b.lat, b.lon, b.intervalMs)

    private fun processRun(store: PointStore, run: List<PendingPoint>): Int {
        if (run.size < MIN_SEGMENT_POINTS) {
            store.markNoTrip(run.map { it.id })
            return 0
        }

        // Classify each fix, then smooth over a 5-fix window to remove single-fix noise.
        val raw = Array(run.size) { i ->
            val j = if (i == 0) 1 else i
            val a = run[j - 1]
            val b = run[j]
            val seconds = (b.time - a.time) / 1000.0
            val speed = if (seconds > 0) TrackRules.distanceMeters(a.lat, a.lon, b.lat, b.lon) / seconds else 0.0
            classify(speed, run[i].activity)
        }
        val kinds = Array(run.size) { i ->
            val window = (maxOf(0, i - 2)..minOf(run.size - 1, i + 2)).map { raw[it] }
            window.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        }

        // Group into same-kind segments, folding tiny ones into their neighbour.
        val segments = mutableListOf<MutableList<Int>>()
        for (i in run.indices) {
            if (segments.isEmpty() || kinds[segments.last().first()] != kinds[i]) {
                segments.add(mutableListOf(i))
            } else {
                segments.last().add(i)
            }
        }
        var merged = true
        while (merged && segments.size > 1) {
            merged = false
            for (s in segments.indices) {
                val seg = segments[s]
                if (seg.size < MIN_SEGMENT_POINTS || length(run, seg) < MIN_SEGMENT_METERS) {
                    val target = if (s > 0) segments[s - 1] else segments[s + 1]
                    if (s > 0) target.addAll(seg) else target.addAll(0, seg)
                    segments.removeAt(s)
                    merged = true
                    break
                }
            }
        }

        var questions = 0
        for (seg in segments) {
            val ids = seg.map { run[it].id }
            val kind = kinds[seg[seg.size / 2]].let { mid ->
                // After merging, use the segment's majority kind.
                seg.map { kinds[it] }.groupingBy { it }.eachCount().maxByOrNull { e -> e.value }?.key ?: mid
            }
            val meters = length(run, seg)
            if (kind == Kind.STILL || meters < MIN_TRIP_METERS) {
                store.markNoTrip(ids)
                continue
            }
            val first = run[seg.first()]
            val last = run[seg.last()]
            val decision = decide(store, kind, seg.map { run[it] }, first, last)
            store.createTrip(
                Trip(
                    id = 0,
                    startTime = first.time,
                    endTime = last.time,
                    distanceM = meters,
                    mode = decision.mode,
                    inferredMode = decision.mode,
                    confidence = decision.confidence,
                    needsReview = decision.ask,
                    startLat = first.lat,
                    startLon = first.lon,
                    endLat = last.lat,
                    endLon = last.lon,
                    remoteId = null,
                ),
                ids,
            )
            if (decision.ask) questions++
        }
        return questions
    }

    private class Decision(val mode: TravelMode, val confidence: Float, val ask: Boolean)

    private fun decide(store: PointStore, kind: Kind, pts: List<PendingPoint>, first: PendingPoint, last: PendingPoint): Decision {
        // Points restored from a backup keep the mode the user already gave them.
        val restored = pts.filter { it.mode != TravelMode.UNKNOWN.id }
        if (restored.size * 2 > pts.size) {
            val id = restored.groupingBy { it.mode }.eachCount().maxByOrNull { it.value }!!.key
            return Decision(TravelMode.fromId(id), 1f, ask = false)
        }

        val activities = pts.map { it.activity }
        fun share(vararg types: Int) = activities.count { it in types }.toFloat() / pts.size

        return when (kind) {
            Kind.WALK -> Decision(TravelMode.WALK, maxOf(0.6f, share(DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_FOOT)), ask = false)
            Kind.CYCLE -> {
                val bike = share(DetectedActivity.ON_BICYCLE)
                Decision(TravelMode.CYCLE, maxOf(0.3f, bike), ask = bike < 0.3f)
            }
            else -> {
                store.answeredModeForSimilarTrip(first.lat, first.lon, last.lat, last.lon)
                    ?.let { return Decision(it, 0.8f, ask = false) }
                Decision(store.mostCommonVehicleMode() ?: TravelMode.DRIVE, 0.4f, ask = true)
            }
        }
    }

    private fun classify(speed: Double, activity: Int): Kind = when {
        activity == DetectedActivity.IN_VEHICLE || speed > 9.0 -> Kind.VEHICLE // > ~32 km/h
        activity == DetectedActivity.ON_BICYCLE -> Kind.CYCLE
        activity == DetectedActivity.WALKING || activity == DetectedActivity.RUNNING || activity == DetectedActivity.ON_FOOT ->
            if (speed > 6.0) Kind.VEHICLE else Kind.WALK
        speed < 0.3 -> Kind.STILL
        speed < 2.2 -> Kind.WALK // < ~8 km/h
        else -> Kind.CYCLE // Could be a bike or slow traffic; low confidence, so it gets asked.
    }

    private fun length(run: List<PendingPoint>, seg: List<Int>): Double {
        var m = 0.0
        for (k in 1 until seg.size) {
            val a = run[seg[k - 1]]
            val b = run[seg[k]]
            m += TrackRules.distanceMeters(a.lat, a.lon, b.lat, b.lon)
        }
        return m
    }

    private fun notifyQuestions(context: Context, pending: Int) {
        if (pending == 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_TIMELINE)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (pending == 1) "How did you travel on your last trip?" else "$pending trips need a quick answer"
        val notification = NotificationCompat.Builder(context, QUESTIONS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ticker)
            .setContentTitle("How did you get around?")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(QUESTIONS_NOTIFICATION_ID, notification) }
    }
}
