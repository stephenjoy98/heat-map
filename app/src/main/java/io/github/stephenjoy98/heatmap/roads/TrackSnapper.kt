package io.github.stephenjoy98.heatmap.roads

import io.github.stephenjoy98.heatmap.data.TravelMode
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Snaps trains and boats (travel modes of the "Train or boat" kind, like SkyTrain and SeaBus) to the lines they
 * actually run on: OpenStreetMap's railways, or its ferry routes for boats. The lines around a trip are fetched
 * from the public Overpass API (free, no key; only the area's corners are sent) and the trip is matched to them
 * with [TrackNetwork].
 */
/** Where a snap has got to, step by step, so a failure can say which step it was (Fix route shows these). */
interface SnapLog {
    fun step(text: String) {}
    fun fail(text: String) {}

    companion object {
        val NONE = object : SnapLog {}
    }
}

object TrackSnapper {
    private const val OVERPASS = "https://overpass-api.de/api/interpreter"
    private const val USER_AGENT = "LocationTicker/2 (io.github.stephenjoy98.heatmap)"
    // How far outside the trip to fetch lines, in degrees (~1 km), so a line that bends away still joins up.
    private const val PAD_DEG = 0.01

    enum class Network(val maxSnapM: Double, val lines: String) {
        RAIL(120.0, "railways"),
        // Ferry routes are drawn between terminals and boats wander a bit: allow more room.
        FERRY(350.0, "ferry routes"),
    }

    /** The network a mode runs on: ferry routes for boats (by name), rails for every other train-or-boat mode. */
    fun networkFor(mode: TravelMode): Network? {
        if (mode.kind != TravelMode.Kind.TRACK) return null
        val name = mode.fullLabel.lowercase()
        return if (listOf("sea", "ferry", "boat", "ship", "water").any { it in name }) Network.FERRY else Network.RAIL
    }

    /** A recorded trip on the lines, or null if it doesn't fit them. Throws IOException when offline. */
    fun snap(lat: DoubleArray, lon: DoubleArray, network: Network, log: SnapLog = SnapLog.NONE): DoubleArray? {
        if (lat.size < 2) return null
        val net = fetch(lat.min(), lat.max(), lon.min(), lon.max(), network, log) ?: return null
        log.step("Matching ${lat.size} dots to the ${network.lines}")
        return net.snap(lat, lon, network.maxSnapM).also {
            if (it == null) log.fail("The dots don't follow any of the ${network.lines} closely enough (within ${network.maxSnapM.toInt()} m), or the lines don't join up")
        }
    }

    /** The way along the lines through [stops] (Fix route on a train or boat trip). Throws IOException when offline. */
    fun route(stops: List<Pair<Double, Double>>, network: Network, log: SnapLog = SnapLog.NONE): DoubleArray? {
        if (stops.size < 2) return null
        val net = fetch(stops.minOf { it.first }, stops.maxOf { it.first }, stops.minOf { it.second }, stops.maxOf { it.second }, network, log)
            ?: return null
        log.step("Finding the way along the ${network.lines} through ${stops.size} points")
        return net.route(stops, network.maxSnapM).also {
            if (it == null) log.fail("A point is more than ${network.maxSnapM.toInt()} m from the ${network.lines}, or they don't join up")
        }
    }

    private fun fetch(south: Double, north: Double, west: Double, east: Double, network: Network, log: SnapLog): TrackNetwork? {
        log.step("Asking OpenStreetMap (Overpass API) for ${network.lines} around the trip")
        val box = "${south - PAD_DEG},${west - PAD_DEG},${north + PAD_DEG},${east + PAD_DEG}"
        val filter = when (network) {
            Network.RAIL -> "way[\"railway\"~\"^(rail|subway|light_rail|monorail|narrow_gauge|tram|funicular)$\"]($box);"
            Network.FERRY -> "way[\"route\"=\"ferry\"]($box);"
        }
        val query = "[out:json][timeout:25];($filter);out body;>;out skel qt;"
        val conn = (URL(OVERPASS).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 40_000
            doOutput = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            val code = conn.responseCode
            // Busy or rate-limited: try again later, like an offline phone.
            if (code == 429 || code >= 500) throw IOException("Overpass is busy (HTTP $code)")
            if (code != 200) {
                log.fail("Overpass answered HTTP $code")
                return null
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val elements = json.optJSONArray("elements") ?: return null
            val nodes = HashMap<Long, Pair<Double, Double>>()
            val ways = ArrayList<LongArray>()
            for (i in 0 until elements.length()) {
                val e = elements.getJSONObject(i)
                when (e.optString("type")) {
                    "node" -> nodes[e.getLong("id")] = e.getDouble("lat") to e.getDouble("lon")
                    "way" -> e.optJSONArray("nodes")?.let { ids -> ways += LongArray(ids.length()) { ids.getLong(it) } }
                }
            }
            if (ways.isEmpty()) {
                log.fail("No ${network.lines} are mapped around this trip")
                return null
            }
            log.step("Got ${ways.size} lines with ${nodes.size} points; building the network")
            return TrackNetwork(nodes, ways)
        } finally {
            conn.disconnect()
        }
    }
}
