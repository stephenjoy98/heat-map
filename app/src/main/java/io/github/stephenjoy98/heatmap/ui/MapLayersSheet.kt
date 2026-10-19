package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.BorderStroke
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import io.github.stephenjoy98.heatmap.map.VectorMaps
import java.util.Locale
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.data.MapLayerType
import io.github.stephenjoy98.heatmap.data.Prefs

/** Material's "layers" icon (two stacked sheets); the core icon set doesn't include it. */
val LayersIcon: ImageVector by lazy {
    ImageVector.Builder("Layers", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes(
                "M11.99,18.54l-7.37,-5.73L3,14.07l9,7 9,-7 -1.63,-1.27 -7.38,5.74zM12,16l7.36,-5.73L21,9l-9,-7 -9,7 1.63,1.27L12,16z"
            ),
            fill = SolidColor(Color.Black),
        )
    }.build()
}

/**
 * Google Maps-style sheet: pick the map type, and turn map details such as 3D buildings on or off. The map
 * types show live previews of the actual OpenStreetMap styles around [center] (your latest position).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLayersSheet(onDismiss: () -> Unit, center: Pair<Double, Double>? = null) {
    val layer by Prefs.mapLayer.collectAsStateWithLifecycle()
    val buildings by Prefs.buildings3d.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Map type", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LayerTile("Default", layer == MapLayerType.DEFAULT, { Prefs.setMapLayer(MapLayerType.DEFAULT) }, preview = {
                    MapPreview(terrain = false, center = center, fallback = it)
                }) { drawDefaultArt(it) }
                LayerTile("Terrain", layer == MapLayerType.TERRAIN, { Prefs.setMapLayer(MapLayerType.TERRAIN) }, preview = {
                    MapPreview(terrain = true, center = center, fallback = it)
                }) { drawTerrainArt(it) }
            }
            Spacer(Modifier.height(20.dp))
            Text("Map details", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LayerTile("3D buildings", buildings, { Prefs.setBuildings3d(!buildings) }) { drawBuildingsArt(it) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "3D buildings tilt the map; drag up or down with two fingers to change the angle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Colours for the little illustrations, so they read in both themes. */
private class ArtColors(val ground: Color, val line: Color, val accent: Color, val selected: Boolean)

