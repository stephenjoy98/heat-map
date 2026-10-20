package io.github.stephenjoy98.heatmap.timeline

import android.content.Context
import android.location.Geocoder
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.PeriodStat
import io.github.stephenjoy98.heatmap.data.Place
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TrackRules
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.data.Trip
import io.github.stephenjoy98.heatmap.map.Smoothing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToLong

enum class TimelineScale(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

/** A day, week (Monday to Sunday), month or year, anchored on any date inside it. */
data class TimelinePeriod(val scale: TimelineScale, val anchor: LocalDate) {
    val start: LocalDate = when (scale) {
        TimelineScale.DAY -> anchor
        TimelineScale.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        TimelineScale.MONTH -> anchor.withDayOfMonth(1)
        TimelineScale.YEAR -> anchor.withDayOfYear(1)
    }
    val end: LocalDate = when (scale) {
        TimelineScale.DAY -> start.plusDays(1)
        TimelineScale.WEEK -> start.plusWeeks(1)
        TimelineScale.MONTH -> start.plusMonths(1)
        TimelineScale.YEAR -> start.plusYears(1)
    }

    private val zone get() = ZoneId.systemDefault()
    val fromMs: Long get() = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val toMs: Long get() = end.atStartOfDay(zone).toInstant().toEpochMilli()

    fun previous() = copy(anchor = shift(-1))
    fun next() = copy(anchor = shift(1))
    val isCurrent: Boolean get() = !LocalDate.now().isBefore(start) && LocalDate.now().isBefore(end)

    private fun shift(n: Long): LocalDate = when (scale) {
        TimelineScale.DAY -> anchor.plusDays(n)
        TimelineScale.WEEK -> anchor.plusWeeks(n)
        TimelineScale.MONTH -> anchor.plusMonths(n)
        TimelineScale.YEAR -> anchor.plusYears(n)
    }

    val label: String
        get() {
            val today = LocalDate.now()
            return when (scale) {
                TimelineScale.DAY -> when (anchor) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> anchor.format(DateTimeFormatter.ofPattern(if (anchor.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy"))
                }
                TimelineScale.WEEK -> {
                    val last = end.minusDays(1)
                    val f = DateTimeFormatter.ofPattern("d MMM")
                    "${start.format(f)} – ${last.format(f)}" + if (last.year != today.year) " ${last.year}" else ""
                }
                TimelineScale.MONTH -> start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                TimelineScale.YEAR -> start.year.toString()
            }
        }
}

/** One continuous line on the timeline map. */
class TimelineRoute(val color: Long, val lat: DoubleArray, val lon: DoubleArray) {
    val box: GeoBox = GeoBox(lat.max(), lat.min(), lon.max(), lon.min())
}

sealed class DayEntry {
    abstract val start: Long
    abstract val end: Long

    class Move(val trip: Trip, val box: GeoBox?) : DayEntry() {
        override val start get() = trip.startTime
        override val end get() = trip.endTime
    }

    class Stay(override val start: Long, override val end: Long, val lat: Double, val lon: Double, val place: String?) : DayEntry()

    /** Back-to-back trips in the same mode with no stop between them, shown as one; long-press for the parts. */
    class Group(val moves: List<Move>) : DayEntry() {
        override val start get() = moves.first().start
        override val end get() = moves.last().end
        val mode: TravelMode get() = moves.first().trip.mode
        val distanceM: Double get() = moves.sumOf { it.trip.distanceM }
        val needsReview: Boolean get() = moves.any { it.trip.needsReview }
        val box: GeoBox? get() = moves.mapNotNull { it.box }.takeIf { it.isNotEmpty() }?.let { b ->
            GeoBox(b.maxOf { it.north }, b.minOf { it.south }, b.maxOf { it.east }, b.minOf { it.west })
        }
    }
}

/** The entries with groups opened up into their trips. */
fun List<DayEntry>.flattened(): List<DayEntry> = flatMap { if (it is DayEntry.Group) it.moves else listOf(it) }

class TimelineData(
    val period: TimelinePeriod,
    val routes: List<TimelineRoute>,
    val bounds: GeoBox?,
    val kmByMode: Map<TravelMode, Double>,
    val trips: Int,
    val entries: List<DayEntry>,
    val stats: List<PeriodStat>,
) {
    val totalKm: Double get() = kmByMode.values.sum()
}

/** Loads everything the Timeline tab shows for a period. Call off the main thread. */
object TimelineRepository {
    private const val MIN_STAY_MS = 5 * 60_000L
    private const val MAX_GEOCODES = 12

    fun load(context: Context, period: TimelinePeriod): TimelineData {
        val store = PointStore.get(context)
        val from = period.fromMs
        val to = period.toMs
        val trips = store.tripsBetween(from, to)
        val smooth = Prefs.smoothRoutes.value

        // Each trip in its travel mode's colour, then any points outside trips in grey.
        val routes = mutableListOf<TimelineRoute>()
        val tripBoxes = HashMap<Long, GeoBox>()
        for (trip in trips) {
            val pts = store.tripDisplayPath(trip.id)
            val n = pts.size / 2
            if (n < 2) continue
            var lat = DoubleArray(n) { pts[it * 2] }
            var lon = DoubleArray(n) { pts[it * 2 + 1] }
            if (smooth && store.tripPathRow(trip.id)?.poly == null) {
                val (la, lo) = Smoothing.smooth(lat, lon, 0, n)
                lat = la
                lon = lo
            }
            val route = TimelineRoute(trip.mode.color, lat, lon)
            routes += route
            tripBoxes[trip.id] = route.box
        }
        val loose = store.untrippedTracks(from, to)
        var s = 0
        for (i in 1..loose.size) {
            val ends = i == loose.size || TrackRules.isBreak(
                loose.time[i - 1], loose.lat[i - 1], loose.lon[i - 1], loose.time[i], loose.lat[i], loose.lon[i], loose.intervalMs[i],
            )
            if (!ends) continue
            if (i - s >= 2) {
                val (la, lo) = if (smooth) Smoothing.smooth(loose.lat, loose.lon, s, i)
                else loose.lat.copyOfRange(s, i) to loose.lon.copyOfRange(s, i)
                routes += TimelineRoute(TravelMode.UNKNOWN.color, la, lo)
            }
            s = i
        }

        val kmByMode = trips.groupBy { it.mode }.mapValues { (_, t) -> t.sumOf { it.distanceM } / 1000.0 }
        val entries = if (period.scale == TimelineScale.DAY) dayEntries(context, store, trips, tripBoxes, from, to) else emptyList()
        val stats = when (period.scale) {
            TimelineScale.DAY -> emptyList()
            TimelineScale.YEAR -> store.periodStats(from, to, byMonth = true)
            else -> store.periodStats(from, to, byMonth = false)
        }
        return TimelineData(period, routes, store.boundsBetween(from, to), kmByMode, trips.size, entries, stats)
    }

    /** Trips and the stays between them, in order. */
    private fun dayEntries(
        context: Context, store: PointStore, trips: List<Trip>, boxes: Map<Long, GeoBox>, from: Long, to: Long,
    ): List<DayEntry> {
        val span = store.readableDatabase.rawQuery(
            "SELECT MIN(time), MAX(time), AVG(lat), AVG(lon), COUNT(*) FROM points WHERE time >= $from AND time < $to", null
        ).use { c -> if (c.moveToFirst() && c.getLong(4) > 0) doubleArrayOf(c.getLong(0).toDouble(), c.getLong(1).toDouble(), c.getDouble(2), c.getDouble(3)) else null }
            ?: return emptyList()
        val firstPoint = span[0].toLong()
        val lastPoint = span[1].toLong()
        val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null
        var lookups = 0
        fun name(lat: Double, lon: Double): String? {
            val key = "s:${(lat * 1000).roundToLong()}:${(lon * 1000).roundToLong()}"
            store.place(key)?.let { return it.city }
            if (geocoder == null || lookups >= MAX_GEOCODES) return null
            lookups++
            return streetName(geocoder, lat, lon)?.also { store.putPlace(key, Place(it, null)) }
        }

        if (trips.isEmpty()) {
            return listOf(DayEntry.Stay(firstPoint, lastPoint, span[2], span[3], name(span[2], span[3])))
        }
        val out = mutableListOf<DayEntry>()
        val first = trips.first()
        if (first.startTime - firstPoint >= MIN_STAY_MS) {
            out += DayEntry.Stay(firstPoint, first.startTime, first.startLat, first.startLon, name(first.startLat, first.startLon))
        }
        trips.forEachIndexed { i, trip ->
            out += DayEntry.Move(trip, boxes[trip.id])
            val nextStart = trips.getOrNull(i + 1)?.startTime ?: lastPoint
            if (nextStart - trip.endTime >= MIN_STAY_MS) {
                out += DayEntry.Stay(trip.endTime, nextStart, trip.endLat, trip.endLon, name(trip.endLat, trip.endLon))
            }
        }
        return grouped(out)
    }

    /** Joins runs of back-to-back trips in the same mode (nothing between them) into groups. */
    private fun grouped(entries: List<DayEntry>): List<DayEntry> {
        val out = mutableListOf<DayEntry>()
        var run = mutableListOf<DayEntry.Move>()
        fun flush() {
            if (run.size >= 2) out += DayEntry.Group(run) else out += run
            run = mutableListOf()
        }
        for (e in entries) {
            if (e is DayEntry.Move && (run.isEmpty() || run.last().trip.mode == e.trip.mode)) {
                run += e
            } else {
                flush()
                if (e is DayEntry.Move) run += e else out += e
            }
        }
        flush()
        return out
    }

    @Suppress("DEPRECATION") // The callback variant needs API 33; this runs on a background thread anyway.
    private fun streetName(geocoder: Geocoder, lat: Double, lon: Double): String? = runCatching {
        val a = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return@runCatching null
        val street = a.thoroughfare ?: a.featureName?.takeIf { it.any(Char::isLetter) }
        listOfNotNull(street, a.locality ?: a.subAdminArea).distinct().joinToString(", ").ifEmpty { null }
    }.getOrNull()
}
