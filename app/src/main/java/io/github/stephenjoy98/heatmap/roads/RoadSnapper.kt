package io.github.stephenjoy98.heatmap.roads

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.NewRoadQuestion
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Polyline
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.RoadOption
import io.github.stephenjoy98.heatmap.data.RoadQuestion
import io.github.stephenjoy98.heatmap.data.TrackRules
import io.github.stephenjoy98.heatmap.data.Trip
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.map.Smoothing
import io.github.stephenjoy98.heatmap.tracking.TripDetector
import io.github.stephenjoy98.heatmap.ui.MainActivity
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Snaps finished trips to OpenStreetMap roads.
 *
 * Each trip is map-matched with Valhalla using the network for its travel mode. Where the recorded trace
 * strays more than ~40 m from the matched road for a few fixes in a row, the app isn't sure which way you
 * went, so it asks: the Insights page shows the best match, Valhalla's alternative roads between the same two
 * points, and the line as recorded. Trips that stray almost everywhere (a trail walk, a train) keep their
 * smoothed recording instead of being forced onto roads.
 */
object RoadSnapper {
    const val STATUS_SNAPPED = 1
    const val STATUS_RECORDED = 2
    const val STATUS_WAITING = 3

    private const val DOUBT_METERS = 40.0
    private const val SAME_ROAD_METERS = 25.0
    private const val MAX_QUESTIONS_PER_TRIP = 3
    private const val MAX_DOUBTFUL_SHARE = 0.4
    private const val MAX_POINTS = 1500
    private const val REQUEST_GAP_MS = 1100L // Fair use of the free public service: about one request a second.
    private const val NOTIFICATION_ID = 4
    // A matched shape that goes further than this from every recorded dot, or is this much longer than the
    // recording, took a detour you never made: keep the recording instead.
    private const val STRAY_METERS = 250.0
    private const val STRAY_LENGTH_RATIO = 1.5
    private const val STRAY_LENGTH_SLACK_M = 300.0

    /** Matches every trip that hasn't been matched yet. Returns the number of new questions. */
    suspend fun processPending(context: Context): Int {
        if (!Prefs.snapToRoads.value) return 0
        val store = PointStore.get(context)
        if (!Prefs.snapsRechecked) {
            recheckSnapped(store)
            Prefs.snapsRechecked = true
        }
        if (!Prefs.tracksSnapped) {
            // Trains and boats used to keep their recorded line: match them to their tracks once.
            store.clearPaths(TravelMode.entries.filter { it.kind == TravelMode.Kind.TRACK }.map { it.id }, STATUS_RECORDED)
            Prefs.tracksSnapped = true
        }
        var questions = 0
        while (true) {
            val trips = store.tripsWithoutPath(10)
            if (trips.isEmpty()) break
            for (trip in trips) {
                questions += try {
                    snapTrip(store, trip)
                } catch (e: IOException) {
                    throw e // Offline or service busy: the worker retries later.
                } catch (e: Exception) {
                    // Something unexpected about this trip: keep its recording rather than retrying forever.
                    store.saveTripPath(trip.id, STATUS_RECORDED, null, null)
                    0
                }
                delay(REQUEST_GAP_MS)
            }
        }
        if (questions > 0) notify(context, store.openRoadQuestions().size)
        return questions
    }

