package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.stephenjoy98.heatmap.data.Cells
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.PointStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The colour explored squares are filled with (a map element, so not themed). */
private val AreaRed = Color(0xFFE53935)

/**
 * Areas explored, full screen: a map three quarters of the screen tall with every ~220 m square you've been to
 * between [from] and [to] filled red, and the squares counted by city below it.
 */
@Composable
fun AreasMapDialog(title: String, explanation: String, from: Long, to: Long, rows: List<Pair<String, String>>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val camera = rememberMapCamera()
    val cells by produceState(LongArray(0), from, to) {
        value = withContext(Dispatchers.IO) { PointStore.get(context).cellKeys(from, to) }
    }
    // Frame every square once they've loaded.
    LaunchedEffect(cells) {
        if (cells.isEmpty()) return@LaunchedEffect
        var n = -90.0
        var s = 90.0
        var e = -180.0
        var w = 180.0
        for (k in cells) {
            val south = Cells.south(Cells.rowOf(k))
            val west = Cells.west(Cells.colOf(k))
            s = min(s, south)
            n = max(n, south + 1 / Cells.PER_DEGREE)
            w = min(w, west)
            e = max(e, west + 1 / Cells.PER_DEGREE)
        }
        frame(camera, GeoBox(n, s, e, w))
    }
    val mapHeight = (LocalConfiguration.current.screenHeightDp * 0.75f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Tip("Close") { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") } }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(mapHeight)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    AppMap(camera = camera, layers = emptyList(), modifier = Modifier.fillMaxSize())
                    AreasOverlay(camera, cells, Modifier.fillMaxSize())
                    ScaleBar(camera, Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp)) {
                    item {
                        Text(
                            explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                        )
                    }
                    items(rows) { (name, value) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Fills each explored square red, at least a couple of pixels wide so far-out views still show them. */
@Composable
private fun AreasOverlay(camera: MapCamera, cells: LongArray, modifier: Modifier) {
    Canvas(modifier) {
        camera.cameraTick // Redraw whenever the camera moves.
        if (cells.isEmpty()) return@Canvas
        val minPx = 2.dp.toPx()
        val fill = AreaRed.copy(alpha = 0.55f)
        for (k in cells) {
            val south = Cells.south(Cells.rowOf(k))
            val west = Cells.west(Cells.colOf(k))
            val a = camera.toScreen(south, west) ?: return@Canvas
            val b = camera.toScreen(south + 1 / Cells.PER_DEGREE, west + 1 / Cells.PER_DEGREE) ?: return@Canvas
            val left = min(a.x, b.x)
            val top = min(a.y, b.y)
            if (left > size.width || top > size.height || max(a.x, b.x) < 0f || max(a.y, b.y) < 0f) continue
            val w = max(abs(b.x - a.x), minPx)
            val h = max(abs(b.y - a.y), minPx)
            drawRect(fill, topLeft = Offset(left, top), size = Size(w, h))
        }
    }
}
