package io.github.stephenjoy98.heatmap.data

import kotlin.math.floor

/**
 * A fixed grid of roughly 220 m cells used to measure "areas explored" and to compare with friends.
 * Keys pack the row and column into one Long.
 */
object Cells {
    const val PER_DEGREE = 500.0

    fun key(lat: Double, lon: Double): Long = pack(row(lat), col(lon))

    fun row(lat: Double): Int = floor((lat + 90.0) * PER_DEGREE).toInt()
    fun col(lon: Double): Int = floor((lon + 180.0) * PER_DEGREE).toInt()
    fun pack(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xFFFFFFFFL)
    fun rowOf(key: Long): Int = (key shr 32).toInt()
    fun colOf(key: Long): Int = key.toInt()

    fun south(row: Int): Double = row / PER_DEGREE - 90.0
    fun west(col: Int): Double = col / PER_DEGREE - 180.0

    /** SQL expressions computing the same row/column as [row] and [col]; coordinates are offset to stay positive. */
    const val SQL_ROW = "CAST((lat + 90.0) * 500.0 AS INTEGER)"
    const val SQL_COL = "CAST((lon + 180.0) * 500.0 AS INTEGER)"

    /** Adds the cells a straight segment passes through, sampling about every 100 m. */
    fun addSegment(into: MutableSet<Long>, lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
        val meters = TrackRules.distanceMeters(lat1, lon1, lat2, lon2)
        val steps = (meters / 100.0).toInt().coerceIn(1, 2000)
        for (i in 0..steps) {
            val f = i.toDouble() / steps
            into += key(lat1 + (lat2 - lat1) * f, lon1 + (lon2 - lon1) * f)
        }
    }
}
