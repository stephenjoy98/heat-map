package io.github.stephenjoy98.heatmap.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.cos
import kotlin.math.hypot
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

data class LocationPoint(
    val time: Long,
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val intervalMs: Int,
    val activity: Int = -1,
    val mode: Int = TravelMode.UNKNOWN.id,
    /** Height in metres (above sea level where the phone reports it), or null if the fix had none. */
    val altitude: Double? = null,
)

data class GeoBox(val north: Double, val south: Double, val east: Double, val west: Double) {
    fun intersects(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) =
        minLat <= north && maxLat >= south && minLon <= east && maxLon >= west
}

/** Points in time order, held in primitive arrays so large histories stay cheap to render. */
class TrackData(
    val size: Int,
    val time: LongArray,
    val lat: DoubleArray,
    val lon: DoubleArray,
    val intervalMs: IntArray,
)

/** Point counts per grid cell; used when there are too many points to draw as routes. */
class GridData(
    val size: Int,
    val row: IntArray,
    val col: IntArray,
    val count: IntArray,
)

/** A point that has not been assigned to a trip yet. */
class PendingPoint(
    val id: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val intervalMs: Int,
    val activity: Int,
    val mode: Int,
)

data class Trip(
    val id: Long,
    val startTime: Long,
    val endTime: Long,
    val distanceM: Double,
    val mode: TravelMode,
    val inferredMode: TravelMode,
    val confidence: Float,
    val needsReview: Boolean,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val remoteId: String?,
) {
    val avgSpeedKmh: Double
        get() = if (endTime > startTime) distanceM / ((endTime - startTime) / 1000.0) * 3.6 else 0.0
}

data class Stats(val total: Long, val today: Long, val latest: LocationPoint?)

/** A coarse (~5 km) area visited during a period, used to work out cities. */
class CoarseArea(val key: String, val lat: Double, val lon: Double, val points: Int, val days: Int = 0)

class Place(val city: String?, val country: String?, val state: String? = null, val countryCode: String? = null)

class FriendRoute(val docId: String, val mode: TravelMode, val poly: String)

class EditablePoint(val id: Long, val time: Long, val lat: Double, val lon: Double, val accuracy: Float)

/** A trip as kept in a backup: when it ran, how it was travelled, and its road shape and road answers. */
class TripBackup(
    val start: Long,
    val end: Long,
    val mode: Int,
    val inferredMode: Int,
    val confidence: Double,
    val needsReview: Boolean,
    val pathStatus: Int?,
    val basePoly: String?,
    val poly: String?,
    /** Road questions as JSON: [{"from", "to", "options", "chosen"}]. */
    val questions: String?,
    /** Its shared route's document id, so a restore updates that route instead of adding a copy. */
    val remoteId: String? = null,
)

/** A height answer as kept in a backup. */
class LevelBackup(val area: Long, val lat: Double, val lon: Double, val low: Double, val high: Double, val decision: Int)

/** A run of dots with heights: its trip (0 or -1 outside trips), its [HeightGrid] pass and the dots. */
class ElevationRun(val tripId: Long, val pass: Long, val dots: List<ElevationPoint>)

/** A place recorded at two clearly different heights ([low] and [high], metres above sea level). */
class LevelQuestion(val area: Long, val lat: Double, val lon: Double, val low: Double, val high: Double)

class DeleteResult(val days: Set<String>, val removedRemoteIds: List<String>, val createdTripId: Long = 0)

/**
 * How trips looked over a stretch of time before an edit on the Timeline, so the edit can be undone: each dot's
 * trip and mode, and the trips there with their road shapes and road questions.
 */
class EditSnapshot internal constructor(
    internal val from: Long,
    internal val to: Long,
    internal val points: List<LongArray>,
    internal val trips: List<ContentValues>,
    internal val paths: List<ContentValues>,
    internal val questions: List<ContentValues>,
)

/** A spot where a lot of time was recorded. */
class Spot(val lat: Double, val lon: Double, val hours: Double)

/** Battery used while recording at one interval: [hours] spent at it and [percent] of the battery used. */
class BatteryUse(val intervalSec: Int, val hours: Double, val percent: Double) {
    val percentPerHour: Double get() = if (hours > 0) percent / hours else 0.0
}

class PeriodStat(
    val key: String,
    val meters: Double,
    val trips: Int,
    val points: Int,
    val metersByMode: Map<TravelMode, Double>,
)

class TripPathRow(val status: Int, val basePoly: String?, val poly: String?)

/** One way a stretch of a trip could have gone: a road (named if known) or the recording itself. */
class RoadOption(val label: String, val poly: String, val recorded: Boolean = false) {
    companion object {
        fun encodeList(options: List<RoadOption>): String = org.json.JSONArray().apply {
            options.forEach { put(org.json.JSONObject().put("label", it.label).put("poly", it.poly).put("recorded", it.recorded)) }
        }.toString()

        fun decodeList(json: String): List<RoadOption> = runCatching {
            val arr = org.json.JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                RoadOption(o.getString("label"), o.getString("poly"), o.optBoolean("recorded"))
            }
        }.getOrDefault(emptyList())
    }
}

class NewRoadQuestion(val baseFrom: Int, val baseTo: Int, val options: List<RoadOption>)

/** A doubtful stretch between points [baseFrom]..[baseTo] of the trip's matched shape. [chosen] is -1 until answered. */
class RoadQuestion(
    val id: Long,
    val tripId: Long,
    val baseFrom: Int,
    val baseTo: Int,
    val options: List<RoadOption>,
    val chosen: Int,
)

class PointStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "points.db", null, 10) {

    private val _changes = MutableStateFlow(0L)

    /** Bumps every time points or trip modes change. */
    val changes: StateFlow<Long> = _changes

    private val _friendChanges = MutableStateFlow(0L)