    private suspend fun snapTrip(store: PointStore, trip: Trip): Int {
        val raw = store.tripPath(trip.id)
        val total = raw.size / 2
        if (total < 3) {
            store.saveTripPath(trip.id, STATUS_RECORDED, null, null)
            return 0
        }
        // Long trips are thinned to keep requests small; every fix still counts towards doubt detection below.
        val step = maxOf(1, total / MAX_POINTS)
        val idx = (0 until total step step).toMutableList().apply { if (last() != total - 1) add(total - 1) }
        val lat = DoubleArray(idx.size) { raw[idx[it] * 2] }
        val lon = DoubleArray(idx.size) { raw[idx[it] * 2 + 1] }
        val costing = Valhalla.costing(trip.mode)
        if (costing == null) {
            // Trains and boats: onto the railway or ferry route they ran on, or else the recorded line.
            val onLines = TrackSnapper.networkFor(trip.mode)?.let { TrackSnapper.snap(lat, lon, it) }
            if (onLines != null) {
                val sLat = DoubleArray(onLines.size / 2) { onLines[it * 2] }
                val sLon = DoubleArray(onLines.size / 2) { onLines[it * 2 + 1] }
                if (!strays(lat, lon, sLat, sLon, STRAY_METERS * 1.6, STRAY_LENGTH_RATIO)) {
                    val shape = Polyline.encode(sLat, sLon)
                    store.saveTripPath(trip.id, STATUS_SNAPPED, shape, shape)
                    return 0
                }
            }
            store.saveTripPath(trip.id, STATUS_RECORDED, null, null)
            return 0
        }

        val matched = Valhalla.match(lat, lon, costing)
        if (matched == null) {
            store.saveTripPath(trip.id, STATUS_RECORDED, null, null)
            return 0
        }
        val mLat = DoubleArray(matched.size / 2) { matched[it * 2] }
        val mLon = DoubleArray(matched.size / 2) { matched[it * 2 + 1] }
        if (strays(lat, lon, mLat, mLon, STRAY_METERS, STRAY_LENGTH_RATIO)) {
            store.saveTripPath(trip.id, STATUS_RECORDED, null, null)
            return 0
        }
        val nearest = nearestSegments(lat, lon, mLat, mLon)
        val stretches = doubtfulStretches(nearest.distance)
        val base = Polyline.encode(mLat, mLon)

        if (stretches.isEmpty()) {
            store.saveTripPath(trip.id, STATUS_SNAPPED, base, base)
            return 0
        }
        val doubtfulShare = nearest.distance.count { it > DOUBT_METERS }.toDouble() / lat.size
        if (stretches.size > MAX_QUESTIONS_PER_TRIP || doubtfulShare > MAX_DOUBTFUL_SHARE) {
            // Mostly off the road network: keep the (smoothed) recording.
            store.saveTripPath(trip.id, STATUS_RECORDED, null, null)
            return 0
        }

        val questions = stretches.map { (s, e) ->
            val from = nearest.segment[s].coerceIn(0, mLat.size - 2)
            val to = (nearest.segment[e] + 1).coerceIn(from + 1, mLat.size - 1)
            delay(REQUEST_GAP_MS)
            NewRoadQuestion(from, to, options(lat, lon, s, e, mLat, mLon, from, to, costing))
        }
        store.saveTripPath(trip.id, STATUS_WAITING, base, base)
        store.addRoadQuestions(trip.id, questions)
        return questions.size
    }

    /** The candidate ways for one doubtful stretch, best match first and the recording last. */
    private fun options(
        lat: DoubleArray, lon: DoubleArray, s: Int, e: Int,
        mLat: DoubleArray, mLon: DoubleArray, from: Int, to: Int,
        costing: String,
    ): List<RoadOption> {
        val bestLat = mLat.copyOfRange(from, to + 1)
        val bestLon = mLon.copyOfRange(from, to + 1)
        var bestLabel = "Best match"
        val roads = mutableListOf<Pair<DoubleArray, DoubleArray>>()
        val labels = mutableListOf<String>()

        val alternatives = runCatching { Valhalla.routes(lat[s], lon[s], lat[e], lon[e], costing) }.getOrDefault(emptyList())
        for (alt in alternatives) {
            val aLat = DoubleArray(alt.points.size / 2) { alt.points[it * 2] }
            val aLon = DoubleArray(alt.points.size / 2) { alt.points[it * 2 + 1] }
            if (separation(aLat, aLon, bestLat, bestLon) < SAME_ROAD_METERS) {
                // The same road as the best match; borrow its name.
                alt.streetName?.let { bestLabel = it }
                continue
            }
            if (roads.any { separation(aLat, aLon, it.first, it.second) < SAME_ROAD_METERS }) continue
            roads += aLat to aLon
            labels += alt.streetName ?: "Other road"
        }

        val (recLat, recLon) = Smoothing.smooth(lat, lon, s, e + 1)
        return buildList {
            add(RoadOption(bestLabel, Polyline.encode(bestLat, bestLon)))
            roads.forEachIndexed { i, (a, b) -> add(RoadOption(labels[i], Polyline.encode(a, b))) }
            add(RoadOption("As recorded", Polyline.encode(recLat, recLon), recorded = true))
        }
    }

    /** Records an answer and rebuilds the trip's shape with every answer so far spliced in. */
    fun answer(context: Context, question: RoadQuestion, choice: Int) {
        val store = PointStore.get(context)
        store.answerRoadQuestion(question.id, choice)
        rebuild(store, question.tripId)
    }

