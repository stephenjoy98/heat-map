package io.github.stephenjoy98.heatmap.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** How good the current GPS fix is, in the spirit of Fog of World's accuracy readout. */
enum class FixQuality(val label: String, val bars: Int, val color: Color) {
    PERFECT("PERFECT", 4, Color(0xFF34C759)),
    GOOD("GOOD", 3, Color(0xFF43A047)),
    FAIR("FAIR", 2, Color(0xFFF9A825)),
    POOR("POOR", 1, Color(0xFFEF6C00)),
    TOO_WEAK("TOO WEAK", 1, Color(0xFFE53935)),
    NONE("ERROR", 0, Color(0xFFE53935)),
}

/** A fix older than this counts as no signal. */
private const val STALE_MS = 20_000L

fun fixQuality(fix: Location?, now: Long, limitM: Float): FixQuality = when {
    fix == null || !fix.hasAccuracy() || now - fix.time > STALE_MS -> FixQuality.NONE
    fix.accuracy <= 10f -> FixQuality.PERFECT
    fix.accuracy <= 20f -> FixQuality.GOOD
    fix.accuracy <= 50f -> FixQuality.FAIR
    fix.accuracy <= limitM -> FixQuality.POOR
    else -> FixQuality.TOO_WEAK
}

/**
 * Live GPS fixes while the screen is in the foreground: every 2 s while [detailed] (the card is open),
 * otherwise every 5 s just to keep the signal bars current. Stops when the app goes to the background.
 */
@SuppressLint("MissingPermission") // Only requested when hasPermission is true.
@Composable
fun rememberLiveFix(hasPermission: Boolean, detailed: Boolean): Location? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var fix by remember { mutableStateOf<Location?>(null) }
    DisposableEffect(hasPermission, detailed, lifecycleOwner) {
        if (!hasPermission) return@DisposableEffect onDispose { }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, if (detailed) 2_000L else 5_000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { fix = it }
            }
        }
        var running = false
        fun start() {
            if (running) return
            running = true
            client.lastLocation.addOnSuccessListener { last -> if (last != null && fix == null) fix = last }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }
        fun stop() {
            if (!running) return
            running = false
            client.removeLocationUpdates(callback)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }
    return fix
}

/** The quality of [fix], re-checked every couple of seconds so the bars drop when fixes stop arriving. */
@Composable
fun rememberFixQuality(fix: Location?): FixQuality {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            now = System.currentTimeMillis()
        }
    }
    val highAccuracy by Prefs.highAccuracy.collectAsStateWithLifecycle()
    return fixQuality(fix, maxOf(now, fix?.time ?: 0L), LocationTickerService.accuracyLimitM(highAccuracy))
}

/** Satellites the GPS chip can see right now, and how many of them it's using for the fix. */
class SatelliteInfo(val used: Int, val visible: Int, val byConstellation: List<Pair<String, Int>>)

private fun constellationName(type: Int) = when (type) {
    GnssStatus.CONSTELLATION_GPS -> "GPS"
    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
    GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
    GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
    GnssStatus.CONSTELLATION_QZSS -> "QZSS"
    GnssStatus.CONSTELLATION_SBAS -> "SBAS"
    GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
    else -> "Other"
}

/**
 * Live satellite counts while [enabled]. Null when there's no precise-location permission or no update
 * yet. Updates only arrive while the GPS chip is on, which [rememberLiveFix] takes care of.
 */
@SuppressLint("MissingPermission") // Checked below.
@Composable
fun rememberSatellites(enabled: Boolean): SatelliteInfo? {
    val context = LocalContext.current
    var info by remember { mutableStateOf<SatelliteInfo?>(null) }
    DisposableEffect(enabled) {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(LocationManager::class.java)
        if (!enabled || !fine || manager == null) return@DisposableEffect onDispose { }
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                val counts = LinkedHashMap<String, Int>()
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                    val name = constellationName(status.getConstellationType(i))
                    counts[name] = (counts[name] ?: 0) + 1
                }
                info = SatelliteInfo(used, status.satelliteCount, counts.entries.sortedByDescending { it.value }.map { it.key to it.value })
            }

            override fun onStopped() {
                info = null
            }
        }
        @Suppress("DEPRECATION") // The Executor overload needs API 30; this one works from API 24.
        manager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterGnssStatusCallback(callback) }
    }
    return info
}

/** Signal-strength bars for the accuracy button: more filled bars, better fix. */
@Composable
fun SignalBars(quality: FixQuality, modifier: Modifier = Modifier) {
    val on = if (quality == FixQuality.NONE) MaterialTheme.colorScheme.error else quality.color
    val off = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val gap = size.width * 0.08f
        val w = (size.width - gap * 3) / 4
        for (i in 0 until 4) {
            val h = size.height * (0.35f + 0.65f * (i + 1) / 4)
            drawRoundRect(
                if (i < quality.bars) on else off,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w * 0.3f),
            )
        }
    }
}

/** The map button: signal bars that open the accuracy card. */
@Composable
fun AccuracyButton(quality: FixQuality, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        shadowElevation = 3.dp,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { SignalBars(quality, Modifier.size(width = 20.dp, height = 16.dp)) }
    }
}

private val clockFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * Live accuracy readout: metres, a quality badge, coordinates, satellites and when the fix was taken. A
 * speech-bubble tail points down at the button that opened it, [tailInset] from the left edge (or the right
 * edge, when [tailAtStart] is false).
 */
