package io.github.stephenjoy98.heatmap.data

import org.json.JSONArray
import org.json.JSONObject

enum class PlaceKind(val label: String) { HOME("Home"), WORK("Work"), SCHOOL("School"), OTHER("Other") }

/**
 * A place you spend a lot of time (home, work, school…): a circle of [radiusM] around a point. While you're
 * inside it and not moving, recording slows to [intervalSec]; walking or driving out speeds it up again.
 */
data class SavedPlace(
    val id: Long,
    val name: String,
    val kind: PlaceKind,
    val lat: Double,
    val lon: Double,
    val radiusM: Int,
    val intervalSec: Int,
) {
    fun distanceTo(lat: Double, lon: Double): Double = TrackRules.distanceMeters(this.lat, this.lon, lat, lon)

    companion object {
        const val DEFAULT_RADIUS_M = 150
        const val DEFAULT_INTERVAL_SEC = 300

        fun encode(places: List<SavedPlace>): String = JSONArray().apply {
            places.forEach { p ->
                put(
                    JSONObject().put("id", p.id).put("name", p.name).put("kind", p.kind.name).put("lat", p.lat)
                        .put("lon", p.lon).put("radius", p.radiusM).put("interval", p.intervalSec)
                )
            }
        }.toString()

        fun decode(json: String?): List<SavedPlace> = runCatching {
            val a = JSONArray(json ?: "[]")
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                SavedPlace(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    kind = PlaceKind.entries.firstOrNull { it.name == o.optString("kind") } ?: PlaceKind.OTHER,
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    radiusM = o.optInt("radius", DEFAULT_RADIUS_M),
                    intervalSec = o.optInt("interval", DEFAULT_INTERVAL_SEC),
                )
            }
        }.getOrDefault(emptyList())
    }
}

/** Which saved place a position is in. */
object PlaceZones {
    // Once inside, a place only lets go this far past its edge, so GPS wobble at the edge doesn't flip-flop.
    private const val LEAVE_MARGIN_M = 30.0

    /** The place containing the position: [current] while still within its edge plus a margin, else the nearest. */
    fun find(places: List<SavedPlace>, lat: Double, lon: Double, current: SavedPlace?): SavedPlace? {
        val stay = current?.let { c -> places.firstOrNull { it.id == c.id } }
        if (stay != null && stay.distanceTo(lat, lon) <= stay.radiusM + LEAVE_MARGIN_M) return stay
        return places.filter { it.distanceTo(lat, lon) <= it.radiusM }.minByOrNull { it.distanceTo(lat, lon) }
    }
}