@Composable
private fun LayerTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    // A picture for the tile; it's handed the drawn icon to show until (or instead of) the picture.
    preview: (@Composable (fallback: @Composable () -> Unit) -> Unit)? = null,
    art: DrawScope.(ArtColors) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = ArtColors(
        ground = scheme.surfaceContainerHighest,
        line = scheme.outline,
        accent = if (selected) scheme.primary else scheme.onSurfaceVariant,
        selected = selected,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp).semantics {
            role = Role.Checkbox
            this.selected = selected
        },
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = colors.ground,
            border = if (selected) BorderStroke(2.dp, scheme.primary) else BorderStroke(1.dp, scheme.outlineVariant),
            modifier = Modifier.size(64.dp),
        ) {
            val drawn: @Composable () -> Unit = { Canvas(Modifier.size(64.dp)) { art(colors) } }
            if (preview != null) preview(drawn) else drawn()
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.primary else scheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

private fun DrawScope.drawDefaultArt(c: ArtColors) {
    val w = size.width
    val h = size.height
    val road = w * 0.07f
    drawLine(c.line, Offset(0f, h * 0.62f), Offset(w, h * 0.38f), strokeWidth = road)
    drawLine(c.line, Offset(w * 0.35f, 0f), Offset(w * 0.55f, h), strokeWidth = road * 0.7f)
    drawLine(c.accent, Offset(0f, h * 0.2f), Offset(w * 0.6f, h * 0.05f), strokeWidth = road * 0.5f)
    drawCircle(c.accent, radius = w * 0.08f, center = Offset(w * 0.7f, h * 0.7f))
}

private fun DrawScope.drawTerrainArt(c: ArtColors) {
    val w = size.width
    val h = size.height
    val hills = Path().apply {
        moveTo(0f, h)
        lineTo(w * 0.38f, h * 0.3f)
        lineTo(w * 0.58f, h * 0.62f)
        lineTo(w * 0.75f, h * 0.42f)
        lineTo(w, h * 0.8f)
        lineTo(w, h)
        close()
    }
    drawPath(hills, c.accent.copy(alpha = 0.35f))
    drawPath(hills, c.accent, style = Stroke(width = w * 0.04f))
    // Contour lines.
    for (i in 1..2) {
        drawArc(
            c.line,
            startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(w * (0.18f - i * 0.04f), h * (0.42f + i * 0.08f)),
            size = androidx.compose.ui.geometry.Size(w * (0.4f + i * 0.08f), h * 0.5f),
            style = Stroke(width = w * 0.025f),
        )
    }
}

private fun DrawScope.drawBuildingsArt(c: ArtColors) {
    val w = size.width
    val h = size.height
    fun box(x: Float, top: Float, bw: Float) {
        val depth = bw * 0.35f
        val front = Path().apply {
            moveTo(x, h * 0.85f); lineTo(x, top); lineTo(x + bw, top); lineTo(x + bw, h * 0.85f); close()
        }
        val side = Path().apply {
            moveTo(x + bw, h * 0.85f); lineTo(x + bw, top); lineTo(x + bw + depth, top - depth); lineTo(x + bw + depth, h * 0.85f - depth); close()
        }
        val roof = Path().apply {
            moveTo(x, top); lineTo(x + depth, top - depth); lineTo(x + bw + depth, top - depth); lineTo(x + bw, top); close()
        }
        drawPath(side, c.accent.copy(alpha = 0.55f))
        drawPath(roof, c.accent.copy(alpha = 0.3f))
        drawPath(front, c.accent.copy(alpha = if (c.selected) 0.9f else 0.75f))
    }
    box(w * 0.14f, h * 0.45f, w * 0.26f)
    box(w * 0.5f, h * 0.28f, w * 0.24f)
}

// Snapshots already rendered this run, by style and rounded position.
private val previewCache = HashMap<String, Bitmap>()

// Turns the light map grey and inverts it, close to the app's dark map, for the dark theme.
private val darkPreview = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.254f, -0.499f, -0.097f, 0f, 216f,
            -0.254f, -0.499f, -0.097f, 0f, 216f,
            -0.254f, -0.499f, -0.097f, 0f, 216f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

/**
 * A still picture of the real map style around [center], rendered off screen by MapLibre's snapshotter and
 * cached. Shows [fallback] while it renders, offline, or when there's no position yet.
 */
@Composable
private fun MapPreview(terrain: Boolean, center: Pair<Double, Double>?, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isDarkTheme()
    val key = center?.let { "$terrain:${"%.2f".format(Locale.US, it.first)}:${"%.2f".format(Locale.US, it.second)}" }
    var bitmap by remember(key) { mutableStateOf(key?.let { previewCache[it] }) }
    DisposableEffect(key) {
        if (center == null || key == null || bitmap != null) return@DisposableEffect onDispose { }
        val style = Style.Builder().fromUri(VectorMaps.STYLE_URL).apply {
            if (terrain) {
                withSource(VectorMaps.terrainSource())
                withLayer(VectorMaps.hillshadeLayer(dark = false))
            }
        }
        val snapshotter = MapSnapshotter(
            context,
            MapSnapshotter.Options(64, 64)
                .withPixelRatio(context.resources.displayMetrics.density)
                .withStyleBuilder(style)
                .withCameraPosition(CameraPosition.Builder().target(LatLng(center.first, center.second)).zoom(12.0).build())
                .withLogo(false),
        )
        snapshotter.start(object : MapSnapshotter.SnapshotReadyCallback {
            override fun onSnapshotReady(snapshot: MapSnapshot) {
                previewCache[key] = snapshot.bitmap
                bitmap = snapshot.bitmap
            }
        })
        onDispose { snapshotter.cancel() }
    }
    val b = bitmap
    if (b == null) {
        fallback()
    } else {
        Image(
            b.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = if (dark) darkPreview else null,
            modifier = Modifier.size(64.dp),
        )
    }
}
