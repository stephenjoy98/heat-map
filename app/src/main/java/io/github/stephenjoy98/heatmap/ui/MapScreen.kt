package io.github.stephenjoy98.heatmap.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.LatLng
import io.github.stephenjoy98.heatmap.data.DeleteResult
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.Stats
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.map.HeatRamps
import io.github.stephenjoy98.heatmap.map.HeatTiles
import io.github.stephenjoy98.heatmap.map.heatTilePx
import io.github.stephenjoy98.heatmap.map.OwnTracks
import io.github.stephenjoy98.heatmap.roads.RoadSnapWorker
import io.github.stephenjoy98.heatmap.tracking.ActivityTracker
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService
import io.github.stephenjoy98.heatmap.tracking.TripDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun MapScreen(contentPadding: PaddingValues, snackbar: SnackbarHostState, resumeTick: Int, onOpenProfile: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    val scope = rememberCoroutineScope()
    val dark = isDarkTheme()
    val tilePx = remember { heatTilePx(context.resources.displayMetrics.density) }

    val tracking by Prefs.tracking.collectAsStateWithLifecycle()
    val intervalSec by Prefs.intervalSec.collectAsStateWithLifecycle()
    val mode by Prefs.displayMode.collectAsStateWithLifecycle()
    val filter by Prefs.modeFilter.collectAsStateWithLifecycle()
    val changes by store.changes.collectAsStateWithLifecycle()
    val stats by produceState<Stats?>(null, changes) {
        value = withContext(Dispatchers.IO) { store.stats() }
    }
    val hasLocation = remember(resumeTick, tracking) { LocationTickerService.hasLocationPermission(context) }

    var centered by rememberSaveable { mutableStateOf(false) }
    val camera = rememberMapCamera()
    val smoothRoutes by Prefs.smoothRoutes.collectAsStateWithLifecycle()
    val snapToRoads by Prefs.snapToRoads.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var layersOpen by remember { mutableStateOf(false) }
    val layers = remember(filter, mode, dark, smoothRoutes, snapToRoads, editing) {
        // While editing, the heatmap fades back so the dots stand out.
        val heat = HeatTiles(OwnTracks(store, filter), if (dark) HeatRamps.darkHeat else HeatRamps.lightHeat, mode, tilePx)
        listOf(MapLayer("heat", heat, alpha = if (editing) 0.35f else 1f))
    }

    val edit = remember { DotEditState(LocalDate.now()) }
    var editLoading by remember { mutableStateOf(false) }
    var editReload by remember { mutableIntStateOf(0) }
    var framedDay by remember { mutableStateOf<LocalDate?>(null) }
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
    BackHandler(enabled = editing) { editing = false }
    val tapRadiusPx = 32 * context.resources.displayMetrics.density
    LaunchedEffect(stats) {
        val latest = stats?.latest
        if (!centered && latest != null) {
            camera.moveTo(latest.lat, latest.lon, 15f)
            centered = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (LocationTickerService.hasLocationPermission(context)) {
            LocationTickerService.start(context)
        } else {
            scope.launch { snackbar.showSnackbar("Location permission is needed to record your position") }
        }
    }

    Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        AppMap(
            camera = camera,
            layers = layers,
            modifier = Modifier.fillMaxSize(),
            refreshKey = changes,
            latest = stats?.latest?.let { LatLng(it.lat, it.lon) },
            showMyLocation = hasLocation,
            onMapClick = if (editing) {
                { lat, lon -> nearestDot(camera, edit, lat, lon, tapRadiusPx)?.let { edit.selected = it } }
            } else {
                null
            },
        )
        if (editing) DotsOverlay(camera, edit, Modifier.fillMaxSize())

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                StatsBar(stats, Modifier.align(Alignment.Center))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    ProfileButton(onClick = onOpenProfile, modifier = Modifier.size(48.dp))
                }
            }
            if (!editing) {
                Spacer(Modifier.size(8.dp))
                ModeFilterRow(filter)
            }
        }

        MapControls(
            camera = camera,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            onLayers = { layersOpen = true },
        )
        if (layersOpen) MapLayersSheet(onDismiss = { layersOpen = false })

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row {
                if (!editing) {
                    SmallFloatingActionButton(
                        onClick = {
                            // Start on the day of the latest dot, where a stray one is most likely to be noticed.
                            edit.day = stats?.latest?.let { Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault()).toLocalDate() }
                                ?: LocalDate.now()
                            editing = true
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) { Icon(Icons.Filled.Edit, contentDescription = "Edit dots") }
                    Spacer(Modifier.width(8.dp))
                }
                SmallFloatingActionButton(
                    onClick = {
                        val latest = stats?.latest
                        if (latest == null) {
                            scope.launch { snackbar.showSnackbar("No points recorded yet") }
                        } else {
                            camera.animateTo(latest.lat, latest.lon, 16f)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) { Icon(Icons.Filled.LocationOn, contentDescription = "Go to my latest position") }
            }
            Spacer(Modifier.size(8.dp))
            if (editing) {
                EditPanel(
                    state = edit,
                    loading = editLoading,
                    onDay = { edit.day = it },
                    onFocus = { camera.animateTo(it.lat, it.lon, 17f) },
                    onShow = { frameDots(camera, it) },
                    onDelete = { doomed ->
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { store.deletePoints(doomed.map { it.id }) }
                            afterDotEdit(context, result)
                            editReload++
                            val undo = snackbar.showSnackbar(
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
                            snackbar.showSnackbar(if (back.size == 1) "Dot restored" else "${back.size} dots restored")
                        }
                    },
                    onClose = { editing = false },
                )
            } else ControlPanel(
                tracking = tracking,
                intervalSec = intervalSec,
                onToggle = {
                    if (tracking) {
                        LocationTickerService.stop(context)
                    } else {
                        val missing = missingStartPermissions(context)
                        if (missing.isEmpty()) LocationTickerService.start(context) else permissionLauncher.launch(missing)
                    }
                },
            )
        }
    }
}

