package io.github.stephenjoy98.heatmap.data

import android.content.ContentValues
import android.content.Context
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
)

data class GeoBox(val north: Double, val south: Double, val east: Double, val west: Double)

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

data class Stats(val total: Long, val today: Long, val latest: LocationPoint?)

class PointStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "points.db", null, 1) {

    private val _changes = MutableStateFlow(0L)

    /** Bumps every time points are added or removed. */
    val changes: StateFlow<Long> = _changes

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
        db.execSQL("CREATE INDEX points_time ON points(time)")
        db.execSQL("CREATE INDEX points_lat_lon ON points(lat, lon)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(p: LocationPoint) {
        val values = ContentValues().apply {
            put("time", p.time)
            put("lat", p.lat)
            put("lon", p.lon)
            put("accuracy", p.accuracy)
            put("interval_ms", p.intervalMs)
        }
        writableDatabase.insert("points", null, values)
        _changes.update { it + 1 }
    }

    fun deleteAll() {
        writableDatabase.delete("points", null, null)
        _changes.update { it + 1 }
    }

    fun stats(): Stats {
        val db = readableDatabase
        val total = db.rawQuery("SELECT COUNT(*) FROM points", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = db.rawQuery("SELECT COUNT(*) FROM points WHERE time >= $midnight", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
        return Stats(total, today, latest())
    }

    fun latest(): LocationPoint? =
        readableDatabase.rawQuery(
            "SELECT time, lat, lon, accuracy, interval_ms FROM points ORDER BY time DESC LIMIT 1", null
        ).use { c ->
            if (c.moveToFirst()) {
                LocationPoint(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getFloat(3), c.getInt(4))
            } else null
        }

    fun countIn(box: GeoBox): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM points WHERE ${where(box)}", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun tracksIn(box: GeoBox): TrackData =
        readableDatabase.rawQuery(
            "SELECT time, lat, lon, interval_ms FROM points WHERE ${where(box)} ORDER BY time", null
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
    fun gridIn(box: GeoBox, cellLat: Double, cellLon: Double): GridData =
        readableDatabase.rawQuery(
            """SELECT CAST((lat - ${box.south}) / $cellLat AS INTEGER) AS r,
                      CAST((lon - ${box.west}) / $cellLon AS INTEGER) AS c,
                      COUNT(*)
               FROM points WHERE ${where(box)} GROUP BY r, c""",
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

    /** Streams every point in time order, for export. */
    fun forEachPoint(block: (LocationPoint) -> Unit) {
        readableDatabase.rawQuery(
            "SELECT time, lat, lon, accuracy, interval_ms FROM points ORDER BY time", null
        ).use { c ->
            while (c.moveToNext()) {
                block(LocationPoint(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getFloat(3), c.getInt(4)))
            }
        }
    }

    // Box values are doubles we computed ourselves, so inlining them into SQL is safe.
    private fun where(box: GeoBox) =
        "lat BETWEEN ${box.south} AND ${box.north} AND lon BETWEEN ${box.west} AND ${box.east}"

    companion object {
        @Volatile
        private var instance: PointStore? = null

        fun get(context: Context): PointStore =
            instance ?: synchronized(this) {
                instance ?: PointStore(context.applicationContext).also { instance = it }
            }
    }
}
