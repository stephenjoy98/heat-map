package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.Canvas
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
    Surface(onClick = onClick, shape = CircleShape, color = controlColor(), shadowElevation = 3.dp, modifier = modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(LayersIcon, contentDescription = "Map layers") }
    }
}

/** A compass that follows the map's rotation; tap to turn the map north up. */
@Composable
fun CompassButton(camera: MapCamera, modifier: Modifier = Modifier) {
    Surface(
        onClick = { camera.northUp() },
        shape = CircleShape,
        color = controlColor(),
        shadowElevation = 3.dp,
        modifier = modifier.size(48.dp).semantics { contentDescription = "Compass: turn map north up" },
    ) {
        CompassNeedle(bearing = camera.bearing, modifier = Modifier.size(48.dp))
    }
}

/** Zoom in above zoom out, in one pill. */
@Composable
fun ZoomButtons(camera: MapCamera, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = controlColor(), shadowElevation = 3.dp, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { camera.zoomIn() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom in")
            }
            HorizontalDivider(Modifier.width(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
            IconButton(onClick = { camera.zoomOut() }, modifier = Modifier.size(48.dp)) {
                // A plain minus sign; the core icon set has no "remove" glyph.
                Text("−", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
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
