package io.github.stephenjoy98.heatmap.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stephenjoy98.heatmap.data.Units
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import kotlin.math.cos

/**
 * Other ways to tell your height, to check the GPS one against: the ground height from the terrain data the map
 * already draws hillshading from (AWS Terrarium tiles), and the phone's barometer.
 */
object AltitudeSources {
    private const val TERRAIN_URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/%d/%d/%d.png"
    private const val ZOOM = 15
    private val tiles = object : LinkedHashMap<String, IntArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?) = size > 8
    }

    /** The ground's height above sea level at a spot, in metres, or null when offline. Runs off the main thread. */
    suspend fun groundHeight(lat: Double, lon: Double): Double? = withContext(Dispatchers.IO) {
        runCatching {
            val n = 2.0.pow(ZOOM)
            val fx = (lon + 180) / 360 * n
            val latRad = Math.toRadians(lat)
            val fy = (1 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2 * n
            val x = floor(fx).toInt()
            val y = floor(fy).toInt()
            val key = "$x/$y"
            val pixels = synchronized(tiles) { tiles[key] } ?: run {
                val conn = URL(TERRAIN_URL.format(ZOOM, x, y)).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                val bmp = try {
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    conn.disconnect()
                } ?: return@runCatching null
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                bmp.recycle()
                synchronized(tiles) { tiles[key] = px }
                px
            }
            val size = kotlin.math.sqrt(pixels.size.toDouble()).toInt()
            val px = ((fx - x) * size).toInt().coerceIn(0, size - 1)
            val py = ((fy - y) * size).toInt().coerceIn(0, size - 1)
            val c = pixels[py * size + px]
            // Terrarium: height = R × 256 + G + B / 256 − 32768.
            ((c shr 16) and 0xFF) * 256.0 + ((c shr 8) and 0xFF) + (c and 0xFF) / 256.0 - 32768.0
        }.getOrNull()
    }

    /**
     * The barometer's reference: the sea-level pressure that made it read the ground height (or the GPS one) when
     * it was set, so later readings show how much higher or lower you are. Air pressure drifts with the weather,
     * so it's set again after [BARO_REFRESH_MS].
     */
    var seaLevelPressure: Float? = null
        private set
    var setAt = 0L
        private set
    private const val BARO_REFRESH_MS = 3 * 60 * 60_000L

    /** Whether the reference came from the ground height (better) rather than the GPS one. */
    var fromGround = false
        private set

    fun calibrate(pressure: Float, heightM: Double, againstGround: Boolean, now: Long = System.currentTimeMillis()) {
        // Keep a recent reference, unless it was the GPS height and the ground height has come in since.
        val fresh = seaLevelPressure != null && now - setAt < BARO_REFRESH_MS
        if (fresh && (fromGround || !againstGround)) return
        fromGround = againstGround
        // The standard atmosphere, as SensorManager.getAltitude, turned round: p0 = p / (1 − h / 44330)^5.255.
        seaLevelPressure = (pressure / (1 - heightM / 44330.0).pow(5.255)).toFloat()
        setAt = now
    }

    fun hasBarometer(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
}

/** "Check your height": the GPS height next to the ground height and the barometer, and whether they agree. */
@Composable
fun AltitudeCheckSheet(fix: Location?, onClose: () -> Unit) {
    val context = LocalContext.current
    val gps = fix?.let { LocationTickerService.altitudeOf(it) }
    val vAcc = fix?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.hasVerticalAccuracy() }?.verticalAccuracyMeters
    // Only fetch again when you've moved a few tens of metres.
    val spot = fix?.let { Math.round(it.latitude * 2000) to Math.round(it.longitude * 2000) }
    val ground by produceState<Double?>(null, spot) {
        value = fix?.let { AltitudeSources.groundHeight(it.latitude, it.longitude) }
    }
    var pressure by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { pressure = e.values[0] }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    val p = pressure
    val groundNow = ground
    if (p != null && groundNow != null) AltitudeSources.calibrate(p, groundNow, againstGround = true)
    else if (p != null && gps != null) AltitudeSources.calibrate(p, gps, againstGround = false)
    val p0 = AltitudeSources.seaLevelPressure
    val baro = if (p != null && p0 != null) SensorManager.getAltitude(p0, p).toDouble() else null

    SlideUpSheet(onClosed = onClose) { _ ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Check your height", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val ageS = fix?.let { ((System.currentTimeMillis() - it.time) / 1000).coerceAtLeast(0) }
            HeightRow(
                "GPS (satellites)",
                gps,
                when {
                    fix == null -> "No GPS fix yet"
                    else -> listOfNotNull(vAcc?.let { "±${Units.length(it.toDouble())}" }, "from the last fix ${ageS}s ago").joinToString(", ")
                },
            )
            HeightRow(
                "Ground here (map terrain)",
                ground,
                if (fix == null) "Needs a position" else if (ground == null) "Loading the terrain data… (needs a connection)" else "From the terrain data the map uses",
            )
            if (AltitudeSources.hasBarometer(context)) {
                val at = AltitudeSources.setAt.takeIf { it > 0 }?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
                HeightRow(
                    "Barometer (air pressure)",
                    baro,
                    if (p == null) "Reading the sensor…"
                    else String.format(java.util.Locale.US, "%.1f hPa", p) + (at?.let { ", set against the ${if (AltitudeSources.fromGround) "ground" else "GPS"} height at $it" } ?: ""),
                )
            }
            val groundM = ground
            val values = listOfNotNull(gps, groundM, baro)
            if (values.size >= 2) {
                val spread = values.max() - values.min()
                val ok = spread <= AGREE_M
                Surface(shape = RoundedCornerShape(16.dp), color = if (ok) Color(0xFF203A26) else Color(0xFF3D3016), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(if (ok) Color(0xFF66BB6A) else Color(0xFFFFB300), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (ok) "They agree within ${Units.length(spread)}" else "They're ${Units.length(spread)} apart",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                if (ok) "Your GPS height looks right."
                                else if (gps != null && groundM != null && abs(gps - groundM) > AGREE_M)
                                    "GPS heights are often off indoors, under cover or with few satellites in view; the ground height is usually closer. Upstairs or on a bridge, you're above the ground anyway."
                                else "The barometer drifts with the weather; it's set again every few hours.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
            // Phones whose Android can't convert GPS heights to sea level (before 14): match them to the ground by hand.
            if (!io.github.stephenjoy98.heatmap.data.Geoid.supported) MatchGround(fix, groundM)
            Text(
                "The ground height comes from the same terrain tiles the map's hillshading uses; nothing else is sent anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val AGREE_M = 25.0

/**
 * "Match the ground height", for phones without Android's sea-level conversion: GPS heights are above the
 * ellipsoid, metres off sea level, so standing outside on open ground, a tap sets the difference to the ground
 * height; the altimeter and new dots use it from then on.
 */
@Composable
private fun MatchGround(fix: Location?, ground: Double?) {
    var offset by remember { mutableStateOf(io.github.stephenjoy98.heatmap.data.Prefs.altitudeOffset) }
    var at by remember { mutableStateOf(io.github.stephenjoy98.heatmap.data.Prefs.altitudeOffsetAt) }
    val raw = fix?.takeIf { it.hasAltitude() }?.altitude
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Match the ground height", style = MaterialTheme.typography.titleSmall)
            Text(
                "GPS measures height above a smooth shape of the Earth, not above sea level; where you are they differ by " +
                    "several metres (about 17 m around Vancouver). Android 14 and later correct this by themselves; this " +
                    "phone can't, so stand outside on open ground and tap below to line GPS up with the ground height. " +
                    "It's used for the altimeter and new dots from now on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (at > 0) {
                val sign = if (offset >= 0) "+" else "−"
                Text(
                    "Matched to the ground ($sign${Units.length(abs(offset))}) on " +
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val next = if (raw != null && ground != null) ground - raw else null
                androidx.compose.material3.Button(
                    enabled = next != null,
                    onClick = {
                        next?.let {
                            io.github.stephenjoy98.heatmap.data.Prefs.setAltitudeOffset(it)
                            offset = it
                            at = io.github.stephenjoy98.heatmap.data.Prefs.altitudeOffsetAt
                        }
                    },
                ) {
                    Text(
                        next?.let { "Match ground (${if (it >= 0) "+" else "−"}${Units.length(abs(it))})" }
                            ?: "Waiting for GPS and ground height…",
                    )
                }
                if (at > 0) {
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.TextButton(onClick = {
                        io.github.stephenjoy98.heatmap.data.Prefs.setAltitudeOffset(0.0)
                        offset = 0.0
                        at = 0L
                    }) { Text("Reset") }
                }
            }
        }
    }
}

@Composable
private fun HeightRow(title: String, meters: Double?, detail: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text(meters?.let { Units.length(it) } ?: "–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