    private fun rebuild(store: PointStore, tripId: Long) {
        val row = store.tripPathRow(tripId) ?: return
        val basePoly = row.basePoly ?: return
        val base = Polyline.decode(basePoly)
        val n = base.size / 2
        val outLat = ArrayList<Double>()
        val outLon = ArrayList<Double>()
        fun addBase(fromIdx: Int, toIdx: Int) {
            for (i in fromIdx until toIdx.coerceAtMost(n)) {
                outLat += base[i * 2]
                outLon += base[i * 2 + 1]
            }
        }

        val questions = store.roadQuestionsForTrip(tripId).sortedBy { it.baseFrom }
        var cursor = 0
        for (q in questions) {
            val from = maxOf(q.baseFrom, cursor)
            if (from >= q.baseTo) continue
            addBase(cursor, from)
            val chosen = q.options.getOrNull(q.chosen)
            if (chosen == null) {
                addBase(from, q.baseTo)
            } else {
                val pts = Polyline.decode(chosen.poly)
                for (i in pts.indices step 2) {
                    outLat += pts[i]
                    outLon += pts[i + 1]
                }
            }
            cursor = q.baseTo
        }
        addBase(cursor, n)

        val status = if (questions.all { it.chosen >= 0 }) STATUS_SNAPPED else STATUS_WAITING
        store.saveTripPath(tripId, status, basePoly, Polyline.encode(outLat.toDoubleArray(), outLon.toDoubleArray()))
    }

    /**
     * Whether a matched shape wanders where the recording never went: some part of it is more than [maxMeters]
     * from the recorded line, or it's over [lengthRatio] times as long. Every dot can be near the shape while the
     * shape still takes a detour (an out-and-back down another street), which drew straight lines to nowhere.
     */
    private fun strays(lat: DoubleArray, lon: DoubleArray, mLat: DoubleArray, mLon: DoubleArray, maxMeters: Double, lengthRatio: Double): Boolean {
        if (lat.size < 2 || mLat.size < 2) return false
        if (length(mLat, mLon) > length(lat, lon) * lengthRatio + STRAY_LENGTH_SLACK_M) return true
        // Check the shape's points (about 500 at most) against the recorded line.
        val step = maxOf(1, mLat.size / 500)
        for (j in mLat.indices step step) {
            var best = Double.MAX_VALUE
            for (i in 0 until lat.size - 1) {
                best = minOf(best, pointToSegment(mLat[j], mLon[j], lat[i], lon[i], lat[i + 1], lon[i + 1]))
                if (best <= maxMeters) break
            }
            if (best > maxMeters) return true
        }
        return false
    }

    private fun length(lat: DoubleArray, lon: DoubleArray): Double {
        var sum = 0.0
        for (i in 1 until lat.size) sum += TrackRules.distanceMeters(lat[i - 1], lon[i - 1], lat[i], lon[i])
        return sum
    }

    /**
     * Once: trips matched before the detour check came in go back to their recording if their road shape strays
     * well away (a stricter test than for new trips, so roads picked by hand with Fix route stay).
     */
    private fun recheckSnapped(store: PointStore) {
        for (id in store.snappedTripIds()) {
            val poly = store.tripPathRow(id)?.poly ?: continue
            val shape = Polyline.decode(poly)
            val raw = store.tripPath(id)
            if (raw.size < 4 || shape.size < 4) continue
            val step = maxOf(1, (raw.size / 2) / MAX_POINTS)
            val idx = (0 until raw.size / 2 step step).toList()
            val lat = DoubleArray(idx.size) { raw[idx[it] * 2] }
            val lon = DoubleArray(idx.size) { raw[idx[it] * 2 + 1] }
            val mLat = DoubleArray(shape.size / 2) { shape[it * 2] }
            val mLon = DoubleArray(shape.size / 2) { shape[it * 2 + 1] }
            if (strays(lat, lon, mLat, mLon, STRAY_METERS * 1.6, STRAY_LENGTH_RATIO * 0.95)) {
                store.dropRoadQuestions(id)
                store.saveTripPath(id, STATUS_RECORDED, null, null)
            }
        }
    }

    private class Nearest(val distance: DoubleArray, val segment: IntArray)