/** After deleting or restoring dots: rebuild trips, and make sure backups and shared routes catch up. */
private suspend fun afterDotEdit(context: Context, result: DeleteResult) {
    Prefs.addDirtyBackupDays(result.days)
    if (result.removedRemoteIds.isNotEmpty()) {
        Prefs.setPendingRouteDeletes(Prefs.pendingRouteDeletes + result.removedRemoteIds)
    }
    withContext(Dispatchers.IO) { TripDetector.process(context) }
    RoadSnapWorker.runNow(context)
}

@Composable
private fun ModeFilterRow(filter: Set<TravelMode>) {
    val all = filter.size == TravelMode.entries.size
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        val chipColors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        )
        FilterChip(
            selected = all,
            onClick = { Prefs.setModeFilter(TravelMode.entries.toSet()) },
            label = { Text("All") },
            colors = chipColors,
        )
        for (m in TravelMode.choosable + TravelMode.UNKNOWN) {
            val on = !all && m in filter
            FilterChip(
                selected = on,
                onClick = {
                    // From "All", tapping a mode shows just that mode; after that, taps add or remove modes.
                    val next = when {
                        all -> setOf(m)
                        on -> filter - m
                        else -> filter + m
                    }
                    Prefs.setModeFilter(next.ifEmpty { TravelMode.entries.toSet() })
                },
                label = { Text(m.label) },
                leadingIcon = { Box(Modifier.size(10.dp).background(Color(m.color), CircleShape)) },
                colors = chipColors,
            )
        }
    }
}

@Composable
private fun StatsBar(stats: Stats?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatItem(stats?.let { formatCount(it.total) } ?: "–", "points")
            StatItem(stats?.let { formatCount(it.today) } ?: "–", "today")
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ControlPanel(
    tracking: Boolean,
    intervalSec: Int,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(60.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (tracking) RecordRed else MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (tracking) {
                    // Stop symbol.
                    Box(Modifier.size(20.dp).background(Color.White, RoundedCornerShape(3.dp)))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start recording", modifier = Modifier.size(34.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tracking) {
                        Box(Modifier.size(8.dp).background(RecordRed, CircleShape))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (tracking) "Recording" else "Paused",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val auto by Prefs.autoInterval.collectAsStateWithLifecycle()
                val live by Prefs.liveIntervalSec.collectAsStateWithLifecycle()
                Text(
                    if (auto) "Auto · ${if (tracking) "every ${formatInterval(live)}" else "adapts to you"}" else "Every ${formatInterval(intervalSec)}",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

private fun missingStartPermissions(context: Context): Array<String> {
    val needed = mutableListOf<String>()
    if (!LocationTickerService.hasLocationPermission(context)) {
        needed += Manifest.permission.ACCESS_FINE_LOCATION
        needed += Manifest.permission.ACCESS_COARSE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        needed += Manifest.permission.POST_NOTIFICATIONS
    }
    // Optional but improves travel-mode detection a lot.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !ActivityTracker.hasPermission(context)) {
        needed += Manifest.permission.ACTIVITY_RECOGNITION
    }
    return needed.toTypedArray()
}
