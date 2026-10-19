package io.github.stephenjoy98.heatmap.cloud

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import io.github.stephenjoy98.heatmap.data.Cells
import io.github.stephenjoy98.heatmap.data.FriendRoute
import io.github.stephenjoy98.heatmap.data.Polyline
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.TravelMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

data class Friend(
    val uid: String,
    val name: String,
    val kmByMode: Map<TravelMode, Double>,
    val cells: Long,
    val cities: List<String>,
) {
    val totalKm: Double get() = kmByMode.values.sum()
}

/**
 * Friends are added with each other's friend code (typed, shared as a link, or scanned from a QR code).
 * Knowing the code is the consent: adding links both accounts, and Firestore rules then let each read the
 * other's profile and shared routes. Shared routes carry the path and travel mode, never timestamps.
 */
object Friends {
    private val db get() = FirebaseFirestore.getInstance()
    private fun me() = FirebaseAuth.getInstance().currentUser?.uid ?: error("Sign in first")
    private fun user(uid: String) = db.collection("users").document(uid)

    suspend fun list(): List<Friend> = coroutineScope {
        val uids = user(me()).collection("friends").get().await().documents.map { it.id }
        uids.map { uid -> async { runCatching { profile(uid) }.getOrNull() } }.awaitAll().filterNotNull()
    }

    private suspend fun profile(uid: String): Friend {
        val doc = user(uid).get().await()
        @Suppress("UNCHECKED_CAST")
        val km = (doc.get("km") as? Map<String, Any>).orEmpty()
        return Friend(
            uid = uid,
            name = doc.getString("name") ?: "Friend",
            kmByMode = km.mapNotNull { (k, v) ->
                val mode = TravelMode.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
                mode to ((v as? Number)?.toDouble() ?: 0.0)
            }.toMap(),
            cells = doc.getLong("cells") ?: 0L,
            cities = (doc.get("cities") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        )
    }

    /** Adds the friend whose code this is. Returns their name. */
    suspend fun addByCode(code: String): String {
        val myUid = me()
        val uid = db.collection("friendCodes").document(code).get().await().getString("uid")
            ?: error("No one has the ID ${Account.formatCode(code)}")
        require(uid != myUid) { "That's your own ID" }
        val now = FieldValue.serverTimestamp()
        db.batch()
            .set(user(myUid).collection("friends").document(uid), mapOf("code" to code, "since" to now))
            // The rules accept this write into their list because it carries their code.
            .set(user(uid).collection("friends").document(myUid), mapOf("code" to code, "since" to now))
            .commit()
            .await()
        return runCatching { profile(uid).name }.getOrDefault("your friend")
    }

    suspend fun remove(context: Context, uid: String) {
        val myUid = me()
        db.batch()
            .delete(user(myUid).collection("friends").document(uid))
            .delete(user(uid).collection("friends").document(myUid))
            .commit()
            .await()
        PointStore.get(context).deleteFriendRoutes(uid)
    }

    /** Downloads a friend's shared routes into the local cache. Returns how many there are. */
    suspend fun downloadRoutes(context: Context, uid: String): Int {
        val docs = user(uid).collection("routes").get().await().documents
        val routes = docs.mapNotNull { d ->
            val poly = d.getString("p") ?: return@mapNotNull null
            FriendRoute(d.id, TravelMode.fromId(d.getLong("m")?.toInt() ?: 0), poly)
        }
        PointStore.get(context).replaceFriendRoutes(uid, routes)
        return routes.size
    }

    /** Uploads this user's profile stats and any new or changed routes (or removes them if sharing is off). */
    suspend fun syncMine(context: Context) {
        val uid = me()
        val store = PointStore.get(context)
        val routes = user(uid).collection("routes")

        if (!Prefs.shareRoutes.value) {
            val existing = routes.get().await().documents
            existing.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            store.markAllForUpload()
        } else {
            while (true) {
                val trips = store.tripsNotUploaded(200)
                if (trips.isEmpty()) break
                val batch = db.batch()
                val ids = trips.map { trip ->
                    val path = store.tripDisplayPath(trip.id)
                    val n = path.size / 2
                    // Cap very long trips at ~1500 points to keep documents small.
                    val step = maxOf(1, n / 1500)
                    val idx = (0 until n step step).toMutableList().apply { if (isNotEmpty() && last() != n - 1) add(n - 1) }
                    val lat = DoubleArray(idx.size) { path[idx[it] * 2] }
                    val lon = DoubleArray(idx.size) { path[idx[it] * 2 + 1] }
                    val ref = trip.remoteId?.let { routes.document(it) } ?: routes.document()
                    batch.set(ref, mapOf("m" to trip.mode.id, "p" to Polyline.encode(lat, lon), "d" to trip.distanceM))
                    trip.id to ref.id
                }
                batch.commit().await()
                ids.forEach { (id, remote) -> store.markUploaded(id, remote) }
            }
        }

        val km = store.distanceByMode(0, Long.MAX_VALUE).mapKeys { it.key.name }.mapValues { it.value / 1000.0 }
        val cities = topCities(store)
        user(uid).set(
            mapOf(
                "km" to km,
                "cells" to store.cellsVisited(0, Long.MAX_VALUE),
                "cities" to cities,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    private fun topCities(store: PointStore): List<String> =
        store.coarseAreas(0, Long.MAX_VALUE, 200)
            .mapNotNull { store.place(it.key)?.city }
            .distinct()
            .take(30)

    /** The ~220 m cells a friend's routes pass through. */
    fun friendCells(context: Context, uid: String): HashSet<Long> {
        val out = HashSet<Long>()
        for (route in PointStore.get(context).friendRoutes(uid, null, TravelMode.entries.toSet())) {
            val pts = Polyline.decode(route.poly)
            var i = 2
            if (pts.size == 2) out += Cells.key(pts[0], pts[1])
            while (i + 1 < pts.size) {
                Cells.addSegment(out, pts[i - 2], pts[i - 1], pts[i], pts[i + 1])
                i += 2
            }
        }
        return out
    }
}
