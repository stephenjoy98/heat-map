package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import java.time.ZoneId
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.PeriodStat
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.map.TileMath
import io.github.stephenjoy98.heatmap.map.heatTilePx
import io.github.stephenjoy98.heatmap.review.Insights
import io.github.stephenjoy98.heatmap.timeline.DayEntry
import io.github.stephenjoy98.heatmap.timeline.TimelineData
import io.github.stephenjoy98.heatmap.timeline.TimelinePeriod
import io.github.stephenjoy98.heatmap.timeline.TimelineRepository
import io.github.stephenjoy98.heatmap.timeline.TimelineScale
import io.github.stephenjoy98.heatmap.timeline.TimelineTiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.ln
import kotlin.math.min

/** Browse your history by day, week, month or year, like Google Maps' Timeline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(contentPadding: PaddingValues, resumeTick: Int, onProfile: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    val dark = isDarkTheme()
    var scale by rememberSaveable { mutableStateOf(TimelineScale.DAY) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    val period = TimelinePeriod(scale, anchor)
    val changes by store.changes.collectAsStateWithLifecycle()

    val data by produceState<TimelineData?>(null, period, changes, resumeTick) {
        value = withContext(Dispatchers.IO) { TimelineRepository.load(context, period) }
    }

    val camera = rememberMapCamera()
    val tilePx = remember { heatTilePx(context.resources.displayMetrics.density) }
    val casing = if (dark) Color(0xCC000000) else Color(0xCCFFFFFF)
    // Editing dots: pick a day, narrow down with the start–end slider or tap a dot, delete or restore.
    var editing by rememberSaveable { mutableStateOf(false) }
    val edit = remember { DotEditState(anchor) }
    var editLoading by remember { mutableStateOf(false) }
    var editReload by remember { mutableIntStateOf(0) }
    var framedDay by remember { mutableStateOf<LocalDate?>(null) }
    // Undo shows over the map, so it never covers the edit panel's buttons.
    val editSnackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tapRadiusPx = 32 * context.resources.displayMetrics.density
    var layersOpen by remember { mutableStateOf(false) }
    val route3d = remember { { box: GeoBox -> store.elevationTracksIn(box, TravelMode.entries.toSet()) } }

    val layers = remember(data, dark, editing) {
        val d = data ?: return@remember emptyList()
        // While editing, the routes fade back so the dots stand out.
        listOf(MapLayer("timeline-${d.period}", TimelineTiles(d.routes, tilePx, casing.toArgb()), alpha = if (editing) 0.35f else 1f))
    }

    LaunchedEffect(editing, edit.day, edit.showingDeleted, editReload) {
        if (!editing) {
            framedDay = null
            return@LaunchedEffect
        }
        editLoading = true
        val zone = ZoneId.systemDefault()
        val from = edit.day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = edit.day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val (pts, deleted) = withContext(Dispatchers.IO) {
            val deleted = store.deletedPoints(from, to)
            (if (edit.showingDeleted) deleted else store.editablePoints(from, to)) to deleted.size
        }
        edit.deletedCount = deleted
        edit.load(pts)
        editLoading = false
        if (framedDay != edit.day) {
            framedDay = edit.day
            frameDots(camera, pts)
        }
    }
    val stopEditing = {
        // Land on the day that was being edited.
        anchor = edit.day
        scale = TimelineScale.DAY
        editing = false
    }
    BackHandler(enabled = editing && !LocalCovered.current) { stopEditing() }
    // Frame the period's travels whenever a new period loads.
    LaunchedEffect(data?.period, data?.bounds) {
        data?.bounds?.let { frame(camera, it) }
    }

    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(
            title = { Text(if (editing) "Edit dots" else "Timeline") },
            actions = {
                if (!editing) {
                    Tip("Edit or delete dots for this day") {
                        IconButton(onClick = {
                            edit.day = anchor
                            edit.showingDeleted = false
                            editing = true
                        }) { Icon(Icons.Filled.Edit, contentDescription = "Edit dots") }
                    }
                }
                ProfileButton(onClick = onProfile)
            },
        )
        if (!editing) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                TimelineScale.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = s == scale,
                        onClick = { scale = s },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = TimelineScale.entries.size),
                    ) { Text(s.label, maxLines = 1) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Tip("Previous ${scale.label.lowercase()}") {
                    IconButton(onClick = { anchor = period.previous().anchor }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                    }
                }
                TextButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                    Text(period.label, style = MaterialTheme.typography.titleMedium)
                }
                Tip("Next ${scale.label.lowercase()}") {
                    IconButton(onClick = { anchor = period.next().anchor }, enabled = !period.isCurrent) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .then(if (editing) Modifier.weight(1f) else Modifier.height(260.dp))
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            AppMap(
                camera = camera,
                layers = layers,
                modifier = Modifier.fillMaxSize(),
                refreshKey = data,
                cacheKey = "timeline",
                route3d = route3d,
                onMapClick = if (editing) {
                    { lat, lon -> nearestDot(camera, edit, lat, lon, tapRadiusPx)?.let { edit.selected = it } }
                } else {
                    null
                },
            )
            if (editing) {
                DotsOverlay(camera, edit, Modifier.fillMaxSize())
                // Map type and 3D buildings, right where you're editing.
                LayersButton(onClick = { layersOpen = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                SnackbarHost(editSnackbar, Modifier.align(Alignment.TopCenter))
            } else if (data == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        if (editing) {
            Box(Modifier.padding(12.dp)) {
                EditPanel(
                    state = edit,
                    loading = editLoading,
                    onDay = { edit.day = it },
                    onFocus = { camera.animateTo(it.lat, it.lon, 17f) },
                    onShow = { frameDots(camera, it) },
                    onDelete = { doomed ->
                        scope.launch {
                            afterDotEdit(context, withContext(Dispatchers.IO) { store.deletePoints(doomed.map { it.id }) })
                            editReload++
                            val undo = editSnackbar.showSnackbar(
                                if (doomed.size == 1) "Dot deleted" else "${doomed.size} dots deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Long,
                            )
                            if (undo == SnackbarResult.ActionPerformed) {
                                afterDotEdit(context, withContext(Dispatchers.IO) { store.restorePoints(doomed.map { it.id }) })
                                editReload++
                            }
                        }
                    },
                    onRestore = { back ->
                        scope.launch {
                            afterDotEdit(context, withContext(Dispatchers.IO) { store.restorePoints(back.map { it.id }) })
                            editReload++
                            editSnackbar.showSnackbar(if (back.size == 1) "Dot restored" else "${back.size} dots restored")
                        }
                    },
                    onClose = stopEditing,
                )
            }
        } else {
            val d = data
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (d == null) return@LazyColumn
                item { Summary(d) }
                when (scale) {
                    TimelineScale.DAY -> {
                        if (d.entries.isEmpty()) item { Empty("Nothing recorded on this day.") }
                        items(d.entries, key = { "${it.javaClass.simpleName}-${it.start}" }) { entry ->
                            when (entry) {
                                is DayEntry.Move -> MoveRow(entry) { entry.box?.let { frame(camera, it) } }
                                is DayEntry.Stay -> StayRow(entry) { camera.animateTo(entry.lat, entry.lon, 16f) }
                            }
                        }
                    }
                    else -> {
                        if (d.stats.isEmpty()) item { Empty("Nothing recorded in this ${scale.label.lowercase()}.") }
                        val max = d.stats.maxOfOrNull { it.meters } ?: 0.0
                        items(d.stats, key = { it.key }) { stat ->
                            PeriodRow(stat, scale, max) {
                                if (scale == TimelineScale.YEAR) {
                                    anchor = YearMonth.parse(stat.key).atDay(1)
                                    scale = TimelineScale.MONTH
                                } else {
                                    anchor = LocalDate.parse(stat.key)
                                    scale = TimelineScale.DAY
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (layersOpen) {
        MapLayersSheet(
            onDismiss = { layersOpen = false },
            center = edit.points.getOrNull(edit.points.size / 2)?.let { it.lat to it.lon },
        )
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = anchor.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { anchor = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** Centres and zooms the map so [box] fits in a roughly 340 × 240 dp view. */
internal fun frame(camera: MapCamera, box: GeoBox) {
    val lonSpan = (box.east - box.west).coerceAtLeast(0.0005)
    val mercSpan = (TileMath.mercY(box.north) - TileMath.mercY(box.south)).coerceAtLeast(0.0005)
    val log2 = { v: Double -> ln(v) / ln(2.0) }
    val zoom = min(log2(340 * 360.0 / (256 * lonSpan)), log2(240 * 360.0 / (256 * mercSpan))) - 0.4
    camera.animateTo((box.north + box.south) / 2, (box.east + box.west) / 2, zoom.toFloat().coerceIn(3f, 17f))
}

