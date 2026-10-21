package io.github.stephenjoy98.heatmap.cloud

import android.content.Context
import android.os.StatFs
import com.google.firebase.auth.FirebaseAuth
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import java.io.File
import kotlin.math.ceil

/**
 * How much space Location Ticker uses on the phone and in the cloud, and how much is left.
 *
 * On the phone the numbers are exact (file sizes and the storage's free space). For the cloud, Firebase doesn't
 * tell an app how much it stores, so it's worked out from what the app uploads, using Firestore's published
 * rules for document sizes: each number takes 8 bytes, each text its length plus one, plus the document's name
 * and a little overhead. The free Spark plan includes 1 GiB of Firestore storage for the whole Firebase project.
 */
object StorageUsage {
    const val FREE_CLOUD_BYTES = 1L shl 30

    class Phone(val databaseBytes: Long, val cacheBytes: Long, val otherBytes: Long, val freeBytes: Long, val totalBytes: Long) {
        val appBytes: Long get() = databaseBytes + cacheBytes + otherBytes
    }

    class Cloud(val backupBytes: Long, val routeBytes: Long) {
        val totalBytes: Long get() = backupBytes + routeBytes
        val leftBytes: Long get() = (FREE_CLOUD_BYTES - totalBytes).coerceAtLeast(0)
    }

    // Must match Backup: dots per backup document, and the arrays each document holds per dot.
    private const val POINTS_PER_DOC = 4_000 // Same as Backup.
    private val BACKUP_FIELDS = listOf("t", "la", "lo", "ac", "iv", "at", "md", "al")
    private const val NUMBER_BYTES = 8
    private const val DOC_OVERHEAD = 32
    // An encoded polyline takes about 6 characters per point.
    private const val POLYLINE_CHARS_PER_POINT = 6

    /** Call off the main thread. */
    fun phone(context: Context): Phone {
        val dbFiles = listOf("points.db", "points.db-wal", "points.db-shm", "points.db-journal").map { context.getDatabasePath(it) }
        val database = dbFiles.sumOf { if (it.exists()) it.length() else 0L }
        val cache = sizeOf(context.cacheDir) + (context.externalCacheDir?.let(::sizeOf) ?: 0L)
        val all = sizeOf(File(context.applicationInfo.dataDir))
        val stat = StatFs(context.filesDir.absolutePath)
        return Phone(
            databaseBytes = database,
            cacheBytes = cache,
            otherBytes = (all - database - sizeOf(context.cacheDir)).coerceAtLeast(0),
            freeBytes = stat.availableBytes,
            totalBytes = stat.totalBytes,
        )
    }

    /** An estimate of what the backup and shared routes take in Firestore. Call off the main thread. */
    fun cloud(context: Context): Cloud {
        val store = PointStore.get(context)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        fun name(vararg segments: String) = segments.sumOf { it.length + 1 } + 16
        val fieldNames = BACKUP_FIELDS.sumOf { it.length + 1 }
        var backup = 0L
        for (n in store.pointsPerDay()) {
            val docs = ceil(n / POINTS_PER_DOC.toDouble()).toInt().coerceAtLeast(1)
            backup += docs * (name("users", uid, "backup", "2026-01-01_1") + fieldNames + DOC_OVERHEAD).toLong()
            backup += n.toLong() * BACKUP_FIELDS.size * NUMBER_BYTES
        }
        // The travel modes, height answers and profile documents are tiny; count a kilobyte for them.
        backup += 1024
        val routePoints = store.routePointCounts()
        // Trips in the backup: about a dozen small fields each, plus the road shape twice (matched and final).
        backup += routePoints.sumOf { points -> 160L + 2L * (points * POLYLINE_CHARS_PER_POINT + 1) }
        val routes = if (Prefs.shareRoutes.value) {
            routePoints.sumOf { points ->
                (name("users", uid, "routes", "a".repeat(20)) + 2 + NUMBER_BYTES + 2 + (minOf(points, 1500) * POLYLINE_CHARS_PER_POINT + 1) +
                    2 + NUMBER_BYTES + DOC_OVERHEAD).toLong()
            }
        } else {
            0L
        }
        return Cloud(backup, routes)
    }

    private fun sizeOf(f: File): Long = when {
        !f.exists() -> 0L
        f.isFile -> f.length()
        else -> f.listFiles()?.sumOf(::sizeOf) ?: 0L
    }

    /** "12.4 MB", "980 KB", "41.2 GB". */
    fun format(bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var u = 0
        while (v >= 1000 && u < units.lastIndex) {
            v /= 1024
            u++
        }
        return if (u == 0 || v >= 100) "${v.toInt()} ${units[u]}" else String.format(java.util.Locale.US, "%.1f %s", v, units[u])
    }
}
