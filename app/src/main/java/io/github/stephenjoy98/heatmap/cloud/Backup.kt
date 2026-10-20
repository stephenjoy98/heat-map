package io.github.stephenjoy98.heatmap.cloud

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.github.stephenjoy98.heatmap.data.LocationPoint
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.tracking.TripDetector
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId

/**
 * Backs up the full history (with timestamps) to the user's private area in Firestore, one document per
 * day, and restores it on a new phone. Only the signed-in user can read their backup.
 */
object Backup {
    private const val POINTS_PER_DOC = 7_000
    private const val MODES_DOC = "modes"

    /** Uploads every day that changed since the last backup. Returns the number of days uploaded. */
    suspend fun backUp(context: Context): Int {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in to back up")
        val store = PointStore.get(context)
        val col = FirebaseFirestore.getInstance().collection("users").document(uid).collection("backup")
        val started = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        // Re-upload whole days so each document is always complete, including days edited since.
        val dirty = Prefs.dirtyBackupDays.value
        val days = (store.daysWithPointsAfter(Prefs.lastBackup.value) + dirty).distinct().sorted()
        for (day in days) {
            val date = LocalDate.parse(day)
            val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val points = store.pointsBetween(from, to)
            val chunks = points.chunked(POINTS_PER_DOC)
            val batch = FirebaseFirestore.getInstance().batch()
            chunks.forEachIndexed { i, chunk ->
                batch.set(col.document(if (i == 0) day else "${day}_$i"), encode(chunk))
            }
            // Remove leftover parts from before an edit made the day smaller (or empty).
            for (i in chunks.size until chunks.size + 3) batch.delete(col.document(if (i == 0) day else "${day}_$i"))
            batch.commit().await()
        }
        // The travel modes you added, so a restore knows them.
        col.document(MODES_DOC).set(mapOf("added" to TravelMode.encodeAdded(), "next" to TravelMode.nextId)).await()
        Prefs.setLastBackup(started)
        Prefs.clearDirtyBackupDays(dirty)
        return days.size
    }

    /** Downloads the backup and merges it into this phone's history. Returns the number of new points. */
    suspend fun restore(context: Context): Int {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in to restore")
        val store = PointStore.get(context)
        val docs = FirebaseFirestore.getInstance().collection("users").document(uid).collection("backup").get().await()
        var added = 0
        // Modes first, so restored dots keep the modes they were travelled by.
        docs.documents.firstOrNull { it.id == MODES_DOC }?.let { d ->
            Prefs.mergeModes(d.getString("added"), d.getLong("next")?.toInt() ?: 0)
        }
        for (doc in docs.documents) {
            if (doc.id == MODES_DOC) continue
            added += store.insertAll(decode(doc.data ?: continue))
        }
        TripDetector.process(context)
        return added
    }

    private fun encode(points: List<LocationPoint>): Map<String, Any> = mapOf(
        "t" to points.map { it.time },
        "la" to points.map { it.lat },
        "lo" to points.map { it.lon },
        "ac" to points.map { it.accuracy.toDouble() },
        "iv" to points.map { it.intervalMs },
        "at" to points.map { it.activity },
        "md" to points.map { it.mode },
        // Firestore lists can't hold nulls, so a missing altitude is stored as NaN.
        "al" to points.map { it.altitude ?: Double.NaN },
    )

    private fun decode(data: Map<String, Any>): List<LocationPoint> {
        fun nums(key: String): List<Number> = (data[key] as? List<*>)?.filterIsInstance<Number>() ?: emptyList()
        val t = nums("t")
        val la = nums("la")
        val lo = nums("lo")
        val ac = nums("ac")
        val iv = nums("iv")
        val at = nums("at")
        val md = nums("md")
        val al = nums("al")
        return t.indices.mapNotNull { i ->
            if (i >= la.size || i >= lo.size) return@mapNotNull null
            LocationPoint(
                time = t[i].toLong(),
                lat = la[i].toDouble(),
                lon = lo[i].toDouble(),
                accuracy = ac.getOrNull(i)?.toFloat() ?: 0f,
                intervalMs = iv.getOrNull(i)?.toInt() ?: 60_000,
                activity = at.getOrNull(i)?.toInt() ?: -1,
                mode = md.getOrNull(i)?.toInt() ?: 0,
                altitude = al.getOrNull(i)?.toDouble()?.takeUnless { it.isNaN() },
            )
        }
    }
}
