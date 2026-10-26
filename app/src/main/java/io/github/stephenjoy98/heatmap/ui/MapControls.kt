package io.github.stephenjoy98.heatmap.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import io.github.stephenjoy98.heatmap.data.Prefs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import io.github.stephenjoy98.heatmap.data.Units
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.cos
import kotlin.math.sin

@Composable
private fun controlColor() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)

/** Map layers (when [onLayers] is given), a compass and a zoom pill stacked together, for any [AppMap]. */
@Composable
fun MapControls(camera: MapCamera, modifier: Modifier = Modifier, onLayers: (() -> Unit)? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (onLayers != null) {
            LayersButton(onLayers)
            Spacer(Modifier.size(12.dp))
        }
        CompassButton(camera)
        Spacer(Modifier.size(12.dp))
        ZoomButtons(camera)
    }
}

/** Opens the map layers sheet. */
@Composable
fun LayersButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Tip("Map type and 3D buildings", modifier) {
        Surface(onClick = onClick, shape = CircleShape, color = controlColor(), shadowElevation = 3.dp, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(LayersIcon, contentDescription = "Map layers") }
        }
    }
}

/**
 * A compass that follows the map's rotation. Tap it to turn the map north up; once it's north up, a tap tilts
 * the map into 3D, and the next one lays it flat again.
 */
@Composable
fun CompassButton(camera: MapCamera, modifier: Modifier = Modifier) {
    val tilted by Prefs.mapTilted.collectAsStateWithLifecycle()
    val rotated = ((camera.bearing % 360f) + 360f) % 360f in 1f..359f
    val action = when {
        rotated -> "Turn the map back to north"
        tilted -> "Lay the map flat"
        else -> "Tilt the map into 3D"
    }
    Tip(action, modifier) {
        Surface(
            onClick = { if (rotated) camera.northUp() else Prefs.setMapTilted(!tilted) },
            shape = CircleShape,
            color = controlColor(),
            shadowElevation = 3.dp,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Compass: $action" },
        ) {
            CompassNeedle(bearing = camera.bearing, modifier = Modifier.size(48.dp))
        }
    }
}

/**
 * Your speed from GPS in a round badge like the compass, in km/h or mph as chosen in Settings. Below 1 (or with
 * no recent speed) it shrinks to a small dot, a touch smaller than the blue location dot, and grows back into
 * the badge once you're moving. It keeps a 48 dp slot either way, so nothing around it moves.
 */