    /** Bumps when cached friend routes change. */
    val friendChanges: StateFlow<Long> = _friendChanges

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                time INTEGER NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                accuracy REAL NOT NULL,
                interval_ms INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX points_lat_lon ON points(lat, lon)")
        onUpgrade(db, 1, 2)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE points ADD COLUMN mode INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE points ADD COLUMN activity INTEGER NOT NULL DEFAULT -1")
            db.execSQL("ALTER TABLE points ADD COLUMN trip_id INTEGER NOT NULL DEFAULT 0")
            // Restores merge by timestamp, so make time unique (dropping any accidental duplicates first).
            db.execSQL("DELETE FROM points WHERE id NOT IN (SELECT MIN(id) FROM points GROUP BY time)")
            db.execSQL("DROP INDEX IF EXISTS points_time")
            db.execSQL("CREATE UNIQUE INDEX points_time ON points(time)")
            db.execSQL("CREATE INDEX points_trip ON points(trip_id)")
            db.execSQL(
                """CREATE TABLE trips (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    start_time INTEGER NOT NULL,
                    end_time INTEGER NOT NULL,
                    distance_m REAL NOT NULL,
                    mode INTEGER NOT NULL,
                    inferred_mode INTEGER NOT NULL,
                    confidence REAL NOT NULL,
                    needs_review INTEGER NOT NULL,
                    start_lat REAL NOT NULL,
                    start_lon REAL NOT NULL,
                    end_lat REAL NOT NULL,
                    end_lon REAL NOT NULL,
                    uploaded INTEGER NOT NULL DEFAULT 0,
                    remote_id TEXT
                )"""
            )
            db.execSQL("CREATE INDEX trips_time ON trips(start_time)")
            db.execSQL("CREATE TABLE places (cell TEXT PRIMARY KEY, city TEXT, country TEXT)")
            db.execSQL(
                """CREATE TABLE friend_routes (
                    friend_uid TEXT NOT NULL,
                    doc_id TEXT NOT NULL,
                    mode INTEGER NOT NULL,
                    poly TEXT NOT NULL,
                    min_lat REAL NOT NULL,
                    max_lat REAL NOT NULL,
                    min_lon REAL NOT NULL,
                    max_lon REAL NOT NULL,
                    PRIMARY KEY (friend_uid, doc_id)
                )"""
            )
        }
        if (oldVersion < 3) {
            // Road-snapped shape of each trip: the matched base shape, and the final shape after the user's answers.
            db.execSQL(
                """CREATE TABLE trip_paths (
                    trip_id INTEGER PRIMARY KEY,
                    status INTEGER NOT NULL,
                    base_poly TEXT,
                    poly TEXT,
                    min_lat REAL, max_lat REAL, min_lon REAL, max_lon REAL
                )"""
            )
            // "Which road did you take?" questions for stretches where the trace strays from the matched road.
            db.execSQL(
                """CREATE TABLE road_questions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trip_id INTEGER NOT NULL,
                    base_from INTEGER NOT NULL,
                    base_to INTEGER NOT NULL,
                    options TEXT NOT NULL,
                    chosen INTEGER NOT NULL DEFAULT -1
                )"""
            )
            db.execSQL("CREATE INDEX road_questions_trip ON road_questions(trip_id)")
        }
        if (oldVersion < 4) {
            // State and country code, for counting states, countries and continents on the profile.
            db.execSQL("ALTER TABLE places ADD COLUMN state TEXT")
            db.execSQL("ALTER TABLE places ADD COLUMN country_code TEXT")
        }
        if (oldVersion < 5) {
            // Soft-deleted points, kept so they can be restored. Same columns as points, plus when it was deleted.
            db.execSQL(
                """CREATE TABLE deleted_points (
                    id INTEGER PRIMARY KEY,
                    time INTEGER NOT NULL,
                    lat REAL NOT NULL,
                    lon REAL NOT NULL,
                    accuracy REAL NOT NULL,
                    interval_ms INTEGER NOT NULL,
                    mode INTEGER NOT NULL DEFAULT 0,
                    activity INTEGER NOT NULL DEFAULT -1,
                    deleted_at INTEGER NOT NULL
                )"""
            )
            db.execSQL("CREATE INDEX deleted_points_time ON deleted_points(time)")
        }
        if (oldVersion < 6) {
            // Cache for km explored: squares visited, new ground per local day, and where the walk got to.
            db.execSQL("CREATE TABLE explored_cells (cell INTEGER PRIMARY KEY)")
            db.execSQL("CREATE TABLE explored_days (day TEXT PRIMARY KEY, meters REAL NOT NULL)")
            db.execSQL(
                """CREATE TABLE explored_state (
                    id INTEGER PRIMARY KEY,
                    watermark INTEGER NOT NULL,
                    prev_time INTEGER NOT NULL,
                    prev_lat REAL NOT NULL,
                    prev_lon REAL NOT NULL,
                    first_pass INTEGER,
                    first_pass_m REAL NOT NULL
                )"""
            )
        }
        if (oldVersion < 7) {
            // Elevation of each dot, for 3D routes. Older dots have none.
            db.execSQL("ALTER TABLE points ADD COLUMN altitude REAL")
            db.execSQL("ALTER TABLE deleted_points ADD COLUMN altitude REAL")
        }
        if (oldVersion < 8) {
            // Battery samples taken while recording, to show how much each recording interval uses.
            db.execSQL(
                """CREATE TABLE battery_log (
                    time INTEGER PRIMARY KEY,
                    level REAL NOT NULL,
                    charge_uah INTEGER NOT NULL,
                    charging INTEGER NOT NULL,
                    screen_on INTEGER NOT NULL,
                    interval_ms INTEGER NOT NULL
                )"""
            )
        }
        if (oldVersion < 9) {
            // Places where trips were recorded at two clearly different heights, and whether they really were
            // different levels (a bridge, another floor) or the same one. See HeightGrid.
            db.execSQL(
                """CREATE TABLE height_levels (
                    area INTEGER PRIMARY KEY,
                    lat REAL NOT NULL,
                    lon REAL NOT NULL,
                    low REAL NOT NULL,
                    high REAL NOT NULL,
                    decision INTEGER NOT NULL DEFAULT 0
                )"""
            )
        }
        if (oldVersion < 10) {
            // Which satellites each recorded dot was fixed with, counted per day (Insights › Satellites), and how
            // many dots a day had satellite information at all.
            db.execSQL(
                """CREATE TABLE satellite_use (
                    day INTEGER NOT NULL,
                    constellation INTEGER NOT NULL,
                    svid INTEGER NOT NULL,
                    fixes INTEGER NOT NULL,
                    PRIMARY KEY (day, constellation, svid)
                )"""
            )
            db.execSQL("CREATE TABLE satellite_fixes (day INTEGER PRIMARY KEY, fixes INTEGER NOT NULL)")
        }
    }

    private fun changed() = _changes.update { it + 1 }

    // ---- Points ----

    /** Returns false if a point with the same timestamp already exists. */
    fun insert(p: LocationPoint): Boolean {
        val id = writableDatabase.insertWithOnConflict("points", null, p.toValues(), SQLiteDatabase.CONFLICT_IGNORE)
        if (id != -1L) changed()
        return id != -1L
    }

    /**
     * Inserts many points in one transaction, skipping timestamps already present and points the user has
     * deleted (so restoring an older backup doesn't bring them back). Returns how many were new.
     */
    fun insertAll(points: List<LocationPoint>): Int {
        if (points.isEmpty()) return 0
        val db = writableDatabase
        val deleted = deletedTimes(points.minOf { it.time }, points.maxOf { it.time } + 1)
        var added = 0
        db.beginTransaction()
        try {
            for (p in points) {
                if (p.time in deleted) continue
                if (db.insertWithOnConflict("points", null, p.toValues(), SQLiteDatabase.CONFLICT_IGNORE) != -1L) added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (added > 0) {
            // Restored points can be older than what km explored has already counted.
            invalidateExplored(db)
            changed()
        }
        return added
    }

    private fun LocationPoint.toValues() = ContentValues().apply {
        put("time", time)
        put("lat", lat)
        put("lon", lon)
        put("accuracy", accuracy)
        put("interval_ms", intervalMs)
        put("activity", activity)
        put("mode", mode)
        if (altitude == null) putNull("altitude") else put("altitude", altitude)
    }

    fun deleteAll() {
        val db = writableDatabase
        db.delete("points", null, null)
        db.delete("trips", null, null)
        db.delete("trip_paths", null, null)
        db.delete("road_questions", null, null)
        db.delete("deleted_points", null, null)
        db.delete("satellite_use", null, null)
        db.delete("satellite_fixes", null, null)
        invalidateExplored(db)
        changed()
    }

    fun stats(): Stats {
        val total = long("SELECT COUNT(*) FROM points")
        val today = long("SELECT COUNT(*) FROM points WHERE time >= ${startOfToday()}")
        return Stats(total, today, latest())
    }

    fun latest(): LocationPoint? =
        readableDatabase.rawQuery("SELECT $POINT_COLS FROM points ORDER BY time DESC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) c.toPoint() else null
        }

    fun countIn(box: GeoBox, modes: Set<TravelMode>): Int =
        long("SELECT COUNT(*) FROM points WHERE ${where(box)}${modeClause(modes)}").toInt()

    /** Dots with heights in [box], split into runs at gaps and at changes of trip, with each run's trip id. */
    fun elevationRunsIn(box: GeoBox, modes: Set<TravelMode>, limit: Int = 60_000): List<ElevationRun> {
        val runs = ArrayList<ElevationRun>()
        var current = ArrayList<ElevationPoint>()
        var currentTrip = 0L
        var startTime = 0L
        var prevTime = 0L
        var prevLat = 0.0
        var prevLon = 0.0
        readableDatabase.rawQuery(
            "SELECT time, lat, lon, interval_ms, altitude, trip_id FROM points WHERE ${where(box)}${modeClause(modes)} " +
                "AND altitude IS NOT NULL ORDER BY time LIMIT $limit",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val time = c.getLong(0)
                val lat = c.getDouble(1)
                val lon = c.getDouble(2)
                val trip = c.getLong(5)
                if (current.isNotEmpty() && (trip != currentTrip || TrackRules.isBreak(prevTime, prevLat, prevLon, time, lat, lon, c.getInt(3)))) {
                    if (current.size >= 2) runs += ElevationRun(currentTrip, HeightGrid.passOf(currentTrip, startTime), current)
                    current = ArrayList()
                }
                if (current.isEmpty()) startTime = time
                currentTrip = trip
                current += ElevationPoint(lat, lon, c.getDouble(4))
                prevTime = time
                prevLat = lat
                prevLon = lon
            }
        }
        if (current.size >= 2) runs += ElevationRun(currentTrip, HeightGrid.passOf(currentTrip, startTime), current)
        return runs
    }

    /** The day (yyyy-MM-dd, local) a time falls on. */
    private fun dayOf(time: Long): String = java.time.Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** How many of these times already have a dot on this phone. */
    fun countExistingTimes(times: List<Long>): Int =
        times.chunked(500).sumOf { chunk -> long("SELECT COUNT(*) FROM points WHERE time IN (${chunk.joinToString(",")})").toInt() }

    /** Local days (yyyy-MM-dd) that have deleted dots. */
    fun daysWithDeleted(): List<String> =
        readableDatabase.rawQuery("SELECT DISTINCT date(time / 1000, 'unixepoch', 'localtime') FROM deleted_points", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    /** Deleted dots in the period with everything about them, for the backup (so they stay deleted, and restorable). */
    fun deletedForBackup(from: Long, to: Long): List<LocationPoint> =
        readableDatabase.rawQuery(
            "SELECT $POINT_COLS FROM deleted_points WHERE time >= $from AND time < $to ORDER BY time", null
        ).use { c -> buildList { while (c.moveToNext()) add(c.toPoint()) } }

    /** Brings back the list of deleted dots from a backup; ones already here, deleted or not, are skipped. */
    fun restoreDeleted(points: List<LocationPoint>) {
        if (points.isEmpty()) return
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for (p in points) {
                val exists = long("SELECT (SELECT COUNT(*) FROM points WHERE time = ${p.time}) + (SELECT COUNT(*) FROM deleted_points WHERE time = ${p.time})")
                if (exists > 0) continue
                db.insert("deleted_points", null, p.toValues().apply { put("deleted_at", now) })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Trips that start in the period, with their road shapes and answers, for the backup. */
    fun tripBackups(from: Long, to: Long): List<TripBackup> =
        trips("WHERE start_time >= $from AND start_time < $to ORDER BY start_time").map { t ->
            val path = tripPathRow(t.id)
            val questions = roadQuestionsForTrip(t.id).takeIf { it.isNotEmpty() }?.let { qs ->
                org.json.JSONArray().apply {
                    qs.forEach { q ->
                        put(org.json.JSONObject().put("from", q.baseFrom).put("to", q.baseTo).put("options", RoadOption.encodeList(q.options)).put("chosen", q.chosen))
                    }
                }.toString()
            }
            TripBackup(
                start = t.startTime,
                end = t.endTime,
                mode = t.mode.id,
                inferredMode = t.inferredMode.id,
                confidence = t.confidence.toDouble(),
                needsReview = t.needsReview,
                pathStatus = path?.status,
                basePoly = path?.basePoly,
                poly = path?.poly,
                questions = questions,
                remoteId = t.remoteId,
            )
        }

    /**
     * Brings back trips from a backup, after their dots are restored: each one takes the restored dots in its
     * time range that aren't in a trip yet, keeps its travel mode and whether it still needs an answer, and
     * gets its road shape and road questions back so it isn't matched to roads again. Trips that already exist
     * here are left alone. Returns how many were brought back.
     */
    fun restoreTrips(backups: List<TripBackup>): Int {
        val db = writableDatabase
        var restored = 0
        db.beginTransaction()
        try {
            for (b in backups.sortedBy { it.start }) {
                if (b.end <= b.start) continue
                val free = long("SELECT COUNT(*) FROM points WHERE time >= ${b.start} AND time <= ${b.end} AND trip_id <= 0")
                val taken = long("SELECT COUNT(*) FROM points WHERE time >= ${b.start} AND time <= ${b.end} AND trip_id > 0")
                if (free < 2 || taken > 0) continue
                val id = insertTripRow(db, TravelMode.fromId(b.mode), TravelMode.fromId(b.inferredMode), b.confidence.toFloat(), b.needsReview)
                db.execSQL("UPDATE points SET trip_id = $id, mode = ${b.mode} WHERE time >= ${b.start} AND time <= ${b.end} AND trip_id <= 0")
                refreshTrip(db, id, mutableListOf())
                b.remoteId?.let { db.execSQL("UPDATE trips SET remote_id = ? WHERE id = ?", arrayOf<Any>(it, id)) }
                // refreshTrip clears road data; put the backed-up shape and answers back.
                if (b.pathStatus != null) {
                    db.insertWithOnConflict("trip_paths", null, ContentValues().apply {
                        put("trip_id", id)
                        put("status", b.pathStatus)
                        put("base_poly", b.basePoly)
                        put("poly", b.poly)
                        b.poly?.let { poly ->
                            val pts = Polyline.decode(poly)
                            if (pts.size >= 2) {
                                put("min_lat", (pts.indices step 2).minOf { pts[it] }); put("max_lat", (pts.indices step 2).maxOf { pts[it] })
                                put("min_lon", (1 until pts.size step 2).minOf { pts[it] }); put("max_lon", (1 until pts.size step 2).maxOf { pts[it] })
                            }
                        }
                    }, SQLiteDatabase.CONFLICT_REPLACE)
                }
                b.questions?.let { json ->
                    runCatching {
                        val a = org.json.JSONArray(json)
                        for (i in 0 until a.length()) {
                            val q = a.getJSONObject(i)
                            db.insert("road_questions", null, ContentValues().apply {
                                put("trip_id", id)
                                put("base_from", q.getInt("from"))
                                put("base_to", q.getInt("to"))
                                put("options", q.getString("options"))
                                put("chosen", q.optInt("chosen", -1))
                            })
                        }
                    }
                }
                restored++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (restored > 0) changed()
        return restored
    }

    fun levelBackups(): List<LevelBackup> =
        readableDatabase.rawQuery("SELECT area, lat, lon, low, high, decision FROM height_levels", null).use { c ->
            buildList { while (c.moveToNext()) add(LevelBackup(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getInt(5))) }
        }

    /** Brings back height answers from a backup; answers already given on this phone win. */
    fun restoreLevels(levels: List<LevelBackup>) {
        if (levels.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (l in levels) {
                db.insertWithOnConflict("height_levels", null, ContentValues().apply {
                    put("area", l.area); put("lat", l.lat); put("lon", l.lon); put("low", l.low); put("high", l.high); put("decision", l.decision)
                }, SQLiteDatabase.CONFLICT_IGNORE)
                if (l.decision != HeightGrid.ASK) {
                    db.execSQL("UPDATE height_levels SET decision = ${l.decision} WHERE area = ${l.area} AND decision = ${HeightGrid.ASK}")
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    /** How many dots each local day has (for estimating backup size). */
    fun pointsPerDay(): List<Int> =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM points GROUP BY date(time / 1000, 'unixepoch', 'localtime')", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getInt(0)) }
        }

    /** How many points each trip's shared route has (its road-snapped shape, or its dots). */
    fun routePointCounts(): List<Int> {
        val trips = readableDatabase.rawQuery("SELECT id FROM trips", null).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
        return trips.map { tripDisplayPath(it).size / 2 }
    }

    /** Every dot with a height in [box], as samples for a [HeightGrid]. */
    fun heightSamples(box: GeoBox, limit: Int = 250_000): List<HeightGrid.Sample> =
        readableDatabase.rawQuery(
            "SELECT lat, lon, trip_id, time, altitude FROM points WHERE ${where(box)} AND altitude IS NOT NULL LIMIT $limit", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(HeightGrid.Sample(c.getDouble(0), c.getDouble(1), HeightGrid.passOf(c.getLong(2), c.getLong(3)), c.getDouble(4)))
                }
            }
        }

    /** The user's answers about levels ([HeightGrid.ASK] while not answered yet), by [Cells] key. */
    fun levelDecisions(): Map<Long, Int> =
        readableDatabase.rawQuery("SELECT area, decision FROM height_levels", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) }
        }

    /** Adds level questions for these places (ones already known are left alone). */
    fun addLevelQuestions(candidates: List<HeightGrid.LevelCandidate>) {
        if (candidates.isEmpty()) return
        val db = writableDatabase
        var added = 0
        for (q in candidates) {
            val id = db.insertWithOnConflict("height_levels", null, ContentValues().apply {
                put("area", q.area)
                put("lat", q.lat)
                put("lon", q.lon)
                put("low", q.low)
                put("high", q.high)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            if (id != -1L) added++
        }
        if (added > 0) changed()
    }

    fun openLevelQuestions(): List<LevelQuestion> =
        readableDatabase.rawQuery("SELECT area, lat, lon, low, high FROM height_levels WHERE decision = ${HeightGrid.ASK} ORDER BY high - low DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(LevelQuestion(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getDouble(4))) }
        }

    /** Records whether a place really has different levels ([HeightGrid.DIFFERENT]) or one ([HeightGrid.SAME]). */
    fun answerLevel(area: Long, decision: Int) {
        writableDatabase.execSQL("UPDATE height_levels SET decision = $decision WHERE area = $area")
        changed()
    }

    /** All of a trip's dots that have heights, in order. */
    fun tripElevations(tripId: Long): List<ElevationPoint> =
        readableDatabase.rawQuery("SELECT lat, lon, altitude FROM points WHERE trip_id = $tripId AND altitude IS NOT NULL ORDER BY time", null).use { c ->
            buildList { while (c.moveToNext()) add(ElevationPoint(c.getDouble(0), c.getDouble(1), c.getDouble(2))) }
        }

    @Volatile private var baselineCache: Pair<Long, Double>? = null

    /**
     * A fixed height that 3D routes are measured up from: a low point (5th percentile, so the odd bad GPS
     * reading doesn't count) of every height recorded. Worked out again at most every 10 minutes.
     */
    fun altitudeBaseline(): Double {
        val now = System.currentTimeMillis()
        baselineCache?.let { (at, value) -> if (now - at < 10 * 60_000L) return value }
        val count = long("SELECT COUNT(*) FROM points WHERE altitude IS NOT NULL")
        val value = if (count == 0L) 0.0 else readableDatabase.rawQuery(
            "SELECT altitude FROM points WHERE altitude IS NOT NULL ORDER BY altitude LIMIT 1 OFFSET ${count / 20}", null
        ).use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }
        baselineCache = now to value
        return value
    }

    /** Points in time order; with [skipSnapped], leaves out trips that have a road-snapped shape. */
    fun tracksIn(box: GeoBox, modes: Set<TravelMode>, skipSnapped: Boolean = false): TrackData =
        readableDatabase.rawQuery(
            "SELECT time, lat, lon, interval_ms FROM points WHERE ${where(box)}${modeClause(modes)}" +
                (if (skipSnapped) " AND trip_id NOT IN (SELECT trip_id FROM trip_paths WHERE poly IS NOT NULL)" else "") +
                " ORDER BY time",
            null,
        ).use { c ->
            val n = c.count
            val time = LongArray(n)
            val lat = DoubleArray(n)
            val lon = DoubleArray(n)
            val interval = IntArray(n)
            var i = 0
            while (i < n && c.moveToNext()) {
                time[i] = c.getLong(0)
                lat[i] = c.getDouble(1)
                lon[i] = c.getDouble(2)
                interval[i] = c.getInt(3)
                i++
            }
            TrackData(i, time, lat, lon, interval)
        }

    /** Counts points per cell, where cell (0, 0) is the south-west corner of [box]. */
    fun gridIn(box: GeoBox, cellLat: Double, cellLon: Double, modes: Set<TravelMode>): GridData =
        readableDatabase.rawQuery(
            """SELECT CAST((lat - ${box.south}) / $cellLat AS INTEGER) AS r,
                      CAST((lon - ${box.west}) / $cellLon AS INTEGER) AS c,
                      COUNT(*)
               FROM points WHERE ${where(box)}${modeClause(modes)} GROUP BY r, c""",
            null,
        ).use { c ->
            val n = c.count
            val row = IntArray(n)
            val col = IntArray(n)
            val count = IntArray(n)
            var i = 0
            while (i < n && c.moveToNext()) {
                row[i] = c.getInt(0)
                col[i] = c.getInt(1)
                count[i] = c.getInt(2)
                i++
            }
            GridData(i, row, col, count)
        }

    /** Streams points in time order, optionally only those recorded after [after]. */
    fun forEachPoint(after: Long = Long.MIN_VALUE, block: (LocationPoint) -> Unit) {
        readableDatabase.rawQuery(
            "SELECT $POINT_COLS FROM points WHERE time > $after ORDER BY time", null
        ).use { c -> while (c.moveToNext()) block(c.toPoint()) }
    }

    /** Local calendar days ("yyyy-MM-dd") with points recorded after [after]. */
    fun daysWithPointsAfter(after: Long): List<String> =
        readableDatabase.rawQuery(
            "SELECT DISTINCT date(time / 1000, 'unixepoch', 'localtime') d FROM points WHERE time > $after ORDER BY d", null
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun pointsBetween(from: Long, to: Long): List<LocationPoint> =
        readableDatabase.rawQuery(
            "SELECT $POINT_COLS FROM points WHERE time >= $from AND time < $to ORDER BY time", null
        ).use { c -> buildList { while (c.moveToNext()) add(c.toPoint()) } }

    /** Points in the range with their row ids, for editing. */
    fun editablePoints(from: Long, to: Long): List<EditablePoint> =
        readableDatabase.rawQuery(
            "SELECT id, time, lat, lon, accuracy FROM points WHERE time >= $from AND time < $to ORDER BY time", null
        ).use { c ->
            buildList { while (c.moveToNext()) add(EditablePoint(c.getLong(0), c.getLong(1), c.getDouble(2), c.getDouble(3), c.getFloat(4))) }
        }

    /**
     * The recorded line nearest to a spot on the map, within [maxM] metres: the time of the nearer dot and
     * its trip id (-1 for dots outside any trip), or null if nothing is that close. Lines between dots count,
     * so a long-press on the drawn route finds it even between sparse dots.
     */
    fun sectionNear(from: Long, to: Long, lat: Double, lon: Double, maxM: Double): Pair<Long, Long>? {
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat))
        var best = maxM
        var found: Pair<Long, Long>? = null
        readableDatabase.rawQuery(
            "SELECT time, lat, lon, trip_id FROM points WHERE time >= $from AND time < $to ORDER BY time", null
        ).use { c ->
            var pt = 0L
            var px = 0.0
            var py = 0.0
            var pTrip = 0L
            var first = true
            while (c.moveToNext()) {
                val t = c.getLong(0)
                val x = (c.getDouble(2) - lon) * mPerDegLon
                val y = (c.getDouble(1) - lat) * mPerDegLat
                val trip = c.getLong(3)
                val dHere = hypot(x, y)
                if (dHere < best) {
                    best = dHere
                    found = t to trip
                }
                if (!first && trip == pTrip) {
                    // Distance from the spot (the origin) to the line between this dot and the one before.
                    val dx = x - px
                    val dy = y - py
                    val len2 = dx * dx + dy * dy
                    if (len2 > 0) {
                        val k = (-(px * dx + py * dy) / len2).coerceIn(0.0, 1.0)
                        val d = hypot(px + k * dx, py + k * dy)
                        if (d < best) {
                            best = d
                            found = (if (k < 0.5) pt else t) to trip
                        }
                    }
                }
                pt = t
                px = x
                py = y
                pTrip = trip
                first = false
            }
        }
        // The drawn line can be a road-matched shape that strays from the dots (a long straight jump, say), so a
        // long-press on it counts too.
        readableDatabase.rawQuery(
            """SELECT t.id, t.start_time, p.poly FROM trip_paths p JOIN trips t ON t.id = p.trip_id
               WHERE p.poly IS NOT NULL AND t.start_time < $to AND t.end_time >= $from""",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val pts = Polyline.decode(c.getString(2))
                var i = 0
                while (i + 3 < pts.size) {
                    val ax = (pts[i + 1] - lon) * mPerDegLon
                    val ay = (pts[i] - lat) * mPerDegLat
                    val dx = (pts[i + 3] - lon) * mPerDegLon - ax
                    val dy = (pts[i + 2] - lat) * mPerDegLat - ay
                    val len2 = dx * dx + dy * dy
                    val k = if (len2 > 0) (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0) else 0.0
                    val d = hypot(ax + k * dx, ay + k * dy)
                    if (d < best) {
                        best = d
                        found = c.getLong(1) to c.getLong(0)
                    }
                    i += 2
                }
            }
        }
        return found
    }

    /** Deleted points in the range with their row ids, so they can be restored. */
    fun deletedPoints(from: Long, to: Long): List<EditablePoint> =
        readableDatabase.rawQuery(
            "SELECT id, time, lat, lon, accuracy FROM deleted_points WHERE time >= $from AND time < $to ORDER BY time", null
        ).use { c ->
            buildList { while (c.moveToNext()) add(EditablePoint(c.getLong(0), c.getLong(1), c.getDouble(2), c.getDouble(3), c.getFloat(4))) }
        }

    private fun deletedTimes(from: Long, to: Long): Set<Long> =
        readableDatabase.rawQuery("SELECT time FROM deleted_points WHERE time >= $from AND time < $to", null)
            .use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }

    /**
     * GPS blips between [from] and [to]: one to three dots that jump well away (over 300 m) and come straight back
     * to almost where they left (within a quarter of that, and within 30 minutes), when they're coarse network
     * fixes (worse than 20 m) or would need an impossible speed. Drawn, they make long straight lines to nowhere.
     */
    fun findSpikes(from: Long, to: Long): List<Long> {
        class P(val id: Long, val time: Long, val lat: Double, val lon: Double, val acc: Float)
        val pts = readableDatabase.rawQuery(
            "SELECT id, time, lat, lon, accuracy FROM points WHERE time >= $from AND time <= $to ORDER BY time", null
        ).use { c -> buildList { while (c.moveToNext()) add(P(c.getLong(0), c.getLong(1), c.getDouble(2), c.getDouble(3), c.getFloat(4))) } }
        val spikes = mutableListOf<Long>()
        fun d(a: P, b: P) = TrackRules.distanceMeters(a.lat, a.lon, b.lat, b.lon)
        var i = 1
        while (i < pts.size - 1) {
            var found = 0
            for (k in 1..SPIKE_MAX_DOTS) {
                if (i + k >= pts.size) break
                val prev = pts[i - 1]
                val next = pts[i + k]
                if (next.time - prev.time > SPIKE_WINDOW_MS) break
                val run = pts.subList(i, i + k)
                val away = run.minOf { minOf(d(prev, it), d(it, next)) }
                if (away <= SPIKE_MIN_M || d(prev, next) >= away * 0.25) continue
                val first = run.first()
                val seconds = (first.time - prev.time).coerceAtLeast(1) / 1000.0
                val impossible = d(prev, first) / seconds > SPIKE_IMPOSSIBLE_MPS
                if (run.any { it.acc > SPIKE_COARSE_M } || impossible) {
                    found = k
                    break
                }
            }
            if (found > 0) {
                for (j in i until i + found) spikes += pts[j].id
                i += found
            } else {
                i++
            }
        }
        return spikes
    }

    /**
     * Soft-deletes points: they move to the deleted_points table, out of the map, stats and backups, and can
     * be brought back with [restorePoints]. Trips they belonged to are dissolved so the trip detector
     * rebuilds them from what's left (points keep their travel mode, so answers aren't asked again).
     * Returns the edited days and the remote ids of shared routes that no longer exist.
     */
    fun deletePoints(ids: Collection<Long>): DeleteResult {
        if (ids.isEmpty()) return DeleteResult(emptySet(), emptyList())
        val db = writableDatabase
        val days = HashSet<String>()
        val remoteIds = mutableListOf<String>()
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for (chunk in ids.chunked(500)) {
                val list = chunk.joinToString(",")
                db.rawQuery("SELECT DISTINCT date(time / 1000, 'unixepoch', 'localtime') FROM points WHERE id IN ($list)", null)
                    .use { c -> while (c.moveToNext()) days += c.getString(0) }
                val tripIds = db.rawQuery("SELECT DISTINCT trip_id FROM points WHERE id IN ($list) AND trip_id > 0", null)
                    .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
                dissolveTrips(db, tripIds, remoteIds)
                db.execSQL(
                    """INSERT OR REPLACE INTO deleted_points (id, time, lat, lon, accuracy, interval_ms, mode, activity, altitude, deleted_at)
                       SELECT id, time, lat, lon, accuracy, interval_ms, mode, activity, altitude, $now FROM points WHERE id IN ($list)"""
                )
                db.execSQL("DELETE FROM points WHERE id IN ($list)")
            }
            invalidateExplored(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return DeleteResult(days, remoteIds)
    }

    /**
     * Brings soft-deleted points back. Trips around them are dissolved so the trip detector rebuilds them with
     * the restored points included. Returns the edited days and the remote ids of shared routes replaced.
     */
    fun restorePoints(ids: Collection<Long>): DeleteResult {
        if (ids.isEmpty()) return DeleteResult(emptySet(), emptyList())
        val db = writableDatabase
        val days = HashSet<String>()
        val remoteIds = mutableListOf<String>()
        db.beginTransaction()
        try {
            for (chunk in ids.chunked(500)) {
                val list = chunk.joinToString(",")
                db.rawQuery("SELECT DISTINCT date(time / 1000, 'unixepoch', 'localtime') FROM deleted_points WHERE id IN ($list)", null)
                    .use { c -> while (c.moveToNext()) days += c.getString(0) }
                val span = db.rawQuery("SELECT MIN(time), MAX(time) FROM deleted_points WHERE id IN ($list)", null)
                    .use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) to c.getLong(1) else null } ?: continue
                // Any trip the restored points could belong to, with some slack for the gap rules.
                val slack = 30 * 60_000L
                val tripIds = db.rawQuery(
                    "SELECT id FROM trips WHERE start_time <= ${span.second + slack} AND end_time >= ${span.first - slack}", null
                ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
                dissolveTrips(db, tripIds, remoteIds)
                db.execSQL(
                    """INSERT OR IGNORE INTO points (id, time, lat, lon, accuracy, interval_ms, mode, activity, altitude, trip_id)
                       SELECT id, time, lat, lon, accuracy, interval_ms, mode, activity, altitude, 0 FROM deleted_points WHERE id IN ($list)"""
                )
                db.execSQL("DELETE FROM deleted_points WHERE id IN ($list)")
            }
            invalidateExplored(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return DeleteResult(days, remoteIds)
    }

    /**
     * Moves a dot to where it should have been (a ferry crossing that GPS drew over land, say). Its trip keeps
     * the dot, with times and distance worked out again and its road match redone. A hand-placed dot counts as
     * accurate unless [accuracy] says otherwise (undo passes the old one back).
     */
    fun movePoint(id: Long, lat: Double, lon: Double, accuracy: Float? = null): DeleteResult {
        val db = writableDatabase
        val days = HashSet<String>()
        val remoteIds = mutableListOf<String>()
        db.beginTransaction()
        try {
            val row = db.rawQuery("SELECT time, trip_id, accuracy FROM points WHERE id = $id", null).use { c ->
                if (c.moveToFirst()) Triple(c.getLong(0), c.getLong(1), c.getFloat(2)) else null
            } ?: return DeleteResult(emptySet(), emptyList())
            days += dayOf(row.first)
            val acc = accuracy ?: minOf(row.third, HAND_PLACED_ACCURACY_M)
            db.execSQL("UPDATE points SET lat = ?, lon = ?, accuracy = ? WHERE id = ?", arrayOf<Any>(lat, lon, acc, id))
            if (row.second > 0) refreshTrip(db, row.second, remoteIds)
            invalidateExplored(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return DeleteResult(days, remoteIds)
    }

    /**
     * Adds a dot by hand halfway in time between dot [afterId] and the next one, at [lat], [lon], in the same
     * trip, to trace a route the recording missed. Returns the new dot's id, or null if there's no next dot
     * (or no time left between the two).
     */
    fun addPointAfter(afterId: Long, lat: Double, lon: Double): Pair<Long, DeleteResult>? {
        val db = writableDatabase
        val remoteIds = mutableListOf<String>()
        var id = -1L
        var time = 0L
        db.beginTransaction()
        try {
            val before = db.rawQuery("SELECT time, trip_id, mode, activity, interval_ms FROM points WHERE id = $afterId", null).use { c ->
                if (c.moveToFirst()) longArrayOf(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4)) else null
            } ?: return null
            val next = db.rawQuery("SELECT MIN(time) FROM points WHERE time > ${before[0]}", null).use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            } ?: return null
            if (next - before[0] < 2) return null
            time = (before[0] + next) / 2
            val values = ContentValues().apply {
                put("time", time)
                put("lat", lat)
                put("lon", lon)
                put("accuracy", HAND_PLACED_ACCURACY_M)
                put("interval_ms", before[4])
                put("mode", before[2])
                put("activity", before[3])
                put("trip_id", before[1])
            }
            id = db.insertWithOnConflict("points", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            if (id == -1L) return null
            if (before[1] > 0) refreshTrip(db, before[1], remoteIds)
            invalidateExplored(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return id to DeleteResult(setOf(dayOf(time)), remoteIds)
    }

    /** Removes a dot added by hand for good (undoing [addPointAfter]); recorded dots go through [deletePoints]. */
    fun removeAddedPoint(id: Long): DeleteResult {
        val db = writableDatabase
        val remoteIds = mutableListOf<String>()
        var day = ""
        db.beginTransaction()
        try {
            val row = db.rawQuery("SELECT time, trip_id FROM points WHERE id = $id", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) to c.getLong(1) else null
            } ?: return DeleteResult(emptySet(), emptyList())
            day = dayOf(row.first)
            db.execSQL("DELETE FROM points WHERE id = $id")
            if (row.second > 0) refreshTrip(db, row.second, remoteIds)
            invalidateExplored(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return DeleteResult(setOf(day), remoteIds)
    }

    /** Removes trips (and their road data) and hands their points back to the trip detector. */
    private fun dissolveTrips(db: SQLiteDatabase, tripIds: List<Long>, remoteIds: MutableList<String>) {
        if (tripIds.isEmpty()) return
        val trips = tripIds.joinToString(",")
        db.rawQuery("SELECT remote_id FROM trips WHERE id IN ($trips) AND remote_id IS NOT NULL", null)
            .use { c -> while (c.moveToNext()) remoteIds += c.getString(0) }
        db.execSQL("DELETE FROM trips WHERE id IN ($trips)")
        db.execSQL("DELETE FROM trip_paths WHERE trip_id IN ($trips)")
        db.execSQL("DELETE FROM road_questions WHERE trip_id IN ($trips)")
        db.execSQL("UPDATE points SET trip_id = 0 WHERE trip_id IN ($trips)")
    }

    // ---- Battery ----

    /** Records a battery sample taken while recording; samples older than 30 days are dropped. */
    /** Bumped whenever satellite counts change, so Insights can refresh just that card. */
    private val _satelliteChanges = MutableStateFlow(0)
    val satelliteChanges: StateFlow<Int> = _satelliteChanges

    /** Counts [used] satellites (as [Satellites.key]s) towards the dot recorded at [time]. */
    fun logSatellites(time: Long, used: Collection<Int>) {
        if (used.isEmpty()) return
        val day = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val db = writableDatabase
        db.beginTransaction()
        try {
            // No UPSERT before Android 11's SQLite, so update first and insert when nothing was there.
            val bump = db.compileStatement("UPDATE satellite_use SET fixes = fixes + 1 WHERE day = ? AND constellation = ? AND svid = ?")
            val add = db.compileStatement("INSERT INTO satellite_use (day, constellation, svid, fixes) VALUES (?, ?, ?, 1)")
            for (k in used) {
                val c = (k / 1000).toLong()
                val id = (k % 1000).toLong()
                bump.clearBindings()
                bump.bindLong(1, day)
                bump.bindLong(2, c)
                bump.bindLong(3, id)
                if (bump.executeUpdateDelete() == 0) {
                    add.clearBindings()
                    add.bindLong(1, day)
                    add.bindLong(2, c)
                    add.bindLong(3, id)
                    add.executeInsert()
                }
            }
            db.execSQL("UPDATE satellite_fixes SET fixes = fixes + 1 WHERE day = $day")
            db.execSQL("INSERT OR IGNORE INTO satellite_fixes (day, fixes) VALUES ($day, 1)")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _satelliteChanges.update { it + 1 }
    }

    /** One day's satellite counts for the backup ([day] as an epoch day): (system, ID, uses) and the day's fixes. */
    fun satelliteDay(day: Long): Pair<List<SatelliteUse>, Long> {
        val list = readableDatabase.rawQuery(
            "SELECT constellation, svid, fixes FROM satellite_use WHERE day = $day", null,
        ).use { c -> buildList { while (c.moveToNext()) add(SatelliteUse(c.getInt(0), c.getInt(1), c.getLong(2))) } }
        return list to long("SELECT COALESCE(SUM(fixes), 0) FROM satellite_fixes WHERE day = $day")
    }

    /**
     * Puts a backed-up day's satellite counts back. Where this phone already counted that day (recording since
     * the reinstall), the larger count wins, so nothing is counted twice.
     */
    fun restoreSatelliteDay(day: Long, uses: List<SatelliteUse>, fixes: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (u in uses) {
                db.execSQL(
                    "INSERT OR IGNORE INTO satellite_use (day, constellation, svid, fixes) VALUES ($day, ${u.constellation}, ${u.svid}, 0)"
                )
                db.execSQL(
                    "UPDATE satellite_use SET fixes = MAX(fixes, ${u.fixes}) WHERE day = $day AND constellation = ${u.constellation} AND svid = ${u.svid}"
                )
            }
            db.execSQL("INSERT OR IGNORE INTO satellite_fixes (day, fixes) VALUES ($day, 0)")
            db.execSQL("UPDATE satellite_fixes SET fixes = MAX(fixes, $fixes) WHERE day = $day")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _satelliteChanges.update { it + 1 }
        changed()
    }

    /** The satellites used for dots recorded between [from] and [to] (ms), most used first. */
    fun satelliteUse(from: Long, to: Long): SatelliteSummary {
        val zone = java.time.ZoneId.systemDefault()
        val first = java.time.Instant.ofEpochMilli(from.coerceAtLeast(0)).atZone(zone).toLocalDate().toEpochDay()
        val last = java.time.Instant.ofEpochMilli(to).atZone(zone).toLocalDate().toEpochDay()
        val list = readableDatabase.rawQuery(
            "SELECT constellation, svid, SUM(fixes) AS n FROM satellite_use WHERE day BETWEEN $first AND $last " +
                "GROUP BY constellation, svid ORDER BY n DESC",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(SatelliteUse(c.getInt(0), c.getInt(1), c.getLong(2))) } }
        val fixes = long("SELECT COALESCE(SUM(fixes), 0) FROM satellite_fixes WHERE day BETWEEN $first AND $last")
        return SatelliteSummary(list, fixes)
    }

    fun logBattery(time: Long, levelPct: Float, chargeUah: Long, charging: Boolean, screenOn: Boolean, intervalMs: Long) {
        val db = writableDatabase
        db.insertWithOnConflict("battery_log", null, ContentValues().apply {
            put("time", time)
            put("level", levelPct)
            put("charge_uah", chargeUah)
            put("charging", if (charging) 1 else 0)
            put("screen_on", if (screenOn) 1 else 0)
            put("interval_ms", intervalMs)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        db.execSQL("DELETE FROM battery_log WHERE time < ${time - 30 * 86_400_000L}")
    }

    /**
     * Battery used while recording since [from], split by recording interval: for each interval, how many hours
     * were spent recording at it and how many percent of the battery went in that time. Only stretches on
     * battery count (not while charging); with [screenOffOnly], only stretches with the screen off, which
     * leaves out most of what other apps use.
     */
    fun batteryByInterval(from: Long, screenOffOnly: Boolean): List<BatteryUse> {
        class Sample(val time: Long, val level: Float, val uah: Long, val charging: Boolean, val screenOn: Boolean, val intervalMs: Long)
        val samples = readableDatabase.rawQuery(
            "SELECT time, level, charge_uah, charging, screen_on, interval_ms FROM battery_log WHERE time >= $from ORDER BY time", null
        ).use { c ->
            buildList { while (c.moveToNext()) add(Sample(c.getLong(0), c.getFloat(1), c.getLong(2), c.getInt(3) == 1, c.getInt(4) == 1, c.getLong(5))) }
        }
        val hours = HashMap<Int, Double>()
        val used = HashMap<Int, Double>()
        for (k in 1 until samples.size) {
            val a = samples[k - 1]
            val b = samples[k]
            val dt = b.time - a.time
            // Gaps (recording stopped, phone off) and charging don't count.
            if (dt <= 0 || dt > maxOf(15 * 60_000L, a.intervalMs * 5 / 2) || a.charging || b.charging) continue
            if (screenOffOnly && (a.screenOn || b.screenOn)) continue
            // The charge counter is far finer than the whole-percent level, when the phone has one.
            val drop = if (a.uah > 0 && b.uah > 0 && a.level > 0) {
                (a.uah - b.uah) / (a.uah / (a.level / 100.0)) * 100.0
            } else {
                (a.level - b.level).toDouble()
            }
            if (drop < 0) continue
            val sec = ((a.intervalMs + 500) / 1000).toInt().coerceAtLeast(1)
            hours[sec] = (hours[sec] ?: 0.0) + dt / 3_600_000.0
            used[sec] = (used[sec] ?: 0.0) + drop
        }
        return hours.keys.sorted().map { BatteryUse(it, hours.getValue(it), used[it] ?: 0.0) }
    }

    // ---- Trips ----

    /** Points not yet looked at by the trip detector, oldest first. */
    fun pendingPoints(limit: Int = 20_000): List<PendingPoint> =
        readableDatabase.rawQuery(
            "SELECT id, time, lat, lon, interval_ms, activity, mode FROM points WHERE trip_id = 0 ORDER BY time LIMIT $limit",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(PendingPoint(c.getLong(0), c.getLong(1), c.getDouble(2), c.getDouble(3), c.getInt(4), c.getInt(5), c.getInt(6)))
                }
            }
        }

    /** Marks points as processed without a trip (stationary, or too sparse to be a route). */
    fun markNoTrip(ids: List<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.chunked(500).forEach { chunk ->
                db.execSQL("UPDATE points SET trip_id = -1 WHERE id IN (${chunk.joinToString(",")})")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Stores a detected trip and tags its points with it. Returns the new trip id. */
    fun createTrip(trip: Trip, pointIds: List<Long>): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insert("trips", null, ContentValues().apply {
                put("start_time", trip.startTime)
                put("end_time", trip.endTime)
                put("distance_m", trip.distanceM)
                put("mode", trip.mode.id)
                put("inferred_mode", trip.inferredMode.id)
                put("confidence", trip.confidence)
                put("needs_review", if (trip.needsReview) 1 else 0)
                put("start_lat", trip.startLat)
                put("start_lon", trip.startLon)
                put("end_lat", trip.endLat)
                put("end_lon", trip.endLon)
            })
            pointIds.chunked(500).forEach { chunk ->
                db.execSQL("UPDATE points SET trip_id = $id, mode = ${trip.mode.id} WHERE id IN (${chunk.joinToString(",")})")
            }
            db.setTransactionSuccessful()
            // Its day's backup now includes the trip.
            Prefs.addDirtyBackupDays(listOf(dayOf(trip.startTime)))
            return id
        } finally {
            db.endTransaction()
            changed()
        }
    }

    /**
     * Remembers how trips look around [start]..[end] (widened to whole trips and an hour either side, which
     * covers the neighbours an edit can trim or join), so [restore] can undo an edit made there.
     */
    fun snapshot(start: Long, end: Long): EditSnapshot {
        val db = readableDatabase
        var from = start - 3_600_000L
        var to = end + 3_600_000L
        db.rawQuery("SELECT MIN(start_time), MAX(end_time) FROM trips WHERE start_time <= $to AND end_time >= $from", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) {
                from = minOf(from, c.getLong(0))
                to = maxOf(to, c.getLong(1))
            }
        }
        val points = db.rawQuery("SELECT id, trip_id, mode FROM points WHERE time >= $from AND time <= $to", null).use { c ->
            buildList { while (c.moveToNext()) add(longArrayOf(c.getLong(0), c.getLong(1), c.getLong(2))) }
        }
        val ids = tripIdsIn(db, from, to)
        val inIds = ids.joinToString(",").ifEmpty { "-1" }
        return EditSnapshot(
            from, to, points,
            rows(db, "SELECT * FROM trips WHERE id IN ($inIds)"),
            rows(db, "SELECT * FROM trip_paths WHERE trip_id IN ($inIds)"),
            rows(db, "SELECT * FROM road_questions WHERE trip_id IN ($inIds)"),
        )
    }

    /**
     * Puts trips back the way [snap] found them, undoing an edit: trips made since are removed and the old ones
     * come back with their dots, road shapes and answers. Returns the days to back up again and the shared
     * routes of removed trips.
     */
    fun restore(snap: EditSnapshot): DeleteResult {
        val db = writableDatabase
        val days = HashSet<String>()
        val remoteIds = mutableListOf<String>()
        db.beginTransaction()
        try {
            val now = tripIdsIn(db, snap.from, snap.to)
            val old = snap.trips.map { it.getAsLong("id") }.toSet()
            val inNow = now.joinToString(",").ifEmpty { "-1" }
            db.rawQuery("SELECT id, start_time, remote_id FROM trips WHERE id IN ($inNow)", null).use { c ->
                while (c.moveToNext()) {
                    days += dayOf(c.getLong(1))
                    // A trip the edit made goes, and so does its shared route.
                    if (c.getLong(0) !in old && !c.isNull(2)) remoteIds += c.getString(2)
                }
            }
            db.execSQL("DELETE FROM trips WHERE id IN ($inNow)")
            db.execSQL("DELETE FROM trip_paths WHERE trip_id IN ($inNow)")
            db.execSQL("DELETE FROM road_questions WHERE trip_id IN ($inNow)")
            for (row in snap.trips) {
                val values = ContentValues(row)
                // Shared again on the next sync; a route the edit removed is shared afresh.
                values.put("uploaded", 0)
                if (row.getAsLong("id") !in now) values.putNull("remote_id")
                db.insertWithOnConflict("trips", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                days += dayOf(row.getAsLong("start_time"))
            }
            for (row in snap.paths) db.insertWithOnConflict("trip_paths", null, row, SQLiteDatabase.CONFLICT_REPLACE)
            for (row in snap.questions) db.insertWithOnConflict("road_questions", null, row, SQLiteDatabase.CONFLICT_REPLACE)
            val update = db.compileStatement("UPDATE points SET trip_id = ?, mode = ? WHERE id = ?")
            for ((id, trip, mode) in snap.points.map { Triple(it[0], it[1], it[2]) }) {
                update.bindLong(1, trip)
                update.bindLong(2, mode)
                update.bindLong(3, id)
                update.executeUpdateDelete()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return DeleteResult(days, remoteIds)
    }

    /** Trips with dots between [from] and [to], or overlapping that time. */
    private fun tripIdsIn(db: SQLiteDatabase, from: Long, to: Long): Set<Long> {
        val ids = HashSet<Long>()
        db.rawQuery("SELECT id FROM trips WHERE start_time <= $to AND end_time >= $from", null).use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        db.rawQuery("SELECT DISTINCT trip_id FROM points WHERE time >= $from AND time <= $to AND trip_id > 0", null).use { c ->
            while (c.moveToNext()) ids += c.getLong(0)
        }
        return ids
    }

    /** Every column of the rows a query returns, ready to insert again. */
    private fun rows(db: SQLiteDatabase, sql: String): List<ContentValues> = db.rawQuery(sql, null).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(ContentValues().apply {
                    for (i in 0 until c.columnCount) {
                        val name = c.getColumnName(i)
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER -> put(name, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> put(name, c.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> put(name, c.getString(i))
                            Cursor.FIELD_TYPE_BLOB -> put(name, c.getBlob(i))
                            else -> putNull(name)
                        }
                    }
                })
            }
        }
    }

    /** Records the user's answer for how a trip was travelled. */
    fun setTripMode(tripId: Long, mode: TravelMode) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE trips SET mode = ${mode.id}, needs_review = 0, uploaded = 0 WHERE id = $tripId")
            db.execSQL("UPDATE points SET mode = ${mode.id} WHERE trip_id = $tripId")
            // Re-match to roads with the right kind of network (footpaths, cycle routes, roads or bus routes).
            db.execSQL("DELETE FROM trip_paths WHERE trip_id = $tripId")
            db.execSQL("DELETE FROM road_questions WHERE trip_id = $tripId")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    /**
     * Changes when a trip started and ended, as picked on the Timeline. Dots inside the new times join the trip
     * (taken from neighbouring trips or stays); dots left outside go to the trip right before or after it when
     * there was no stop in between, or otherwise become part of the stay. Neighbouring trips are trimmed to
     * match, and removed if nothing is left of them. Returns the edited days and the remote ids of shared
     * routes that no longer exist.
     */
    fun retimeTrip(tripId: Long, newStart: Long, newEnd: Long): DeleteResult {
        val trip = trip(tripId) ?: return DeleteResult(emptySet(), emptyList())
        if (newEnd <= newStart) return DeleteResult(emptySet(), emptyList())
        val db = writableDatabase
        val days = HashSet<String>()
        val remoteIds = mutableListOf<String>()
        val touched = linkedSetOf(tripId)
        db.beginTransaction()
        try {
            val lo = minOf(newStart, trip.startTime)
            val hi = maxOf(newEnd, trip.endTime)
            db.rawQuery("SELECT DISTINCT date(time / 1000, 'unixepoch', 'localtime') FROM points WHERE time >= $lo AND time <= $hi", null)
                .use { c -> while (c.moveToNext()) days += c.getString(0) }

            // Neighbours it can hand dots back to: only when the trips run straight into each other.
            val before = trips("WHERE id != $tripId AND end_time <= ${trip.startTime} ORDER BY end_time DESC LIMIT 1").firstOrNull()
                ?.takeIf { trip.startTime - it.endTime < ADJACENT_MS }
            val after = trips("WHERE id != $tripId AND start_time >= ${trip.endTime} ORDER BY start_time LIMIT 1").firstOrNull()
                ?.takeIf { it.startTime - trip.endTime < ADJACENT_MS }

            // Dots inside the new times join the trip.
            db.rawQuery("SELECT DISTINCT trip_id FROM points WHERE time >= $newStart AND time <= $newEnd AND trip_id > 0 AND trip_id != $tripId", null)
                .use { c -> while (c.moveToNext()) touched += c.getLong(0) }
            db.execSQL("UPDATE points SET trip_id = $tripId, mode = ${trip.mode.id} WHERE time >= $newStart AND time <= $newEnd")

            // Dots left outside go to the neighbour they now touch, or to the stay.
            fun release(clause: String, to: Trip?) {
                if (to != null) {
                    touched += to.id
                    db.execSQL("UPDATE points SET trip_id = ${to.id}, mode = ${to.mode.id} WHERE trip_id = $tripId AND $clause")
                } else {
                    db.execSQL("UPDATE points SET trip_id = -1, mode = ${TravelMode.UNKNOWN.id} WHERE trip_id = $tripId AND $clause")
                }
            }
            release("time < $newStart", before)
            release("time > $newEnd", after)

            for (id in touched) refreshTrip(db, id, remoteIds)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return DeleteResult(days, remoteIds)
    }

    /**
     * Makes the dots from [start] to [end] (inclusive) a trip of their own, travelled by [mode], as picked on the
     * Timeline. It can cover a stay, or parts of other trips: those are trimmed, and a trip it lands in the
     * middle of is split in two around it. Returns the edited days and remote ids of shared routes removed.
     */
    fun addTrip(start: Long, end: Long, mode: TravelMode): DeleteResult {
        if (end <= start) return DeleteResult(emptySet(), emptyList())
        var newId = 0L
        val db = writableDatabase
        val days = HashSet<String>()
        val remoteIds = mutableListOf<String>()
        db.beginTransaction()
        try {
            db.rawQuery("SELECT DISTINCT date(time / 1000, 'unixepoch', 'localtime') FROM points WHERE time >= $start AND time <= $end", null)
                .use { c -> while (c.moveToNext()) days += c.getString(0) }
            val touched = db.rawQuery("SELECT DISTINCT trip_id FROM points WHERE time >= $start AND time <= $end AND trip_id > 0", null)
                .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
            val refresh = touched.toMutableList()
            for (id in touched) {
                // A trip with dots on both sides of the new one carries on after it as a trip of its own.
                val before = long("SELECT COUNT(*) FROM points WHERE trip_id = $id AND time < $start")
                val after = long("SELECT COUNT(*) FROM points WHERE trip_id = $id AND time > $end")
                if (before == 0L || after == 0L) continue
                val old = trip(id) ?: continue
                val rest = insertTripRow(db, old.mode, old.inferredMode, old.confidence, old.needsReview)
                db.execSQL("UPDATE points SET trip_id = $rest WHERE trip_id = $id AND time > $end")
                refresh += rest
            }
            val created = insertTripRow(db, mode, mode, 1f, needsReview = false)
            db.execSQL("UPDATE points SET trip_id = $created, mode = ${mode.id} WHERE time >= $start AND time <= $end")
            refresh += created
            for (id in refresh) refreshTrip(db, id, remoteIds)
            newId = created
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return DeleteResult(days, remoteIds, newId)
    }

    /**
     * Breaks a trip in two: dots from [atTime] on become a trip of their own, in the same travel mode. Both parts
     * are worked out again (and matched to roads afresh). Returns the edited days and shared routes replaced.
     */
    fun splitTrip(tripId: Long, atTime: Long): DeleteResult {
        val trip = trip(tripId) ?: return DeleteResult(emptySet(), emptyList())
        if (atTime <= trip.startTime || atTime > trip.endTime) return DeleteResult(emptySet(), emptyList())
        val db = writableDatabase
        val remoteIds = mutableListOf<String>()
        db.beginTransaction()
        try {
            val rest = insertTripRow(db, trip.mode, trip.inferredMode, trip.confidence, trip.needsReview)
            db.execSQL("UPDATE points SET trip_id = $rest WHERE trip_id = $tripId AND time >= $atTime")
            // Both halves get new road shapes and questions.
            db.execSQL("DELETE FROM trip_paths WHERE trip_id = $tripId")
            db.execSQL("DELETE FROM road_questions WHERE trip_id = $tripId")
            refreshTrip(db, tripId, remoteIds)
            refreshTrip(db, rest, remoteIds)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return DeleteResult(setOf(dayOf(trip.startTime), dayOf(trip.endTime)), remoteIds)
    }

    /** A new, empty trip row; [refreshTrip] fills in its times and ends once it has dots. */
    private fun insertTripRow(db: SQLiteDatabase, mode: TravelMode, inferred: TravelMode, confidence: Float, needsReview: Boolean): Long =
        db.insert("trips", null, ContentValues().apply {
            put("start_time", 0L)
            put("end_time", 0L)
            put("distance_m", 0.0)
            put("mode", mode.id)
            put("inferred_mode", inferred.id)
            put("confidence", confidence)
            put("needs_review", if (needsReview) 1 else 0)
            put("start_lat", 0.0)
            put("start_lon", 0.0)
            put("end_lat", 0.0)
            put("end_lon", 0.0)
        })

    /** Changes the travel mode of several trips at once (picked together on the Timeline). */
    fun setTripModes(tripIds: Collection<Long>, mode: TravelMode) {
        tripIds.forEach { setTripMode(it, mode) }
    }

    /** Recomputes a trip's times, distance and ends from its dots, or removes it if fewer than two are left. */
    private fun refreshTrip(db: SQLiteDatabase, tripId: Long, remoteIds: MutableList<String>) {
        val pts = db.rawQuery("SELECT time, lat, lon FROM points WHERE trip_id = $tripId ORDER BY time", null).use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getDouble(1), c.getDouble(2))) }
        }
        if (pts.size < 2) {
            // A last dot left over belongs to the stay now, not back with the trip detector.
            db.execSQL("UPDATE points SET trip_id = -1, mode = ${TravelMode.UNKNOWN.id} WHERE trip_id = $tripId")
            dissolveTrips(db, listOf(tripId), remoteIds)
            return
        }
        var meters = 0.0
        for (i in 1 until pts.size) {
            meters += TrackRules.distanceMeters(pts[i - 1].second, pts[i - 1].third, pts[i].second, pts[i].third)
        }
        val first = pts.first()
        val last = pts.last()
        db.execSQL(
            """UPDATE trips SET start_time = ${first.first}, end_time = ${last.first}, distance_m = $meters,
               start_lat = ${first.second}, start_lon = ${first.third}, end_lat = ${last.second}, end_lon = ${last.third},
               uploaded = 0 WHERE id = $tripId"""
        )
        // The shape changed, so match it to roads again.
        db.execSQL("DELETE FROM trip_paths WHERE trip_id = $tripId")
        db.execSQL("DELETE FROM road_questions WHERE trip_id = $tripId")
    }

    /** Forgets the road matches of a mode's trips, so they're matched again (after its kind changed). */
    fun clearRoadMatches(mode: TravelMode) {
        val db = writableDatabase
        db.execSQL("DELETE FROM road_questions WHERE trip_id IN (SELECT id FROM trips WHERE mode = ${mode.id})")
        db.execSQL("DELETE FROM trip_paths WHERE trip_id IN (SELECT id FROM trips WHERE mode = ${mode.id})")
        changed()
    }

    /** How many trips were travelled by [mode]. */
    fun tripCountForMode(mode: TravelMode): Int = long("SELECT COUNT(*) FROM trips WHERE mode = ${mode.id}").toInt()

    /**
     * Moves every trip and dot of a travel mode that's being deleted to [to]: a sub-mode's go to its parent,
     * otherwise to Other, and then they're asked about again. Returns the local days whose dots changed.
     */
    fun replaceMode(mode: TravelMode, to: TravelMode = TravelMode.UNKNOWN): Set<String> {
        val db = writableDatabase
        val days = HashSet<String>()
        val other = to.id
        val ask = if (to == TravelMode.UNKNOWN) 1 else 0
        db.beginTransaction()
        try {
            db.rawQuery("SELECT DISTINCT date(time / 1000, 'unixepoch', 'localtime') FROM points WHERE mode = ${mode.id}", null)
                .use { c -> while (c.moveToNext()) days += c.getString(0) }
            db.execSQL("DELETE FROM trip_paths WHERE trip_id IN (SELECT id FROM trips WHERE mode = ${mode.id})")
            db.execSQL("DELETE FROM road_questions WHERE trip_id IN (SELECT id FROM trips WHERE mode = ${mode.id})")
            db.execSQL("UPDATE trips SET inferred_mode = $other WHERE inferred_mode = ${mode.id}")
            db.execSQL("UPDATE trips SET mode = $other, needs_review = MAX(needs_review, $ask), uploaded = 0 WHERE mode = ${mode.id}")
            db.execSQL("UPDATE points SET mode = $other WHERE mode = ${mode.id}")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return days
    }

    fun tripsNeedingReview(): List<Trip> = trips("WHERE needs_review = 1 ORDER BY start_time DESC")

    fun tripsNotUploaded(limit: Int = 400): List<Trip> = trips("WHERE uploaded = 0 ORDER BY start_time LIMIT $limit")

    fun markUploaded(tripId: Long, remoteId: String) {
        writableDatabase.execSQL("UPDATE trips SET uploaded = 1, remote_id = ? WHERE id = ?", arrayOf<Any>(remoteId, tripId))
    }

    fun markAllForUpload() {
        writableDatabase.execSQL("UPDATE trips SET uploaded = 0")
    }

    private fun trips(clause: String): List<Trip> =
        readableDatabase.rawQuery(
            """SELECT id, start_time, end_time, distance_m, mode, inferred_mode, confidence, needs_review,
                      start_lat, start_lon, end_lat, end_lon, remote_id FROM trips $clause""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Trip(
                            id = c.getLong(0),
                            startTime = c.getLong(1),
                            endTime = c.getLong(2),
                            distanceM = c.getDouble(3),
                            mode = TravelMode.fromId(c.getInt(4)),
                            inferredMode = TravelMode.fromId(c.getInt(5)),
                            confidence = c.getFloat(6),
                            needsReview = c.getInt(7) == 1,
                            startLat = c.getDouble(8),
                            startLon = c.getDouble(9),
                            endLat = c.getDouble(10),
                            endLon = c.getDouble(11),
                            remoteId = if (c.isNull(12)) null else c.getString(12),
                        )
                    )
                }
            }
        }

    /** Interleaved [lat0, lon0, lat1, lon1, ...] of a trip's points. */
    fun tripPath(tripId: Long): DoubleArray =
        readableDatabase.rawQuery("SELECT lat, lon FROM points WHERE trip_id = $tripId ORDER BY time", null).use { c ->
            val out = DoubleArray(c.count * 2)
            var i = 0
            while (c.moveToNext()) {
                out[i++] = c.getDouble(0)
                out[i++] = c.getDouble(1)
            }
            out
        }

    /**
     * The mode the user most often chose for earlier vehicle trips between roughly the same two places
     * (within ~300 m, either direction), so a regular commute is only asked about once.
     */
    fun answeredModeForSimilarTrip(startLat: Double, startLon: Double, endLat: Double, endLon: Double): TravelMode? {
        val d = 0.003
        fun near(latCol: String, lonCol: String, lat: Double, lon: Double) =
            "$latCol BETWEEN ${lat - d} AND ${lat + d} AND $lonCol BETWEEN ${lon - d} AND ${lon + d}"
        val vehicles = TravelMode.vehicles.joinToString(",") { it.id.toString() }
        val sql = """SELECT mode, COUNT(*) n FROM trips
            WHERE needs_review = 0 AND mode IN ($vehicles) AND (
              (${near("start_lat", "start_lon", startLat, startLon)} AND ${near("end_lat", "end_lon", endLat, endLon)}) OR
              (${near("start_lat", "start_lon", endLat, endLon)} AND ${near("end_lat", "end_lon", startLat, startLon)}))
            GROUP BY mode ORDER BY n DESC LIMIT 1"""
        return readableDatabase.rawQuery(sql, null).use { c ->
            if (c.moveToFirst()) TravelMode.fromId(c.getInt(0)) else null
        }
    }

    /** The vehicle mode the user picks most often, used as the suggested answer. */
    fun mostCommonVehicleMode(): TravelMode? {
        val vehicles = TravelMode.vehicles.joinToString(",") { it.id.toString() }
        return readableDatabase.rawQuery(
            "SELECT mode, COUNT(*) n FROM trips WHERE needs_review = 0 AND mode IN ($vehicles) GROUP BY mode ORDER BY n DESC LIMIT 1",
            null,
        ).use { c -> if (c.moveToFirst()) TravelMode.fromId(c.getInt(0)) else null }
    }

    fun distanceByMode(from: Long, to: Long): Map<TravelMode, Double> =
        readableDatabase.rawQuery(
            "SELECT mode, SUM(distance_m) FROM trips WHERE start_time >= $from AND start_time < $to GROUP BY mode", null
        ).use { c ->
            buildMap {
                while (c.moveToNext()) put(TravelMode.fromId(c.getInt(0)), c.getDouble(1))
            }
        }

    /** Distance per local calendar day ("yyyy-MM-dd" to metres). */
    fun distanceByDay(from: Long, to: Long): Map<String, Double> =
        readableDatabase.rawQuery(
            """SELECT date(start_time / 1000, 'unixepoch', 'localtime') d, SUM(distance_m) FROM trips
               WHERE start_time >= $from AND start_time < $to GROUP BY d""",
            null,
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getDouble(1)) } }

    fun tripCount(from: Long, to: Long): Int =
        long("SELECT COUNT(*) FROM trips WHERE start_time >= $from AND start_time < $to").toInt()

    // ---- Road snapping ----

    /** Trips that haven't been matched to roads yet, oldest first. */
    /** Forgets the [status] road shapes of trips in [modeIds], so they're matched again. */
    fun clearPaths(modeIds: Collection<Int>, status: Int) {
        if (modeIds.isEmpty()) return
        writableDatabase.execSQL(
            "DELETE FROM trip_paths WHERE status = $status AND trip_id IN (SELECT id FROM trips WHERE mode IN (${modeIds.joinToString(",")}))"
        )
        changed()
    }

    fun tripsWithoutPath(limit: Int = 20): List<Trip> =
        trips("WHERE id NOT IN (SELECT trip_id FROM trip_paths) ORDER BY start_time LIMIT $limit")

    fun trip(id: Long): Trip? = trips("WHERE id = $id").firstOrNull()

    fun tripPathRow(tripId: Long): TripPathRow? =
        readableDatabase.rawQuery("SELECT status, base_poly, poly FROM trip_paths WHERE trip_id = $tripId", null).use { c ->
            if (c.moveToFirst()) TripPathRow(c.getInt(0), c.getStringOrNull(1), c.getStringOrNull(2)) else null
        }

    /**
     * Saves a trip's road-snapped shape. [poly] is what gets drawn (null keeps the smoothed recording);
     * [basePoly] is the matched shape that answers to road questions are spliced into.
     */
    fun saveTripPath(tripId: Long, status: Int, basePoly: String?, poly: String?) {
        val values = ContentValues().apply {
            put("trip_id", tripId)
            put("status", status)
            put("base_poly", basePoly)
            put("poly", poly)
            if (poly != null) {
                val pts = Polyline.decode(poly)
                var minLat = 90.0
                var maxLat = -90.0
                var minLon = 180.0
                var maxLon = -180.0
                for (i in pts.indices step 2) {
                    minLat = minOf(minLat, pts[i]); maxLat = maxOf(maxLat, pts[i])
                    minLon = minOf(minLon, pts[i + 1]); maxLon = maxOf(maxLon, pts[i + 1])
                }
                put("min_lat", minLat); put("max_lat", maxLat); put("min_lon", minLon); put("max_lon", maxLon)
            }
        }
        val db = writableDatabase
        db.insertWithOnConflict("trip_paths", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        // The backup keeps road shapes and answers, so the trip's day goes up again.
        trip(tripId)?.let { Prefs.addDirtyBackupDays(listOf(dayOf(it.startTime))) }
        // Friends get the improved shape on the next sync.
        db.execSQL("UPDATE trips SET uploaded = 0 WHERE id = $tripId")
        changed()
    }

    /** Road-snapped trip shapes overlapping [box], for travel modes being shown. */
    fun snappedPathsIn(box: GeoBox, modes: Set<TravelMode>): List<String> {
        val modeFilter = if (modes.containsAll(TravelMode.entries)) "" else
            " AND t.mode IN (${modes.joinToString(",") { it.id.toString() }.ifEmpty { "-1" }})"
        return readableDatabase.rawQuery(
            """SELECT p.poly FROM trip_paths p JOIN trips t ON t.id = p.trip_id
               WHERE p.poly IS NOT NULL AND p.min_lat <= ${box.north} AND p.max_lat >= ${box.south}
               AND p.min_lon <= ${box.east} AND p.max_lon >= ${box.west}$modeFilter""",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    }

    /** The shape to show or share for a trip: road-snapped if available, otherwise the recorded points. */
    fun tripDisplayPath(tripId: Long): DoubleArray =
        tripPathRow(tripId)?.poly?.let { Polyline.decode(it) } ?: tripPath(tripId)

    fun addRoadQuestions(tripId: Long, questions: List<NewRoadQuestion>) {
        val db = writableDatabase
        for (q in questions) {
            db.insert("road_questions", null, ContentValues().apply {
                put("trip_id", tripId)
                put("base_from", q.baseFrom)
                put("base_to", q.baseTo)
                put("options", RoadOption.encodeList(q.options))
            })
        }
        changed()
    }

    fun openRoadQuestions(): List<RoadQuestion> = roadQuestions("WHERE chosen = -1 ORDER BY trip_id DESC, id")

    fun roadQuestionsForTrip(tripId: Long): List<RoadQuestion> = roadQuestions("WHERE trip_id = $tripId ORDER BY base_from")

    private fun roadQuestions(clause: String): List<RoadQuestion> =
        readableDatabase.rawQuery("SELECT id, trip_id, base_from, base_to, options, chosen FROM road_questions $clause", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(RoadQuestion(c.getLong(0), c.getLong(1), c.getInt(2), c.getInt(3), RoadOption.decodeList(c.getString(4)), c.getInt(5)))
                }
            }
        }

    /** Trips drawn with a road-matched shape. */
    fun snappedTripIds(): List<Long> =
        readableDatabase.rawQuery("SELECT trip_id FROM trip_paths WHERE poly IS NOT NULL", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }

    /** Forgets a trip's "which road?" questions (when its road shape is dropped). */
    fun dropRoadQuestions(tripId: Long) {
        writableDatabase.execSQL("DELETE FROM road_questions WHERE trip_id = $tripId")
    }

    fun answerRoadQuestion(id: Long, choice: Int) {
        writableDatabase.execSQL("UPDATE road_questions SET chosen = $choice WHERE id = $id")
    }

    /** Time coverage in the range: distinct hours, days, months and years with at least one point. */
    fun coverage(from: Long, to: Long): IntArray =
        readableDatabase.rawQuery(
            """SELECT COUNT(DISTINCT strftime('%Y-%m-%d %H', time / 1000, 'unixepoch', 'localtime')),
                      COUNT(DISTINCT date(time / 1000, 'unixepoch', 'localtime')),
                      COUNT(DISTINCT strftime('%Y-%m', time / 1000, 'unixepoch', 'localtime')),
                      COUNT(DISTINCT strftime('%Y', time / 1000, 'unixepoch', 'localtime'))
               FROM points WHERE time >= $from AND time < $to""",
            null,
        ).use { c -> if (c.moveToFirst()) intArrayOf(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3)) else IntArray(4) }

    /**
     * Distance over ground covered for the first time, in metres, for fixes in the range. Served from a
     * cache that's brought up to date with any new fixes first; call it off the main thread.
     */
    fun exploredMeters(from: Long, to: Long): Double {
        updateExplored()
        val zone = ZoneId.systemDefault()
        val where = buildList {
            if (from > 0) add("day >= '${Instant.ofEpochMilli(from).atZone(zone).toLocalDate()}'")
            if (to < Long.MAX_VALUE) add("day < '${Instant.ofEpochMilli(to).atZone(zone).toLocalDate()}'")
        }.joinToString(" AND ").ifEmpty { "1" }
        return readableDatabase.rawQuery("SELECT COALESCE(SUM(meters), 0) FROM explored_days WHERE $where", null)
            .use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }
    }

    /**
     * Brings the km explored cache up to date: only fixes newer than the last run are walked, carrying on
     * from where it stopped. After edits to older history (deletes, restores, backup restores) it starts over.
     */
    @Synchronized
    fun updateExplored() {
        val db = writableDatabase
        val saved = db.rawQuery(
            "SELECT watermark, prev_time, prev_lat, prev_lon, first_pass, first_pass_m FROM explored_state WHERE id = 0", null
        ).use { c ->
            if (!c.moveToFirst()) null
            else c.getLong(0) to ExploredDistance.State(
                c.getLong(1), c.getDouble(2), c.getDouble(3), if (c.isNull(4)) null else c.getLong(4), c.getDouble(5),
            )
        }
        val watermark = saved?.first ?: Long.MIN_VALUE
        val visited = HashSet<Long>()
        if (saved == null) {
            db.delete("explored_cells", null, null)
            db.delete("explored_days", null, null)
        } else {
            db.rawQuery("SELECT cell FROM explored_cells", null).use { c -> while (c.moveToNext()) visited += c.getLong(0) }
        }
        val zone = ZoneId.systemDefault()
        val perDay = HashMap<String, Double>()
        val walk = ExploredDistance(visited = visited, state = saved?.second) { time, meters ->
            val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toString()
            perDay[day] = (perDay[day] ?: 0.0) + meters
        }
        var last = watermark
        db.rawQuery("SELECT time, lat, lon, interval_ms FROM points WHERE time > $watermark ORDER BY time", null).use { c ->
            while (c.moveToNext()) {
                last = c.getLong(0)
                walk.add(last, c.getDouble(1), c.getDouble(2), c.getInt(3))
            }
        }
        val state = walk.state() ?: return
        if (last == watermark) return
        db.beginTransaction()
        try {
            for (cell in walk.newCells) db.execSQL("INSERT OR IGNORE INTO explored_cells (cell) VALUES ($cell)")
            for ((day, meters) in perDay) {
                db.execSQL("INSERT OR IGNORE INTO explored_days (day, meters) VALUES (?, 0)", arrayOf(day))
                db.execSQL("UPDATE explored_days SET meters = meters + ? WHERE day = ?", arrayOf<Any>(meters, day))
            }
            db.insertWithOnConflict("explored_state", null, ContentValues().apply {
                put("id", 0)
                put("watermark", last)
                put("prev_time", state.prevTime)
                put("prev_lat", state.prevLat)
                put("prev_lon", state.prevLon)
                if (state.firstPass == null) putNull("first_pass") else put("first_pass", state.firstPass)
                put("first_pass_m", state.firstPassMeters)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Older history changed, so km explored must be worked out again from the start. */
    private fun invalidateExplored(db: SQLiteDatabase = writableDatabase) {
        db.delete("explored_state", null, null)
    }

    /**
     * Total time the recording covers in the range: each point stands for its interval, capped at the gap
     * to the next point (so pauses and missed ticks don't count).
     */
    fun capturedMillis(from: Long, to: Long): Long {
        var total = 0L
        readableDatabase.rawQuery(
            "SELECT time, interval_ms FROM points WHERE time >= $from AND time < $to ORDER BY time", null
        ).use { c ->
            var prevTime = -1L
            var prevInterval = 0L
            while (c.moveToNext()) {
                val t = c.getLong(0)
                if (prevTime >= 0) total += minOf(t - prevTime, prevInterval)
                prevTime = t
                prevInterval = c.getLong(1)
            }
            if (prevTime >= 0) total += prevInterval
        }
        return total
    }

    /** Years (local time) that have recorded points, newest first. */
    fun yearsWithData(): List<Int> =
        readableDatabase.rawQuery(
            "SELECT DISTINCT CAST(strftime('%Y', time / 1000, 'unixepoch', 'localtime') AS INTEGER) y FROM points ORDER BY y DESC", null
        ).use { c -> buildList { while (c.moveToNext()) add(c.getInt(0)) } }

    fun firstPointTime(): Long? =
        readableDatabase.rawQuery("SELECT MIN(time) FROM points", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }

    fun longestTrip(from: Long, to: Long): Trip? =
        trips("WHERE start_time >= $from AND start_time < $to ORDER BY distance_m DESC LIMIT 1").firstOrNull()

    // ---- Timeline ----

    fun tripsBetween(from: Long, to: Long): List<Trip> =
        trips("WHERE start_time >= $from AND start_time < $to ORDER BY start_time")

    /** Recorded points in the range that aren't part of any trip (sparse ticks, unprocessed runs). */
    fun untrippedTracks(from: Long, to: Long): TrackData =
        readableDatabase.rawQuery(
            "SELECT time, lat, lon, interval_ms FROM points WHERE time >= $from AND time < $to AND trip_id <= 0 ORDER BY time",
            null,
        ).use { c ->
            val n = c.count
            val time = LongArray(n)
            val lat = DoubleArray(n)
            val lon = DoubleArray(n)
            val interval = IntArray(n)
            var i = 0
            while (i < n && c.moveToNext()) {
                time[i] = c.getLong(0)
                lat[i] = c.getDouble(1)
                lon[i] = c.getDouble(2)
                interval[i] = c.getInt(3)
                i++
            }
            TrackData(i, time, lat, lon, interval)
        }

    /** Bounding box of everything recorded in the range, or null if nothing was. */
    fun boundsBetween(from: Long, to: Long): GeoBox? =
        readableDatabase.rawQuery(
            "SELECT MAX(lat), MIN(lat), MAX(lon), MIN(lon), COUNT(*) FROM points WHERE time >= $from AND time < $to", null
        ).use { c ->
            if (c.moveToFirst() && c.getLong(4) > 0) GeoBox(c.getDouble(0), c.getDouble(1), c.getDouble(2), c.getDouble(3)) else null
        }

    /**
     * Distance and trip count per local day ("yyyy-MM-dd") or, with [byMonth], per month ("yyyy-MM"),
     * including periods that only have recorded points.
     */
    fun periodStats(from: Long, to: Long, byMonth: Boolean): List<PeriodStat> {
        val fmt = if (byMonth) "%Y-%m" else "%Y-%m-%d"
        val out = LinkedHashMap<String, PeriodStat>()
        readableDatabase.rawQuery(
            """SELECT strftime('$fmt', time / 1000, 'unixepoch', 'localtime') k, COUNT(*) FROM points
               WHERE time >= $from AND time < $to GROUP BY k ORDER BY k""",
            null,
        ).use { c -> while (c.moveToNext()) out[c.getString(0)] = PeriodStat(c.getString(0), 0.0, 0, c.getInt(1), emptyMap()) }
        readableDatabase.rawQuery(
            """SELECT strftime('$fmt', start_time / 1000, 'unixepoch', 'localtime') k, mode, SUM(distance_m), COUNT(*) FROM trips
               WHERE start_time >= $from AND start_time < $to GROUP BY k, mode""",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val key = c.getString(0)
                val prev = out[key] ?: PeriodStat(key, 0.0, 0, 0, emptyMap())
                val mode = TravelMode.fromId(c.getInt(1))
                out[key] = PeriodStat(
                    key,
                    prev.meters + c.getDouble(2),
                    prev.trips + c.getInt(3),
                    prev.points,
                    prev.metersByMode + (mode to c.getDouble(2)),
                )
            }
        }
        return out.values.sortedBy { it.key }
    }

    // ---- Areas ----

    fun pointCount(from: Long, to: Long): Long = long("SELECT COUNT(*) FROM points WHERE time >= $from AND time < $to")

    /** Distinct ~220 m cells visited in the period. */
    fun cellsVisited(from: Long, to: Long): Long =
        long("SELECT COUNT(*) FROM (SELECT DISTINCT ${Cells.SQL_ROW}, ${Cells.SQL_COL} FROM points WHERE time >= $from AND time < $to)")

    /** The ~5 km area key ([coarseAreas]) a position is in. */
    private fun areaKey(lat: Double, lon: Double) = "${((lat + 90) * 20).toInt()}:${((lon + 180) * 20).toInt()}"

    /** Kilometres travelled in each ~5 km area ([coarseAreas] keys) during the period, by where each hop starts. */
    fun kmByArea(from: Long, to: Long): Map<String, Double> {
        val out = HashMap<String, Double>()
        var prevTime = 0L
        var prevLat = 0.0
        var prevLon = 0.0
        var first = true
        readableDatabase.rawQuery("SELECT time, lat, lon, interval_ms FROM points WHERE time >= $from AND time < $to ORDER BY time", null).use { c ->
            while (c.moveToNext()) {
                val time = c.getLong(0)
                val lat = c.getDouble(1)
                val lon = c.getDouble(2)
                if (!first && !TrackRules.isBreak(prevTime, prevLat, prevLon, time, lat, lon, c.getInt(3))) {
                    val key = areaKey(prevLat, prevLon)
                    out[key] = (out[key] ?: 0.0) + TrackRules.distanceMeters(prevLat, prevLon, lat, lon) / 1000.0
                }
                first = false
                prevTime = time
                prevLat = lat
                prevLon = lon
            }
        }
        return out
    }

    /**
     * Map squares (~220 m, see [Cells]) in each ~5 km area during the period: every square visited, or with
     * [onlyNew] only those visited for the very first time.
     */
    fun cellsByArea(from: Long, to: Long, onlyNew: Boolean): Map<String, Int> {
        val sql = if (onlyNew) {
            """SELECT AVG(lat), AVG(lon) FROM points GROUP BY ${Cells.SQL_ROW}, ${Cells.SQL_COL}
               HAVING MIN(time) >= $from AND MIN(time) < $to"""
        } else {
            """SELECT AVG(lat), AVG(lon) FROM points WHERE time >= $from AND time < $to GROUP BY ${Cells.SQL_ROW}, ${Cells.SQL_COL}"""
        }
        val out = HashMap<String, Int>()
        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val key = areaKey(c.getDouble(0), c.getDouble(1))
                out[key] = (out[key] ?: 0) + 1
            }
        }
        return out
    }

    /** Cells visited for the very first time during the period. */
    fun newCells(from: Long, to: Long): Long =
        long(
            """SELECT COUNT(*) FROM (SELECT MIN(time) t FROM points GROUP BY ${Cells.SQL_ROW}, ${Cells.SQL_COL})
               WHERE t >= $from AND t < $to"""
        )

    /** The cells visited in the period, as [Cells] keys (for drawing areas explored on a map). */
    fun cellKeys(from: Long, to: Long): LongArray =
        readableDatabase.rawQuery(
            "SELECT DISTINCT ${Cells.SQL_ROW}, ${Cells.SQL_COL} FROM points WHERE time >= $from AND time < $to", null
        ).use { c ->
            LongArray(c.count).also { out -> var i = 0; while (c.moveToNext()) out[i++] = Cells.pack(c.getInt(0), c.getInt(1)) }
        }

    /** Every cell ever visited, as [Cells] keys. */
    fun allCells(): HashSet<Long> =
        readableDatabase.rawQuery("SELECT DISTINCT ${Cells.SQL_ROW}, ${Cells.SQL_COL} FROM points", null).use { c ->
            HashSet<Long>(c.count * 2).apply { while (c.moveToNext()) add(Cells.pack(c.getInt(0), c.getInt(1))) }
        }

    /**
     * Where you spend the most time: ~300 m squares ranked by time recorded in them (each dot counts for its
     * interval, capped at 15 minutes), with the average position and hours. For suggesting saved places.
     */
    fun frequentSpots(limit: Int = 20): List<Spot> =
        readableDatabase.rawQuery(
            """SELECT AVG(lat), AVG(lon), SUM(MIN(interval_ms, 900000)) t FROM points
               GROUP BY CAST((lat + 90) * 370 AS INTEGER), CAST((lon + 180) * 370 AS INTEGER)
               ORDER BY t DESC LIMIT $limit""",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(Spot(c.getDouble(0), c.getDouble(1), c.getLong(2) / 3_600_000.0)) } }

    /** ~5 km areas visited in the period with a representative coordinate, busiest first. */
    fun coarseAreas(from: Long, to: Long, limit: Int = 300): List<CoarseArea> =
        readableDatabase.rawQuery(
            """SELECT CAST((lat + 90) * 20 AS INTEGER) r, CAST((lon + 180) * 20 AS INTEGER) c, AVG(lat), AVG(lon), COUNT(*) n,
                      COUNT(DISTINCT date(time / 1000, 'unixepoch', 'localtime'))
               FROM points WHERE time >= $from AND time < $to GROUP BY r, c ORDER BY n DESC LIMIT $limit""",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(CoarseArea("${c.getInt(0)}:${c.getInt(1)}", c.getDouble(2), c.getDouble(3), c.getInt(4), c.getInt(5)))
                }
            }
        }

    /** Cached reverse-geocoding result; null if never looked up. */
    fun place(cell: String): Place? =
        readableDatabase.rawQuery("SELECT city, country, state, country_code FROM places WHERE cell = ?", arrayOf(cell)).use { c ->
            if (c.moveToFirst()) Place(c.getStringOrNull(0), c.getStringOrNull(1), c.getStringOrNull(2), c.getStringOrNull(3)) else null
        }

    fun putPlace(cell: String, place: Place) {
        writableDatabase.insertWithOnConflict("places", null, ContentValues().apply {
            put("cell", cell)
            put("city", place.city)
            put("country", place.country)
            put("state", place.state)
            put("country_code", place.countryCode)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---- Friends ----

    fun replaceFriendRoutes(friendUid: String, routes: List<FriendRoute>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("friend_routes", "friend_uid = ?", arrayOf(friendUid))
            for (r in routes) {
                val pts = Polyline.decode(r.poly)
                if (pts.size < 4) continue
                var minLat = 90.0
                var maxLat = -90.0
                var minLon = 180.0
                var maxLon = -180.0
                for (i in pts.indices step 2) {
                    minLat = minOf(minLat, pts[i]); maxLat = maxOf(maxLat, pts[i])
                    minLon = minOf(minLon, pts[i + 1]); maxLon = maxOf(maxLon, pts[i + 1])
                }
                db.insert("friend_routes", null, ContentValues().apply {
                    put("friend_uid", friendUid)
                    put("doc_id", r.docId)
                    put("mode", r.mode.id)
                    put("poly", r.poly)
                    put("min_lat", minLat)
                    put("max_lat", maxLat)
                    put("min_lon", minLon)
                    put("max_lon", maxLon)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _friendChanges.update { it + 1 }
    }

    fun deleteFriendRoutes(friendUid: String) {
        writableDatabase.delete("friend_routes", "friend_uid = ?", arrayOf(friendUid))
        _friendChanges.update { it + 1 }
    }

    /** A friend's routes that overlap [box] (or all of them when [box] is null). */
    /** The area a friend's shared routes cover; null if none are stored. */
    fun friendBounds(friendUid: String): GeoBox? =
        readableDatabase.rawQuery(
            "SELECT MAX(max_lat), MIN(min_lat), MAX(max_lon), MIN(min_lon), COUNT(*) FROM friend_routes WHERE friend_uid = ?",
            arrayOf(friendUid),
        ).use { c -> if (c.moveToFirst() && c.getLong(4) > 0) GeoBox(c.getDouble(0), c.getDouble(1), c.getDouble(2), c.getDouble(3)) else null }

    fun friendRoutes(friendUid: String, box: GeoBox?, modes: Set<TravelMode>): List<FriendRoute> {
        val boxClause = box?.let {
            " AND min_lat <= ${it.north} AND max_lat >= ${it.south} AND min_lon <= ${it.east} AND max_lon >= ${it.west}"
        } ?: ""
        return readableDatabase.rawQuery(
            "SELECT doc_id, mode, poly FROM friend_routes WHERE friend_uid = ?$boxClause${modeClause(modes)}",
            arrayOf(friendUid),
        ).use { c ->
            buildList { while (c.moveToNext()) add(FriendRoute(c.getString(0), TravelMode.fromId(c.getInt(1)), c.getString(2))) }
        }
    }

    // ---- Helpers ----

    private fun long(sql: String): Long =
        readableDatabase.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    private fun Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

    private fun Cursor.toPoint() =
        LocationPoint(
            getLong(0), getDouble(1), getDouble(2), getFloat(3), getInt(4), getInt(5), getInt(6),
            if (isNull(7)) null else getDouble(7),
        )

    // Box values are doubles we computed ourselves, so inlining them into SQL is safe.
    private fun where(box: GeoBox) =
        "lat BETWEEN ${box.south} AND ${box.north} AND lon BETWEEN ${box.west} AND ${box.east}"

    private fun modeClause(modes: Set<TravelMode>): String =
        if (modes.containsAll(TravelMode.entries)) "" else " AND mode IN (${modes.joinToString(",") { it.id.toString() }.ifEmpty { "-1" }})"

    companion object {
        // Trips closer together than this have no stay between them (the Timeline shows stays from 5 min).
        private const val ADJACENT_MS = 5 * 60_000L

        // GPS blips (see findSpikes).
        private const val SPIKE_MAX_DOTS = 3
        private const val SPIKE_WINDOW_MS = 30 * 60_000L
        private const val SPIKE_MIN_M = 300.0
        private const val SPIKE_COARSE_M = 20f
        private const val SPIKE_IMPOSSIBLE_MPS = 70.0

        // Accuracy given to a dot moved or added by hand, so it never counts as a coarse fix.
        private const val HAND_PLACED_ACCURACY_M = 5f

        private const val POINT_COLS = "time, lat, lon, accuracy, interval_ms, activity, mode, altitude"

        @Volatile
        private var instance: PointStore? = null

        fun get(context: Context): PointStore =
            instance ?: synchronized(this) {
                instance ?: PointStore(context.applicationContext).also { instance = it }
            }

        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
