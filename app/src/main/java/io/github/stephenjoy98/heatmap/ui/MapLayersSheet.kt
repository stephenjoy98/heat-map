package io.github.stephenjoy98.heatmap.ui

import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import io.github.stephenjoy98.heatmap.data.DisplayMode
import io.github.stephenjoy98.heatmap.data.HeatPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import io.github.stephenjoy98.heatmap.R
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
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
 * types are shown as pictures of a street map and a terrain map, in light and dark versions (see
 * tools/make_layer_previews.py).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLayersSheet(onDismiss: () -> Unit) {
    val layer by Prefs.mapLayer.collectAsStateWithLifecycle()
    val buildings by Prefs.buildings3d.collectAsStateWithLifecycle()
    // Pictures follow the app's own light or dark theme.
    val dark = isDarkTheme()
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
                    TilePicture(if (dark) R.drawable.layer_default_dark else R.drawable.layer_default)
                }) { drawDefaultArt(it) }
                LayerTile("Terrain", layer == MapLayerType.TERRAIN, { Prefs.setMapLayer(MapLayerType.TERRAIN) }, preview = {
                    TilePicture(if (dark) R.drawable.layer_terrain_dark else R.drawable.layer_terrain)
                }) { drawTerrainArt(it) }
            }
            Spacer(Modifier.height(20.dp))
            Text("Map details", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LayerTile("3D buildings", buildings, { Prefs.setBuildings3d(!buildings) }) { drawBuildingsArt(it) }
                val dot by Prefs.showLocationDot.collectAsStateWithLifecycle()
                LayerTile("My location", dot, { Prefs.setShowLocationDot(!dot) }) { drawLocationArt(it) }
                val raised by Prefs.routes3d.collectAsStateWithLifecycle()
                LayerTile("3D routes", raised, { Prefs.setRoutes3d(!raised) }) { drawRaisedRouteArt(it) }
            }
            if (!buildings) {
                Text(
                    "3D routes show when the map is tilted into 3D.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            // The heatmap's colours: how often you've been somewhere, from one visit to the most.
            Text("Heatmap", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val palette by Prefs.heatPalette.collectAsStateWithLifecycle()
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LayerTile("Fire", palette == HeatPalette.FIRE, { Prefs.setHeatPalette(HeatPalette.FIRE) }) { drawHeatArt(FIRE_STOPS) }
                LayerTile("Ice", palette == HeatPalette.ICE, { Prefs.setHeatPalette(HeatPalette.ICE) }) { drawHeatArt(ICE_STOPS) }
                // No heat: every route the same plain white.
                LayerTile("White", palette == HeatPalette.WHITE, { Prefs.setHeatPalette(HeatPalette.WHITE) }) { drawHeatArt(WHITE_STOPS) }
            }
            // Routes draws the lines you travelled; Dots draws where the recorded dots are, thicker where there are more.
            val display by Prefs.displayMode.collectAsStateWithLifecycle()
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = display == DisplayMode.ROUTES,
                    onClick = { Prefs.setDisplayMode(DisplayMode.ROUTES) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Routes") }
                SegmentedButton(
                    selected = display == DisplayMode.POINTS,
                    onClick = { Prefs.setDisplayMode(DisplayMode.POINTS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Dots") }
            }
            Spacer(Modifier.height(20.dp))
            // Map colours, folded away until you want it: muted to vivid, and whether roads join in.
            var coloursOpen by remember { mutableStateOf(false) }
            val colour by (if (dark) Prefs.mapColourDark else Prefs.mapColourLight).collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { coloursOpen = !coloursOpen }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Map colours", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            colour <= 0f -> "Standard"
                            colour >= 1f -> "Vivid"
                            else -> "${(colour * 100).roundToInt()}% vivid"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (coloursOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (coloursOpen) "Hide map colours" else "Show map colours",
                )
            }
            AnimatedVisibility(visible = coloursOpen) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (dark) "For the dark map" else "For the light map",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (colour != 0f) TextButton(onClick = { Prefs.setMapColour(dark, 0f) }) { Text("Reset") }
                    }
                    Slider(value = colour, onValueChange = { Prefs.setMapColour(dark, it) }, valueRange = 0f..1f)
                    Row {
                        Text("Muted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("Vivid", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val roads by Prefs.mapColourRoads.collectAsStateWithLifecycle()
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clickable { Prefs.setMapColourRoads(!roads) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Colour roads too", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = roads, onCheckedChange = Prefs::setMapColourRoads)
                    }
                }
            }
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
        // The name works as part of the tile too.
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.primary else scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 2.dp),
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

// The heatmap palettes' own colours (map elements aren't themed), from one visit to the most.
private val FIRE_STOPS = listOf(Color(0xFF78082D), Color(0xFFDC2819), Color(0xFFFF9100), Color(0xFFFFF0A0), Color.White)
private val WHITE_STOPS = List(5) { Color(0xFFF0F0F0) }
private val ICE_STOPS = listOf(Color(0xFF0C1969), Color(0xFF195AD7), Color(0xFF46B4FF), Color(0xFFC8EEFF), Color.White)

/** A heatmap palette as stripes of solid colour, from one visit (left) to the most (right). */
private fun DrawScope.drawHeatArt(stops: List<Color>) {
    val w = size.width
    val h = size.height
    val band = w * 0.8f / stops.size
    stops.forEachIndexed { i, c ->
        drawRect(c, topLeft = Offset(w * 0.1f + i * band, h * 0.3f), size = androidx.compose.ui.geometry.Size(band, h * 0.4f))
    }
}

/** A route lifted off the ground on a curtain, in the 3D route's own orange (map elements aren't themed). */
private fun DrawScope.drawRaisedRouteArt(c: ArtColors) {
    val w = size.width
    val h = size.height
    val orange = if (c.selected) Color(0xFFFF7A1A) else c.accent
    val ground = listOf(Offset(w * 0.12f, h * 0.82f), Offset(w * 0.45f, h * 0.7f), Offset(w * 0.88f, h * 0.78f))
    val top = listOf(Offset(w * 0.12f, h * 0.5f), Offset(w * 0.45f, h * 0.25f), Offset(w * 0.88f, h * 0.4f))
    val curtain = Path().apply {
        moveTo(ground[0].x, ground[0].y)
        ground.drop(1).forEach { lineTo(it.x, it.y) }
        top.reversed().forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(curtain, orange.copy(alpha = 0.3f))
    for (i in 0 until top.size - 1) drawLine(orange, top[i], top[i + 1], strokeWidth = h * 0.08f)
}

/** A street corner with the blue dot for where you are on it (grey while it's hidden). */
private fun DrawScope.drawLocationArt(c: ArtColors) {
    val w = size.width
    val h = size.height
    drawLine(c.line, Offset(0f, h * 0.62f), Offset(w, h * 0.62f), strokeWidth = h * 0.08f)
    drawLine(c.line, Offset(w * 0.38f, 0f), Offset(w * 0.38f, h), strokeWidth = h * 0.08f)
    // The map's own blue, not the theme's: map elements keep their colours.
    val blue = if (c.selected) Color(0xFF1A73E8) else c.accent
    val centre = Offset(w * 0.38f, h * 0.62f)
    drawCircle(blue.copy(alpha = 0.25f), radius = h * 0.26f, center = centre)
    drawCircle(Color.White, radius = h * 0.13f, center = centre)
    drawCircle(blue, radius = h * 0.1f, center = centre)
}

/** A map-type picture filling its tile. */
@Composable
private fun TilePicture(@DrawableRes id: Int) {
    Image(
        painterResource(id),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(64.dp),
    )
}
