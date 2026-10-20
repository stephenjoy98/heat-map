package io.github.stephenjoy98.heatmap.roads

import io.github.stephenjoy98.heatmap.data.Polyline
import io.github.stephenjoy98.heatmap.data.TravelMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A road path from Valhalla: interleaved [lat, lon, ...] plus the name of its main street, if any. */
class RoadPath(val points: DoubleArray, val streetName: String?)

/**
 * Talks to the public Valhalla routing service run by FOSSGIS on OpenStreetMap data (free, no key, fair use:
 * the caller keeps to about one request a second). Only coordinates are sent, never timestamps.
 */
object Valhalla {
    private const val BASE = "https://valhalla1.openstreetmap.de"
    private const val USER_AGENT = "LocationTicker/2 (io.github.stephenjoy98.heatmap)"

    fun costing(mode: TravelMode) = when (mode.kind) {
        TravelMode.Kind.FOOT -> "pedestrian"
        TravelMode.Kind.BIKE -> "bicycle"
        TravelMode.Kind.PUBLIC -> "bus"
        else -> "auto"
    }

    /** Snaps a GPS trace to the road network. Returns null when no reasonable match exists. */
    fun match(lat: DoubleArray, lon: DoubleArray, costing: String): DoubleArray? {
        val shape = JSONArray()
        for (i in lat.indices) shape.put(JSONObject().put("lat", lat[i]).put("lon", lon[i]))
        val body = JSONObject()
            .put("shape", shape)
            .put("costing", costing)
            .put("shape_match", "map_snap")
            .put("trace_options", JSONObject().put("search_radius", 50))
            .put("directions_options", JSONObject().put("units", "kilometers").put("directions_type", "none"))
        val json = post("/trace_route", body) ?: return null
        val legs = json.optJSONObject("trip")?.optJSONArray("legs") ?: return null
        val out = ArrayList<Double>()
        for (i in 0 until legs.length()) {
            val pts = Polyline.decode(legs.getJSONObject(i).optString("shape"), precision = 6)
            out.addAll(pts.toList())
        }
        return out.toDoubleArray().takeIf { it.size >= 4 }
    }

    /** Up to three ways between two points, best first. */
    fun routes(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, costing: String): List<RoadPath> {
        val body = JSONObject()
            .put(
                "locations",
                JSONArray()
                    .put(JSONObject().put("lat", fromLat).put("lon", fromLon))
                    .put(JSONObject().put("lat", toLat).put("lon", toLon)),
            )
            .put("costing", costing)
            .put("alternates", 2)
            .put("directions_options", JSONObject().put("units", "kilometers"))
        val json = post("/route", body) ?: return emptyList()
        val trips = mutableListOf<JSONObject>()
        json.optJSONObject("trip")?.let(trips::add)
        json.optJSONArray("alternates")?.let { alts ->
            for (i in 0 until alts.length()) alts.getJSONObject(i).optJSONObject("trip")?.let(trips::add)
        }
        return trips.mapNotNull { trip -> toPath(trip) }
    }

    private fun toPath(trip: JSONObject): RoadPath? {
        val legs = trip.optJSONArray("legs") ?: return null
        val pts = ArrayList<Double>()
        var bestName: String? = null
        var bestLength = 0.0
        for (i in 0 until legs.length()) {
            val leg = legs.getJSONObject(i)
            pts.addAll(Polyline.decode(leg.optString("shape"), precision = 6).toList())
            val maneuvers = leg.optJSONArray("maneuvers") ?: continue
            for (m in 0 until maneuvers.length()) {
                val man = maneuvers.getJSONObject(m)
                val names = man.optJSONArray("street_names") ?: continue
                val length = man.optDouble("length", 0.0)
                if (names.length() > 0 && length > bestLength) {
                    bestLength = length
                    bestName = names.getString(0)
                }
            }
        }
        return if (pts.size >= 4) RoadPath(pts.toDoubleArray(), bestName) else null
    }

    /** Returns the response, null for "no route/match" answers, and throws on network trouble. */
    private fun post(path: String, body: JSONObject): JSONObject? {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code == 429 || code >= 500) throw IOException("Valhalla busy ($code)")
            if (code != 200) return null // e.g. 400 "no suitable edges near location"
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