    /** For each fix, its distance to the matched shape and the nearest segment (searched in a moving window). */
    private fun nearestSegments(lat: DoubleArray, lon: DoubleArray, mLat: DoubleArray, mLon: DoubleArray): Nearest {
        val distance = DoubleArray(lat.size)
        val segment = IntArray(lat.size)
        var last = 0
        for (i in lat.indices) {
            var best = Double.MAX_VALUE
            var bestJ = last
            val lo = maxOf(0, last - 20)
            val hi = minOf(mLat.size - 2, last + 400)
            for (j in lo..hi) {
                val d = pointToSegment(lat[i], lon[i], mLat[j], mLon[j], mLat[j + 1], mLon[j + 1])
                if (d < best) {
                    best = d
                    bestJ = j
                }
            }
            distance[i] = if (best == Double.MAX_VALUE) 0.0 else best
            segment[i] = bestJ
            last = bestJ
        }
        return Nearest(distance, segment)
    }

    /** Runs of at least two doubtful fixes, widened by one fix each side to reliable anchors. */
    private fun doubtfulStretches(distance: DoubleArray): List<Pair<Int, Int>> {
        val runs = mutableListOf<IntArray>()
        var i = 0
        while (i < distance.size) {
            if (distance[i] <= DOUBT_METERS) {
                i++
                continue
            }
            var j = i
            while (j + 1 < distance.size && distance[j + 1] > DOUBT_METERS) j++
            if (j > i) {
                val prev = runs.lastOrNull()
                // Merge with the previous run if only a couple of fixes separate them.
                if (prev != null && i - prev[1] <= 3) prev[1] = j else runs += intArrayOf(i, j)
            }
            i = j + 1
        }
        return runs.map { (maxOf(0, it[0] - 1)) to minOf(distance.size - 1, it[1] + 1) }
    }

    /** Roughly how far apart two lines are: the larger of their average distances to each other. */
    private fun separation(aLat: DoubleArray, aLon: DoubleArray, bLat: DoubleArray, bLon: DoubleArray): Double =
        maxOf(meanDistance(aLat, aLon, bLat, bLon), meanDistance(bLat, bLon, aLat, aLon))

    private fun meanDistance(aLat: DoubleArray, aLon: DoubleArray, bLat: DoubleArray, bLon: DoubleArray): Double {
        if (aLat.isEmpty() || bLat.size < 2) return Double.MAX_VALUE
        val samples = minOf(30, aLat.size)
        var sum = 0.0
        for (k in 0 until samples) {
            val i = if (samples == 1) 0 else k * (aLat.size - 1) / (samples - 1)
            var best = Double.MAX_VALUE
            for (j in 0 until bLat.size - 1) {
                best = minOf(best, pointToSegment(aLat[i], aLon[i], bLat[j], bLon[j], bLat[j + 1], bLon[j + 1]))
            }
            sum += best
        }
        return sum / samples
    }

    /** Distance in metres from a point to a segment, on a local flat projection. */
    private fun pointToSegment(pLat: Double, pLon: Double, aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val mPerLat = 111_320.0
        val mPerLon = 111_320.0 * cos(Math.toRadians(pLat))
        val ax = (aLon - pLon) * mPerLon
        val ay = (aLat - pLat) * mPerLat
        val bx = (bLon - pLon) * mPerLon
        val by = (bLat - pLat) * mPerLat
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt(cx * cx + cy * cy)
    }

    private fun notify(context: Context, pending: Int) {
        if (pending == 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        // Road questions are answered on the Timeline's Questions tab.
        val open = MainActivity.openIntent(context, MainActivity.PAGE_QUESTIONS)
        val notification = NotificationCompat.Builder(context, TripDetector.QUESTIONS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ticker)
            .setContentTitle("Which road did you take?")
            .setContentText(if (pending == 1) "One part of a trip could have gone two ways" else "$pending parts of your trips could have gone different ways")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}

/** Runs road snapping in the background whenever there's a network connection. */
class RoadSnapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        TripDetector.process(applicationContext)
        RoadSnapper.processPending(applicationContext)
        Result.success()
    } catch (e: IOException) {
        if (runAttemptCount < 5) Result.retry() else Result.failure()
    }

    companion object {
        fun runNow(context: Context) {
            if (!Prefs.snapToRoads.value) return
            val request = OneTimeWorkRequestBuilder<RoadSnapWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("snap-roads", ExistingWorkPolicy.KEEP, request)
        }
    }
}
