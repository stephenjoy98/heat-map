package io.github.stephenjoy98.heatmap.ui

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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/** A compass that follows the map's rotation; tap to turn the map north up. */
@Composable
fun CompassButton(camera: MapCamera, modifier: Modifier = Modifier) {
    Tip("Turn the map back to north", modifier) {
        Surface(
            onClick = { camera.northUp() },
            shape = CircleShape,
            color = controlColor(),
            shadowElevation = 3.dp,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Compass: turn map north up" },
        ) {
            CompassNeedle(bearing = camera.bearing, modifier = Modifier.size(48.dp))
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

/** A two-tone needle: red points north, so it turns opposite to the map's rotation. */
@Composable
private fun CompassNeedle(bearing: Float, modifier: Modifier = Modifier) {
    val north = Color(0xFFE53935)
    val south = MaterialTheme.colorScheme.onSurfaceVariant
    val pivot = MaterialTheme.colorScheme.surface
    Canvas(modifier.rotate(-bearing)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val half = size.minDimension * 0.32f
        val waist = size.minDimension * 0.09f
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
        drawCircle(pivot, radius = waist * 0.45f, center = center)
    }
}

/**
 * A map scale like Google Maps': a bar of a round distance (e.g. 200 m or 1 km) that fades in while the map
 * moves or zooms and fades out a couple of seconds after it settles. Feet and miles where those are usual.
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
    val imperial = remember { Locale.getDefault().country in setOf("US", "LR", "MM") }
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