@Composable
private fun Summary(d: TimelineData) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            if (d.trips == 0 && d.totalKm == 0.0) "No trips" else "${Insights.km(d.totalKm)} · ${d.trips} ${if (d.trips == 1) "trip" else "trips"}",
            style = MaterialTheme.typography.titleLarge,
        )
        if (d.kmByMode.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (TravelMode.choosable + TravelMode.UNKNOWN).forEach { m ->
                    val km = d.kmByMode[m] ?: return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(Color(m.color), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("${m.label} ${Insights.km(km)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

private val timeFormat: DateFormat get() = DateFormat.getTimeInstance(DateFormat.SHORT)

private fun duration(ms: Long): String {
    val min = ms / 60_000
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}

/** One step of the day: a coloured rail on the left, details on the right. */
@Composable
private fun TimelineRow(color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(color.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
            Box(Modifier.padding(top = 6.dp).size(12.dp).background(color, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun MoveRow(entry: DayEntry.Move, onClick: () -> Unit) {
    val t = entry.trip
    TimelineRow(Color(t.mode.color), onClick) {
        Text("${t.mode.label} · ${Insights.km(t.distanceM / 1000)}", style = MaterialTheme.typography.titleSmall)
        Text(
            "${timeFormat.format(Date(t.startTime))} – ${timeFormat.format(Date(t.endTime))} · ${duration(t.endTime - t.startTime)}" +
                (if (t.needsReview) " · needs your answer on Review" else ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StayRow(entry: DayEntry.Stay, onClick: () -> Unit) {
    TimelineRow(MaterialTheme.colorScheme.outline, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(entry.place ?: "Stayed here", style = MaterialTheme.typography.titleSmall)
        }
        Text(
            "${timeFormat.format(Date(entry.start))} – ${timeFormat.format(Date(entry.end))} · ${duration(entry.end - entry.start)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A day (or a month, in the year view) with its distance and a bar split by travel mode. */
@Composable
private fun PeriodRow(stat: PeriodStat, scale: TimelineScale, max: Double, onClick: () -> Unit) {
    val title = if (scale == TimelineScale.YEAR) {
        YearMonth.parse(stat.key).format(DateTimeFormatter.ofPattern("MMMM"))
    } else {
        LocalDate.parse(stat.key).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                if (stat.trips > 0) "${Insights.km(stat.meters / 1000)} · ${stat.trips} ${if (stat.trips == 1) "trip" else "trips"}" else "No trips",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (max > 0) {
                val shown = (stat.meters / max).toFloat().coerceIn(0f, 1f)
                (TravelMode.choosable + TravelMode.UNKNOWN).forEach { m ->
                    val part = stat.metersByMode[m] ?: return@forEach
                    if (part <= 0) return@forEach
                    Box(Modifier.fillMaxHeight().weight((part / stat.meters).toFloat() * shown).background(Color(m.color)))
                }
                if (shown < 1f) Spacer(Modifier.weight(1f - shown))
            }
        }
    }
}
