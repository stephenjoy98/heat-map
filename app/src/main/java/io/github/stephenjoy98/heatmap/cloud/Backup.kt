package io.github.stephenjoy98.heatmap.cloud

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.github.stephenjoy98.heatmap.data.SavedPlace
import io.github.stephenjoy98.heatmap.data.HeightGrid
import io.github.stephenjoy98.heatmap.data.LevelBackup
import io.github.stephenjoy98.heatmap.data.TripBackup
import io.github.stephenjoy98.heatmap.data.LocationPoint
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.tracking.TripDetector
import io.github.stephenjoy98.heatmap.data.SatelliteUse
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId

/**
 * Backs up the full history (with timestamps) to the user's private area in Firestore, and restores it on a
 * new phone. Only the signed-in user can read their backup. Documents:
 * - `yyyy-MM-dd` (and `_1`, `_2`… for busy days): the day's dots, as parallel lists.
 * - `trips_yyyy-MM-dd`: the trips that start that day, with their travel mode, whether they still need an
 *   answer, their road-snapped shape and "which road?" answers.
 * - `deleted_yyyy-MM-dd`: dots you deleted that day, so they stay deleted (and can still be restored).
 * - `sats_yyyy-MM-dd`: which satellites fixed that day's dots and how often (Insights › Satellites).
 * - `levels`: answers to "different heights here?".
 * - `modes`: the travel modes you added.
 * - `settings`: everything you've set up: recording, map, theme, units, places, travel mode names, colours
 *   and deletions, filters and backup choices.
 */
object Backup {
    private const val POINTS_PER_DOC = 7_000
    private const val MODES_DOC = "modes"
    private const val LEVELS_DOC = "levels"
    private const val TRIPS_PREFIX = "trips_"
    private const val DELETED_PREFIX = "deleted_"
    private const val SATS_PREFIX = "sats_"
    private const val SETTINGS_DOC = "settings"

