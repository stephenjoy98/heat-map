package io.github.stephenjoy98.heatmap.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar

data class LocationPoint(
    val time: Long,
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val intervalMs: Int,
    val activity: Int = -1,
    val mode: Int = TravelMode.UNKNOWN.id,
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

class DeleteResult(val days: Set<String>, val removedRemoteIds: List<String>)

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
    SQLiteOpenHelper(context, "points.db", null, 4) {

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
    }

    private fun changed() = _changes.update { it + 1 }

    // ---- Points ----

    /** Returns false if a point with the same timestamp already exists. */
    fun insert(p: LocationPoint): Boolean {
        val id = writableDatabase.insertWithOnConflict("points", null, p.toValues(), SQLiteDatabase.CONFLICT_IGNORE)
        if (id != -1L) changed()
        return id != -1L
    }

    /** Inserts many points in one transaction, skipping timestamps already present. Returns how many were new. */
    fun insertAll(points: List<LocationPoint>): Int {
        if (points.isEmpty()) return 0
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            for (p in points) {
                if (db.insertWithOnConflict("points", null, p.toValues(), SQLiteDatabase.CONFLICT_IGNORE) != -1L) added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (added > 0) changed()
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
    }

    fun deleteAll() {
        val db = writableDatabase
        db.delete("points", null, null)
        db.delete("trips", null, null)
        db.delete("trip_paths", null, null)
        db.delete("road_questions", null, null)
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
     * Deletes points. Trips they belonged to are dissolved so the trip detector rebuilds them from what's
     * left (points keep their travel mode, so answers aren't asked again). Returns the edited days and the
     * remote ids of shared routes that no longer exist.
     */
    fun deletePoints(ids: Collection<Long>): DeleteResult {
        if (ids.isEmpty()) return DeleteResult(emptySet(), emptyList())
        val db = writableDatabase
        val days = HashSet<String>()
        val remoteIds = mutableListOf<String>()
        db.beginTransaction()
        try {
            for (chunk in ids.chunked(500)) {
                val list = chunk.joinToString(",")
                db.rawQuery("SELECT DISTINCT date(time / 1000, 'unixepoch', 'localtime') FROM points WHERE id IN ($list)", null)
                    .use { c -> while (c.moveToNext()) days += c.getString(0) }
                val tripIds = db.rawQuery("SELECT DISTINCT trip_id FROM points WHERE id IN ($list) AND trip_id > 0", null)
                    .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
                if (tripIds.isNotEmpty()) {
                    val trips = tripIds.joinToString(",")
                    db.rawQuery("SELECT remote_id FROM trips WHERE id IN ($trips) AND remote_id IS NOT NULL", null)
                        .use { c -> while (c.moveToNext()) remoteIds += c.getString(0) }
                    db.execSQL("DELETE FROM trips WHERE id IN ($trips)")
                    db.execSQL("DELETE FROM trip_paths WHERE trip_id IN ($trips)")
                    db.execSQL("DELETE FROM road_questions WHERE trip_id IN ($trips)")
                    db.execSQL("UPDATE points SET trip_id = 0 WHERE trip_id IN ($trips)")
                }
                db.execSQL("DELETE FROM points WHERE id IN ($list)")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return DeleteResult(days, remoteIds)
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
            return id
        } finally {
            db.endTransaction()
            changed()
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
        // Friends get the improved shape on the next sync.
        db.execSQL("UPDATE trips SET uploaded = 0 WHERE id = $tripId")
        changed()
    }

    /** Road-snapped trip shapes overlapping [box], for travel modes being shown. */
    fun snappedPathsIn(box: GeoBox, modes: Set<TravelMode>): List<String> {
        val modeFilter = if (modes.size >= TravelMode.entries.size) "" else
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

    /** Cells visited for the very first time during the period. */
    fun newCells(from: Long, to: Long): Long =
        long(
            """SELECT COUNT(*) FROM (SELECT MIN(time) t FROM points GROUP BY ${Cells.SQL_ROW}, ${Cells.SQL_COL})
               WHERE t >= $from AND t < $to"""
        )

    /** Every cell ever visited, as [Cells] keys. */
    fun allCells(): HashSet<Long> =
        readableDatabase.rawQuery("SELECT DISTINCT ${Cells.SQL_ROW}, ${Cells.SQL_COL} FROM points", null).use { c ->
            HashSet<Long>(c.count * 2).apply { while (c.moveToNext()) add(Cells.pack(c.getInt(0), c.getInt(1))) }
        }

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
        LocationPoint(getLong(0), getDouble(1), getDouble(2), getFloat(3), getInt(4), getInt(5), getInt(6))

    // Box values are doubles we computed ourselves, so inlining them into SQL is safe.
    private fun where(box: GeoBox) =
        "lat BETWEEN ${box.south} AND ${box.north} AND lon BETWEEN ${box.west} AND ${box.east}"

    private fun modeClause(modes: Set<TravelMode>): String =
        if (modes.size >= TravelMode.entries.size) "" else " AND mode IN (${modes.joinToString(",") { it.id.toString() }.ifEmpty { "-1" }})"

    companion object {
        private const val POINT_COLS = "time, lat, lon, accuracy, interval_ms, activity, mode"

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
