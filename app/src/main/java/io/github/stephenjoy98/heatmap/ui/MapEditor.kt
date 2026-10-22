package io.github.stephenjoy98.heatmap.ui

import androidx.compose.ui.window.DialogProperties
import io.github.stephenjoy98.heatmap.data.Units
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import android.content.Context
import io.github.stephenjoy98.heatmap.data.DeleteResult
import io.github.stephenjoy98.heatmap.data.EditablePoint
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.roads.RoadSnapWorker
import io.github.stephenjoy98.heatmap.tracking.TripDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.stephenjoy98.heatmap.data.GeoBox
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * What's being edited on the map: one day's dots (or its deleted dots, when [showingDeleted]), the start–end
 * range picked, and a single tapped dot.
 */
class DotEditState(day: LocalDate) {
    var day by mutableStateOf(day)
    var showingDeleted by mutableStateOf(false)
    var deletedCount by mutableIntStateOf(0)
    var points by mutableStateOf<List<EditablePoint>>(emptyList())
    var range by mutableStateOf(0..0)
    var selected by mutableStateOf<Int?>(null)

    /** The dots a delete (or restore) would act on: the tapped dot if there is one, otherwise the range. */
    val target: List<EditablePoint>
        get() = selected?.let { listOfNotNull(points.getOrNull(it)) }
            ?: if (points.isEmpty()) emptyList() else points.subList(range.first, range.last + 1)

    fun load(newPoints: List<EditablePoint>) {
        points = newPoints
        range = 0..(newPoints.size - 1).coerceAtLeast(0)
        selected = null
    }
}

/**
 * Draws the day's dots over the map: dots inside the range are highlighted and joined in time order, the
 * tapped dot is ringed. It takes no touches, so the map underneath still pans and zooms.
 */
@Composable
fun DotsOverlay(camera: MapCamera, state: DotEditState, modifier: Modifier = Modifier) {
    val inRange = if (state.showingDeleted) MaterialTheme.colorScheme.error else MapAccent
    val outside = MaterialTheme.colorScheme.outline
    val ring = MaterialTheme.colorScheme.surface
    val pick = Color(0xFFE53935)
    Canvas(modifier) {
        camera.cameraTick // Redraw whenever the camera moves.
        val pts = state.points
        if (pts.isEmpty()) return@Canvas
        val r = 4.dp.toPx()
        val path = Path()
        var started = false
        pts.forEachIndexed { i, p ->
            val o = camera.toScreen(p.lat, p.lon) ?: return@Canvas
            val active = i in state.range
            if (active) {
                if (started) path.lineTo(o.x, o.y) else path.moveTo(o.x, o.y)
                started = true
            }
            drawCircle(if (active) inRange else outside.copy(alpha = 0.6f), radius = if (active) r else r * 0.75f, center = o)
        }
        drawPath(path, inRange.copy(alpha = 0.5f), style = Stroke(width = 2.dp.toPx()))
        state.selected?.let { pts.getOrNull(it) }?.let { p ->
            val o = camera.toScreen(p.lat, p.lon) ?: return@let
            drawCircle(ring, radius = r * 3f, center = o)
            drawCircle(pick, radius = r * 2.2f, center = o)
        }
    }
}

/** The dot nearest to a tap, if one is within [maxDistancePx] of it on screen. */
fun nearestDot(camera: MapCamera, state: DotEditState, lat: Double, lon: Double, maxDistancePx: Float): Int? {
    val tap = camera.toScreen(lat, lon) ?: return null
    var best: Int? = null
    var bestDist = maxDistancePx * maxDistancePx
    state.points.forEachIndexed { i, p ->
        val o = camera.toScreen(p.lat, p.lon) ?: return null
        val d = (o - tap).getDistanceSquared()
        if (d <= bestDist) {
            bestDist = d
            best = i
        }
    }
    return best
}

/** Frames the camera on [points]. */
fun frameDots(camera: MapCamera, points: List<EditablePoint>) {
    if (points.isEmpty()) return
    frame(camera, GeoBox(points.maxOf { it.lat }, points.minOf { it.lat }, points.maxOf { it.lon }, points.minOf { it.lon }))
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
private val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeFormat)