@Composable
fun Speedometer(speedMs: Float?, modifier: Modifier = Modifier) {
    val perHour = speedMs?.let { it * 3.6 / (if (Units.imperial) 1.609344 else 1.0) }
    val value = perHour?.roundToInt() ?: 0
    val moving = value >= 1
    val unit = if (Units.imperial) "mph" else "km/h"
    val size by animateDpAsState(if (moving) 48.dp else 10.dp, tween(250), label = "speedometer")
    val textAlpha by animateFloatAsState(if (moving) 1f else 0f, tween(if (moving) 250 else 120), label = "speed text")
    val color by animateColorAsState(if (moving) controlColor() else MaterialTheme.colorScheme.onSurfaceVariant, tween(250), label = "speed colour")
    Tip(if (moving) "Your speed, from GPS" else "Your speed shows here once you're moving", modifier) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = color,
                shadowElevation = if (moving) 3.dp else 1.dp,
                modifier = Modifier.size(size).semantics { contentDescription = if (moving) "$value $unit" else "Not moving" },
            ) {
                if (size > 32.dp) {
                    Column(
                        Modifier.fillMaxSize().alpha(textAlpha),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(value.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(unit, fontSize = 9.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Your height above sea level from GPS, in m or ft as chosen in Settings, in a badge like the compass. */
@Composable
fun Altimeter(meters: Double?, modifier: Modifier = Modifier) {
    val value = meters?.let { Units.lengthValue(it).toString() } ?: "–"
    Tip("Your height above sea level, from GPS", modifier) {
        Surface(
            shape = CircleShape,
            color = controlColor(),
            shadowElevation = 3.dp,
            modifier = Modifier.size(48.dp).semantics { contentDescription = if (meters == null) "Height unknown" else "$value ${Units.shortUnit} above sea level" },
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    value,
                    fontSize = if (value.length >= 4) 13.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(Units.shortUnit, fontSize = 9.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Zoom in above zoom out, in one pill. */
@Composable
fun ZoomButtons(camera: MapCamera, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = controlColor(), shadowElevation = 3.dp, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Tip("Zoom in") {
                IconButton(onClick = { camera.zoomIn() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom in")
                }
            }
            HorizontalDivider(Modifier.width(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Tip("Zoom out") {
                IconButton(onClick = { camera.zoomOut() }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Zoom out" }) {
                    // A plain minus sign; the core icon set has no "remove" glyph.
                    Text("−", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/**
 * A compass dial: N, E, S and W with small ticks for NE, SE, SW and NW, fixed in place like a watch face,
 * around a two-tone needle whose red end turns to point at real north as the map rotates.
 */
@Composable
private fun CompassNeedle(bearing: Float, modifier: Modifier = Modifier) {
    val north = Color(0xFFE53935)
    val south = MaterialTheme.colorScheme.onSurfaceVariant
    val pivot = MaterialTheme.colorScheme.surface
    val letters = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
    Canvas(modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val half = size.minDimension * 0.22f
        val waist = size.minDimension * 0.075f
        // The needle turns opposite to the map's rotation, so red points north.
        rotate(-bearing, pivot = center) {
            drawPath(
                Path().apply {
                    moveTo(cx, cy - half)
                    lineTo(cx + waist, cy)
                    lineTo(cx - waist, cy)
                    close()
                },
                north,
            )
            drawPath(
                Path().apply {
                    moveTo(cx, cy + half)
                    lineTo(cx + waist, cy)
                    lineTo(cx - waist, cy)
                    close()
                },
                south,
            )
        }
        drawCircle(pivot, radius = waist * 0.45f, center = center)
        // The dial stays put: N at the top.
        run {
            // The letters sit around the edge, each upright relative to the dial, N in the needle's red.
            val r = size.minDimension * 0.36f
            listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, angle) ->
                val text = measurer.measure(label, style.copy(color = if (label == "N") north else letters))
                val rad = Math.toRadians(angle.toDouble())
                val x = cx + (r * sin(rad)).toFloat()
                val y = cy - (r * cos(rad)).toFloat()
                rotate(angle, pivot = Offset(x, y)) {
                    drawText(text, topLeft = Offset(x - text.size.width / 2f, y - text.size.height / 2f))
                }
            }
            // Small ticks between them for NE, SE, SW and NW.
            val inner = size.minDimension * 0.33f
            val outer = size.minDimension * 0.41f
            for (angle in listOf(45.0, 135.0, 225.0, 315.0)) {
                val rad = Math.toRadians(angle)
                val dx = sin(rad).toFloat()
                val dy = -cos(rad).toFloat()
                drawLine(
                    letters.copy(alpha = 0.7f),
                    Offset(cx + inner * dx, cy + inner * dy),
                    Offset(cx + outer * dx, cy + outer * dy),
                    strokeWidth = 1.2.dp.toPx(),
                )
            }
        }
    }
}

/**
 * A map scale like Google Maps': a bar of a round distance (e.g. 200 m or 1 km) that fades in while the map
 * moves or zooms and fades out a couple of seconds after it settles, in the units chosen in Settings.
 */
@Composable
fun ScaleBar(camera: MapCamera, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(camera.moving) {
        if (camera.moving) {
            show = true
        } else {
            delay(2_000)
            show = false
        }
    }
    val imperial = Units.imperial
    val maxPx = with(LocalDensity.current) { 96.dp.toPx() }
    val density = LocalDensity.current.density
    AnimatedVisibility(visible = show && camera.metersPerPixel > 0f, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        val (meters, label) = niceScale(camera.metersPerPixel * maxPx, imperial)
        val barDp = (meters / camera.metersPerPixel / density).dp
        val ink = MaterialTheme.colorScheme.onSurface
        Surface(shape = RoundedCornerShape(8.dp), color = controlColor(), shadowElevation = 2.dp) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = ink)
                Canvas(Modifier.width(barDp).height(6.dp)) {
                    val stroke = 2.dp.toPx()
                    drawLine(ink, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = stroke)
                    drawLine(ink, Offset(stroke / 2, 0f), Offset(stroke / 2, size.height), strokeWidth = stroke)
                    drawLine(ink, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), strokeWidth = stroke)
                }
            }
        }
    }
}

/** The largest round distance (1, 2 or 5 × a power of ten) up to [maxMeters], in metres, with its label. */
internal fun niceScale(maxMeters: Float, imperial: Boolean): Pair<Float, String> {
    fun roundDown(max: Double): Double {
        val p = 10.0.pow(floor(log10(max)))
        return listOf(5.0, 2.0, 1.0).map { it * p }.first { it <= max }
    }
    fun trim(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(Locale.US, v)
    if (!imperial) {
        val m = roundDown(maxMeters.toDouble())
        return m.toFloat() to (if (m >= 1000) "${trim(m / 1000)} km" else "${trim(m)} m")
    }
    val maxFeet = maxMeters / 0.3048
    return if (maxFeet < 1000) {
        val ft = roundDown(maxFeet)
        (ft * 0.3048).toFloat() to "${trim(ft)} ft"
    } else {
        val mi = roundDown(maxMeters / 1609.344)
        (mi * 1609.344).toFloat() to "${trim(mi)} mi"
    }
}