    /** Uploads every day that changed since the last backup. Returns the number of days uploaded. */
    suspend fun backUp(context: Context): Int {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in to back up")
        val store = PointStore.get(context)
        val col = FirebaseFirestore.getInstance().collection("users").document(uid).collection("backup")
        val started = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        // Backups from before trips and deleted dots were included get every day uploaded once more.
        if (!Prefs.backupComplete) Prefs.addDirtyBackupDays(store.daysWithPointsAfter(0L) + store.daysWithDeleted())
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
            val deleted = store.deletedForBackup(from, to)
            if (deleted.isEmpty()) batch.delete(col.document(DELETED_PREFIX + day))
            else batch.set(col.document(DELETED_PREFIX + day), encode(deleted.takeLast(POINTS_PER_DOC)))
            val trips = store.tripBackups(from, to)
            if (trips.isEmpty()) batch.delete(col.document(TRIPS_PREFIX + day))
            else batch.set(col.document(TRIPS_PREFIX + day), mapOf("trips" to trips.map(::encodeTrip)))
            val (sats, satFixes) = store.satelliteDay(date.toEpochDay())
            if (sats.isEmpty()) {
                batch.delete(col.document(SATS_PREFIX + day))
            } else {
                batch.set(
                    col.document(SATS_PREFIX + day),
                    mapOf(
                        "c" to sats.map { it.constellation },
                        "s" to sats.map { it.svid },
                        "n" to sats.map { it.fixes },
                        "f" to satFixes,
                    ),
                )
            }
            batch.commit().await()
        }
        // The travel modes you added, so a restore knows them.
        col.document(MODES_DOC).set(mapOf("added" to TravelMode.encodeAdded(), "next" to TravelMode.nextId)).await()
        col.document(SETTINGS_DOC).set(mapOf("json" to Prefs.exportSettings())).await()
        // Height answers: few and small, so always the full set.
        col.document(LEVELS_DOC).set(mapOf("levels" to store.levelBackups().map { l ->
            mapOf("a" to l.area, "la" to l.lat, "lo" to l.lon, "l" to l.low, "h" to l.high, "d" to l.decision)
        })).await()
        Prefs.setLastBackup(started)
        Prefs.clearDirtyBackupDays(dirty)
        Prefs.setBackupComplete()
        return days.size
    }

    /** What a restore would bring back, worked out from the downloaded backup before anything is changed. */
    class Preview(
        internal val docs: List<DocumentSnapshot>,
        val days: Int,
        val firstDay: String?,
        val lastDay: String?,
        val dots: Int,
        /** Dots this phone doesn't have yet. */
        val newDots: Int,
        val trips: Int,
        val roadAnswers: Int,
        val deletedDots: Int,
        val heightAnswers: Int,
        /** Days with satellite counts, and how many different satellites they name. */
        val satelliteDays: Int,
        val satellites: Int,
        val customModes: List<String>,
        val places: List<String>,
        val hasSettings: Boolean,
    ) {
        val isEmpty: Boolean get() = dots == 0 && trips == 0 && !hasSettings
    }

    private suspend fun download(): List<DocumentSnapshot> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in to restore")
        return FirebaseFirestore.getInstance().collection("users").document(uid).collection("backup").get().await().documents
    }

    private fun isDayDoc(id: String) = id.isNotEmpty() && id[0].isDigit()

    /** Downloads the backup and sums up what restoring it would do. Call off the main thread. */
    suspend fun preview(context: Context): Preview {
        val store = PointStore.get(context)
        val docs = download()
        val dayDocs = docs.filter { isDayDoc(it.id) }
        val days = dayDocs.map { it.id.substringBefore('_') }.distinct().sorted()
        var dots = 0
        var newDots = 0
        for (d in dayDocs) {
            val times = (d.get("t") as? List<*>)?.filterIsInstance<Number>()?.map { it.toLong() }.orEmpty()
            dots += times.size
            newDots += times.size - store.countExistingTimes(times)
        }
        var trips = 0
        var roadAnswers = 0
        for (d in docs.filter { it.id.startsWith(TRIPS_PREFIX) }) {
            val list = (d.get("trips") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
            trips += list.size
            for (t in list) {
                val q = t["q"] as? String ?: continue
                roadAnswers += runCatching {
                    val a = org.json.JSONArray(q)
                    (0 until a.length()).count { a.getJSONObject(it).optInt("chosen", -1) >= 0 }
                }.getOrDefault(0)
            }
        }
        val deletedDots = docs.filter { it.id.startsWith(DELETED_PREFIX) }.sumOf { (it.get("t") as? List<*>)?.size ?: 0 }
        val heightAnswers = (docs.firstOrNull { it.id == LEVELS_DOC }?.get("levels") as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>().count { ((it["d"] as? Number)?.toInt() ?: HeightGrid.ASK) != HeightGrid.ASK }
        val satDocs = docs.filter { it.id.startsWith(SATS_PREFIX) }
        val satellites = satDocs.flatMap { d ->
            val c = (d.get("c") as? List<*>)?.filterIsInstance<Number>().orEmpty()
            val s = (d.get("s") as? List<*>)?.filterIsInstance<Number>().orEmpty()
            c.zip(s) { a, b -> a.toInt() * 1000 + b.toInt() }
        }.toSet().size
        val customModes = docs.firstOrNull { it.id == MODES_DOC }?.getString("added")?.let { json ->
            runCatching {
                val a = org.json.JSONArray(json)
                (0 until a.length()).map { a.getJSONObject(it).getString("label") }
            }.getOrNull()
        }.orEmpty()
        val settingsJson = docs.firstOrNull { it.id == SETTINGS_DOC }?.getString("json")
        val places = settingsJson?.let { json ->
            runCatching { SavedPlace.decode(org.json.JSONObject(json).optJSONObject("saved_places")?.optString("s")).map { it.name } }.getOrNull()
        }.orEmpty()
        return Preview(
            docs = docs,
            days = days.size,
            firstDay = days.firstOrNull(),
            lastDay = days.lastOrNull(),
            dots = dots,
            newDots = newDots,
            trips = trips,
            roadAnswers = roadAnswers,
            deletedDots = deletedDots,
            heightAnswers = heightAnswers,
            satelliteDays = satDocs.size,
            satellites = satellites,
            customModes = customModes,
            places = places,
            hasSettings = settingsJson != null,
        )
    }

    /**
     * Merges the backup into this phone's history (using [preview]'s download if given, so it isn't fetched
     * twice). Returns the number of new points.
     */
    suspend fun restore(context: Context, preview: Preview? = null): Int {
        val store = PointStore.get(context)
        val docs = preview?.docs ?: download()
        var added = 0
        // Settings first: they include the travel modes you renamed, recoloured or deleted.
        docs.firstOrNull { it.id == SETTINGS_DOC }?.getString("json")?.let { Prefs.importSettings(context, it) }
        // Modes next, so restored dots keep the modes they were travelled by.
        docs.firstOrNull { it.id == MODES_DOC }?.let { d ->
            Prefs.mergeModes(d.getString("added"), d.getLong("next")?.toInt() ?: 0)
        }
        docs.firstOrNull { it.id == LEVELS_DOC }?.let { d ->
            @Suppress("UNCHECKED_CAST")
            val list = (d.get("levels") as? List<Map<String, Any?>>).orEmpty()
            store.restoreLevels(list.mapNotNull { m ->
                LevelBackup(
                    area = (m["a"] as? Number)?.toLong() ?: return@mapNotNull null,
                    lat = (m["la"] as? Number)?.toDouble() ?: return@mapNotNull null,
                    lon = (m["lo"] as? Number)?.toDouble() ?: return@mapNotNull null,
                    low = (m["l"] as? Number)?.toDouble() ?: 0.0,
                    high = (m["h"] as? Number)?.toDouble() ?: 0.0,
                    decision = (m["d"] as? Number)?.toInt() ?: HeightGrid.ASK,
                )
            })
        }
        val tripDocs = docs.filter { it.id.startsWith(TRIPS_PREFIX) }
        // Dots and trips go in together, before the trip detector can claim the dots.
        synchronized(TripDetector) {
            // Deleted dots first, so the dots restored next don't bring them back.
            for (doc in docs) {
                if (doc.id.startsWith(DELETED_PREFIX)) store.restoreDeleted(decode(doc.data ?: continue))
            }
            for (doc in docs) {
                val id = doc.id
                if (id == MODES_DOC || id == LEVELS_DOC || id == SETTINGS_DOC || id.startsWith(TRIPS_PREFIX) ||
                    id.startsWith(DELETED_PREFIX) || id.startsWith(SATS_PREFIX)
                ) continue
                added += store.insertAll(decode(doc.data ?: continue))
            }
            for (doc in tripDocs) {
                @Suppress("UNCHECKED_CAST")
                val list = (doc.get("trips") as? List<Map<String, Any?>>).orEmpty()
                store.restoreTrips(list.mapNotNull(::decodeTrip))
            }
        }
        for (doc in docs.filter { it.id.startsWith(SATS_PREFIX) }) {
            val day = runCatching { LocalDate.parse(doc.id.removePrefix(SATS_PREFIX)).toEpochDay() }.getOrNull() ?: continue
            val c = (doc.get("c") as? List<*>)?.filterIsInstance<Number>().orEmpty()
            val s = (doc.get("s") as? List<*>)?.filterIsInstance<Number>().orEmpty()
            val n = (doc.get("n") as? List<*>)?.filterIsInstance<Number>().orEmpty()
            val uses = c.indices.filter { it < s.size && it < n.size }.map { SatelliteUse(c[it].toInt(), s[it].toInt(), n[it].toLong()) }
            store.restoreSatelliteDay(day, uses, (doc.get("f") as? Number)?.toLong() ?: 0L)
        }
        // Dots without a backed-up trip (from older backups) are sorted into trips as usual.
        TripDetector.process(context)
        return added
    }

    private fun encodeTrip(t: TripBackup): Map<String, Any?> = mapOf(
        "s" to t.start,
        "e" to t.end,
        "m" to t.mode,
        "im" to t.inferredMode,
        "c" to t.confidence,
        "r" to t.needsReview,
        "ps" to t.pathStatus,
        "bp" to t.basePoly,
        "p" to t.poly,
        "q" to t.questions,
        "id" to t.remoteId,
    ).filterValues { it != null } // Leave out what a trip doesn't have.

    private fun decodeTrip(m: Map<String, Any?>): TripBackup? {
        val start = (m["s"] as? Number)?.toLong() ?: return null
        val end = (m["e"] as? Number)?.toLong() ?: return null
        return TripBackup(
            start = start,
            end = end,
            mode = (m["m"] as? Number)?.toInt() ?: 0,
            inferredMode = (m["im"] as? Number)?.toInt() ?: 0,
            confidence = (m["c"] as? Number)?.toDouble() ?: 1.0,
            needsReview = m["r"] as? Boolean ?: false,
            pathStatus = (m["ps"] as? Number)?.toInt(),
            basePoly = m["bp"] as? String,
            poly = m["p"] as? String,
            questions = m["q"] as? String,
            remoteId = m["id"] as? String,
        )
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