/** Replaces the recording panel while editing: pick a day, narrow the dots with a start–end slider, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPanel(
    state: DotEditState,
    loading: Boolean,
    onDay: (LocalDate) -> Unit,
    onFocus: (EditablePoint) -> Unit,
    onShow: (List<EditablePoint>) -> Unit,
    onDelete: (List<EditablePoint>) -> Unit,
    onRestore: (List<EditablePoint>) -> Unit,
    onClose: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<List<EditablePoint>?>(null) }
    val pts = state.points

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tip("Stop editing") { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Stop editing") } }
                Text("Edit dots", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Tip("Previous day") {
                    IconButton(onClick = { onDay(state.day.minusDays(1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                }
                TextButton(onClick = { picking = true }) { Text(state.day.format(dayFormat)) }
                Tip("Next day") {
                    IconButton(onClick = { onDay(state.day.plusDays(1)) }, enabled = state.day < LocalDate.now()) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                SegmentedButton(
                    selected = !state.showingDeleted,
                    onClick = { state.showingDeleted = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("On map") }
                SegmentedButton(
                    selected = state.showingDeleted,
                    onClick = { state.showingDeleted = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(if (state.deletedCount > 0) "Deleted (${state.deletedCount})" else "Deleted") }
            }

            when {
                loading -> Hint("Loading dots…")
                pts.isEmpty() -> Hint(if (state.showingDeleted) "No deleted dots on this day." else "No dots on this day.")
                else -> {
                    val sel = state.selected?.let { pts.getOrNull(it) }
                    if (sel != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Dot ${state.selected!! + 1} of ${pts.size}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${time(sel.time)} · ±${Units.length(sel.accuracy.toDouble())}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Tip("Previous dot") {
                                IconButton(
                                    onClick = { state.selected = (state.selected!! - 1).also { onFocus(pts[it]) } },
                                    enabled = state.selected!! > 0,
                                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous dot") }
                            }
                            Tip("Next dot") {
                                IconButton(
                                    onClick = { state.selected = (state.selected!! + 1).also { onFocus(pts[it]) } },
                                    enabled = state.selected!! < pts.size - 1,
                                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next dot") }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { state.selected = null }) { Text("Back to range") }
                            Spacer(Modifier.width(8.dp))
                            if (state.showingDeleted) {
                                RestoreButton("Restore dot") { onRestore(listOf(sel)) }
                            } else {
                                DeleteButton("Delete dot") { confirming = listOf(sel) }
                            }
                        }
                    } else {
                        val first = pts[state.range.first]
                        val last = pts[state.range.last]
                        val count = state.range.last - state.range.first + 1
                        Text(
                            "$count of ${pts.size} dots · ${time(first.time)} – ${time(last.time)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        if (pts.size > 1) {
                            RangeSlider(
                                value = state.range.first.toFloat()..state.range.last.toFloat(),
                                onValueChange = { v -> state.range = v.start.roundToInt()..v.endInclusive.roundToInt() },
                                valueRange = 0f..(pts.size - 1).toFloat(),
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            RangeSteppers(state, Modifier.padding(horizontal = 4.dp))
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Tap a dot to pick it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onShow(state.target) }) { Text("Show") }
                            if (state.showingDeleted) {
                                RestoreButton(if (count == 1) "Restore dot" else "Restore $count") { onRestore(state.target) }
                            } else {
                                DeleteButton(if (count == 1) "Delete dot" else "Delete $count") { confirming = state.target }
                            }
                        }
                    }
                }
            }
        }
    }

    confirming?.let { doomed ->
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onDismissRequest = { confirming = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(if (doomed.size == 1) "Delete this dot?" else "Delete ${doomed.size} dots?") },
            text = {
                Text(
                    "Recorded ${time(doomed.first().time)}" +
                        (if (doomed.size > 1) " – ${time(doomed.last().time)}" else "") +
                        " on ${state.day.format(dayFormat)}. Trips on this stretch are rebuilt from the dots that are left. " +
                        "You can bring them back later from Deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    onDelete(doomed)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }

    if (picking) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = state.day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 12.dp),
    )
}

@Composable
private fun RestoreButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Icon(Icons.Filled.Refresh, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun DeleteButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

/** After deleting or restoring dots: rebuild trips, and make sure backups and shared routes catch up. */
suspend fun afterDotEdit(context: Context, result: DeleteResult) {
    Prefs.addDirtyBackupDays(result.days)
    if (result.removedRemoteIds.isNotEmpty()) {
        Prefs.setPendingRouteDeletes(Prefs.pendingRouteDeletes + result.removedRemoteIds)
    }
    withContext(Dispatchers.IO) { TripDetector.process(context) }
    RoadSnapWorker.runNow(context)
}