@Composable
fun AccuracyCard(fix: Location?, modifier: Modifier = Modifier, tailInset: Dp = 12.dp, tailAtStart: Boolean = true) {
    val satellites = rememberSatellites(enabled = true)
    val context = LocalContext.current
    val precise = remember { context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val highAccuracy by Prefs.highAccuracy.collectAsStateWithLifecycle()
    val limit = LocationTickerService.accuracyLimitM(highAccuracy)
    val quality = fixQuality(fix, now, limit)
    var explaining by remember { mutableStateOf(false) }
    val mono = FontFamily.Monospace
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val cardColor = MaterialTheme.colorScheme.surfaceContainerLowest
    Column(modifier, horizontalAlignment = if (tailAtStart) Alignment.Start else Alignment.End) {
        Surface(
            modifier = Modifier.widthIn(max = 360.dp),
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            shadowElevation = 4.dp,
        ) {
            Row(Modifier.height(IntrinsicSize.Min).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
                    Text("ACCURACY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = muted)
                    Text(
                        if (quality == FixQuality.NONE) "--" else fix!!.accuracy.roundToInt().toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("METERS", style = MaterialTheme.typography.labelSmall, color = muted)
                }
                VerticalDivider(Modifier.fillMaxHeight().padding(horizontal = 12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.background(quality.color, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(quality.label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { explaining = true }, modifier = Modifier.size(36.dp)) {
                            Box(
                                Modifier.size(26.dp).border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("?", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val current = fix?.takeIf { quality != FixQuality.NONE }
                        // Points the way you're heading when moving, otherwise north.
                        val heading = current?.takeIf { it.hasBearing() && it.hasSpeed() && it.speed > 0.5f }?.bearing ?: 0f
                        HeadingArrow(heading, enabled = current != null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            current?.let { String.format(Locale.US, "%.5f,%.5f", it.latitude, it.longitude) } ?: "Not available",
                            fontFamily = mono,
                            fontSize = 13.sp,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SatelliteGlyph(Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            satellites?.let { "${it.used} in use · ${it.visible} in view" }
                                ?: if (precise) "Searching for satellites…" else "Satellites need precise location",
                            fontFamily = mono,
                            fontSize = 13.sp,
                        )
                    }
                    satellites?.takeIf { it.visible > 0 }?.let { sats ->
                        Text(
                            sats.byConstellation.joinToString("  ") { (name, n) -> "$name $n" },
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClockGlyph(Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Instant.ofEpochMilli(fix?.time ?: now).atZone(ZoneId.systemDefault()).format(clockFormat),
                            fontFamily = mono,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        // Speech-bubble tail pointing at the accuracy button.
        Canvas(
            Modifier
                .padding(start = if (tailAtStart) tailInset else 0.dp, end = if (tailAtStart) 0.dp else tailInset)
                .size(width = 18.dp, height = 9.dp),
        ) {
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2, size.height)
                    close()
                },
                cardColor,
            )
        }
    }

    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text("About accuracy") },
            text = {
                Text(
                    "Accuracy is the radius around the reported position within which your true location very " +
                        "probably is, so a smaller number means a more precise dot. More satellites in use usually " +
                        "means better accuracy; buildings, trees and being indoors block them.\n\n" +
                        "Good is within 20 m, fair within 50 m. Location Ticker only records fixes within " +
                        "${limit.roundToInt()} m" +
                        (if (highAccuracy) "" else " (100 m with High accuracy on in Settings)") +
                        "; anything less precise is skipped so it can't put stray dots on your map."
                )
            },
            confirmButton = { TextButton(onClick = { explaining = false }) { Text("OK") } },
        )
    }
}

/** A navigation arrow pointing along [bearing] (degrees from north), greyed out when there's no fix. */
@Composable
private fun HeadingArrow(bearing: Float, enabled: Boolean, modifier: Modifier = Modifier) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    Canvas(modifier.rotate(bearing)) {
        val w = size.width
        val h = size.height
        drawPath(
            Path().apply {
                moveTo(w / 2, 0f)
                lineTo(w * 0.9f, h)
                lineTo(w / 2, h * 0.72f)
                lineTo(w * 0.1f, h)
                close()
            },
            color,
        )
    }
}

/** A small satellite: a body with two solar panels. */
@Composable
private fun SatelliteGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.rotate(-45f)) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, topLeft = Offset(w * 0.38f, h * 0.3f), size = Size(w * 0.24f, h * 0.4f), cornerRadius = CornerRadius(w * 0.05f))
        drawRect(color, topLeft = Offset(0f, h * 0.38f), size = Size(w * 0.3f, h * 0.24f))
        drawRect(color, topLeft = Offset(w * 0.7f, h * 0.38f), size = Size(w * 0.3f, h * 0.24f))
        drawLine(color, Offset(w * 0.3f, h / 2), Offset(w * 0.7f, h / 2), strokeWidth = w * 0.06f)
    }
}

@Composable
private fun ClockGlyph(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        val stroke = r * 0.22f
        drawCircle(color, radius = r - stroke / 2, center = c, style = Stroke(stroke))
        drawLine(color, c, Offset(c.x, c.y - r * 0.55f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, c, Offset(c.x + r * 0.4f, c.y), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
