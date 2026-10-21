package io.github.stephenjoy98.heatmap.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.geometry.LatLng
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.Stats
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.map.Route3dTracks
import io.github.stephenjoy98.heatmap.map.HeatRamps
import io.github.stephenjoy98.heatmap.map.HeatTiles
import io.github.stephenjoy98.heatmap.map.heatTilePx
import io.github.stephenjoy98.heatmap.map.OwnTracks
import io.github.stephenjoy98.heatmap.tracking.ActivityTracker
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var layersOpen by remember { mutableStateOf(false) }
    var recordingSheet by remember { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var accuracyOpen by rememberSaveable { mutableStateOf(false) }
    val showDot by Prefs.showLocationDot.collectAsStateWithLifecycle()
    // Full screen: only the compass, location, zoom, layers and full-screen buttons stay over the map.
    val full = MapChrome.fullscreen
    BackHandler(enabled = full && !LocalCovered.current) { MapChrome.fullscreen = false }
    // The hidden controls fly out (easing out) and back in (easing in) by moving, never by changing the layout,
    // so the map and the buttons that stay don't jump. 0 = all shown, 1 = full screen.
    val hide by animateFloatAsState(
        if (full) 1f else 0f,
        tween(FULLSCREEN_MS, easing = if (full) EaseOut else EaseIn),
        label = "fullscreen",
    )
    var topRowPx by remember { mutableIntStateOf(0) }
    var panelPx by remember { mutableIntStateOf(0) }
    // The tab bar's height, remembered while it's hidden; the map runs under it so it never changes size.
    var navPad by remember { mutableStateOf(80.dp) }
    val barPad = contentPadding.calculateBottomPadding()
    LaunchedEffect(barPad) { if (barPad > 0.dp) navPad = barPad }
    val density = LocalDensity.current
    // Live GPS pauses while a page covers the map.
    val covered = LocalCovered.current
    val liveFix = rememberLiveFix(hasLocation && !covered, detailed = accuracyOpen)
    val signal = rememberFixQuality(liveFix)
    // The blue dot: the newer of the last recorded dot and the live GPS fix (if it's reasonably precise), so it
    // keeps up while you travel; the map glides it between them.
    val dotAt = run {
        val rec = stats?.latest
        val live = liveFix?.takeIf { it.hasAccuracy() && it.accuracy <= 50f }
        when {
            live != null && (rec == null || live.time > rec.time) -> LatLng(live.latitude, live.longitude)
            rec != null -> LatLng(rec.lat, rec.lon)
            else -> null
        }
    }
    // Routes at their recorded heights, for the 3D buildings view.
    val route3d = remember(filter) { { box: GeoBox -> Route3dTracks.load(store, box, filter) } }
    val layers = remember(filter, mode, dark, smoothRoutes, snapToRoads) {
        listOf(MapLayer("heat", HeatTiles(OwnTracks(store, filter), if (dark) HeatRamps.darkHeat else HeatRamps.lightHeat, mode, tilePx)))
    }

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

    Box(Modifier.fillMaxSize()) {
        AppMap(
            camera = camera,
            layers = layers,
            modifier = Modifier.fillMaxSize(),
            refreshKey = changes,
            latest = if (showDot) dotAt else null,
            cacheKey = "map",
            route3d = route3d,
            bottomInset = navPad,
        )
        // While the accuracy card is open, touching the map anywhere else closes it. The buttons drawn above
        // this still work as usual.
        if (accuracyOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            accuracyOpen = false
                        }
                    },
            )
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .onSizeChanged { topRowPx = it.height }
                    // Up and off the top of the screen.
                    .graphicsLayer { translationY = -hide * (size.height + with(density) { 80.dp.toPx() }) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                FilterButton(
                    open = filtersOpen,
                    active = !filter.containsAll(TravelMode.entries),
                    onClick = { filtersOpen = !filtersOpen },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                StatsBar(stats, Modifier.align(Alignment.Center))
                ProfileButton(
                    onClick = onOpenProfile,
                    background = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            // The travel-mode chips slide out from the filter button, and tuck back into it.
            AnimatedVisibility(
                visible = filtersOpen,
                enter = expandVertically(spatialSpring(), expandFrom = Alignment.Top) +
                    scaleIn(spatialSpring(), initialScale = 0.6f, transformOrigin = TransformOrigin(0f, 0f)) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) +
                    scaleOut(targetScale = 0.6f, transformOrigin = TransformOrigin(0f, 0f)) + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.size(8.dp))
                    ModeFilterRow(filter)
                }
            }
            }
            // Under the top row, and under the chips when they're out so nothing overlaps: the scale while the
            // map moves on the left, and the compass on the right, under the profile button.
            // In full screen the compass glides up into the top row's place.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                    .graphicsLayer { translationY = -hide * (topRowPx + with(density) { 12.dp.toPx() }) },
            ) {
                if (!full) ScaleBar(camera, Modifier.align(Alignment.CenterStart))
                CompassButton(camera, Modifier.align(Alignment.CenterEnd))
            }
        }

        if (layersOpen) MapLayersSheet(onDismiss = { layersOpen = false })

        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navPad).padding(12.dp)) {
            // The buttons that stay glide down into the space the recording panel and the tabs leave.
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = hide * (panelPx + with(density) { navPad.toPx() }) },
                verticalAlignment = Alignment.Bottom,
            ) {
                // Bottom left: GPS accuracy. Its card grows up out of the button and shrinks back into it.
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    AnimatedVisibility(
                        visible = accuracyOpen && hasLocation && !full,
                        enter = scaleIn(spatialSpring(), initialScale = 0.3f, transformOrigin = TransformOrigin(0.05f, 1f)) + fadeIn(),
                        exit = scaleOut(targetScale = 0.3f, transformOrigin = TransformOrigin(0.05f, 1f)) + fadeOut(),
                    ) {
                        Column {
                            // The tail points at the middle of the 48 dp accuracy button.
                            AccuracyCard(liveFix, tailInset = 15.dp)
                            Spacer(Modifier.size(4.dp))
                        }
                    }
                    // Off to the left.
                    if (hasLocation) {
                        Box(Modifier.graphicsLayer { translationX = -hide * (size.width + with(density) { 24.dp.toPx() }) }) {
                            AccuracyButton(signal) { if (!full) accuracyOpen = !accuracyOpen }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                // Bottom right, from the corner up: map layers, zoom, then your latest position.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tip(if (full) "Show all the map's controls" else "Full screen: hide everything but the map's buttons") {
                        Surface(
                            onClick = {
                                accuracyOpen = false
                                filtersOpen = false
                                MapChrome.fullscreen = !full
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                            shadowElevation = 3.dp,
                            modifier = Modifier.size(48.dp).semantics { contentDescription = if (full) "Exit full screen" else "Full screen" },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MaterialIcon(if (full) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen)
                            }
                        }
                    }
                    // Double duty: once the map is on your latest position, the next tap turns it north up.
                    val latest = stats?.latest
                    val onLatest = latest != null && !camera.moving && camera.center?.let { (lat, lon) ->
                        val d = FloatArray(1)
                        android.location.Location.distanceBetween(lat, lon, latest.lat, latest.lon, d)
                        d[0] <= maxOf(25f, camera.metersPerPixel * 40f)
                    } == true
                    val rotated = ((camera.bearing % 360f) + 360f) % 360f in 1f..359f
                    val turnsNorth = onLatest && rotated
                    Tip(if (turnsNorth) "Turn the map back to north" else "Go to your latest position") {
                        Surface(
                            onClick = {
                                when {
                                    latest == null -> scope.launch { snackbar.showSnackbar("No points recorded yet") }
                                    turnsNorth -> camera.northUp()
                                    else -> camera.animateTo(latest.lat, latest.lon, 16f)
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                            shadowElevation = 3.dp,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.LocationOn, contentDescription = if (turnsNorth) "Turn map north up" else "Go to my latest position")
                            }
                        }
                    }
                    ZoomButtons(camera)
                    LayersButton(onClick = { layersOpen = true })
                }
            }
            // Down and off the bottom of the screen.
            Column(
                Modifier
                    .onSizeChanged { panelPx = it.height }
                    .graphicsLayer { translationY = hide * (size.height + with(density) { (navPad + 24.dp).toPx() }) },
            ) {
            Spacer(Modifier.size(8.dp))
            ControlPanel(
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
                onOpen = { recordingSheet = true },
            )
            }
        }
    }

    if (recordingSheet) RecordingSheet(onDismiss = { recordingSheet = false })
}

/** Whether the map is full screen, with the tabs and most controls hidden. Kept here so the tab bar can hide too. */
object MapChrome {
    var fullscreen by mutableStateOf(false)
}

/** How long controls take to fly out of or back onto the map when switching full screen. */
const val FULLSCREEN_MS = 300

/** Material's "filter_list" icon; the core icon set doesn't include it. */
private val FilterIcon: ImageVector by lazy {
    ImageVector.Builder("Filter", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes("M10,18h4v-2h-4v2zM3,6v2h18L21,6L3,6zM6,13h12v-2L6,11v2z"),
            fill = SolidColor(Color.Black),
        )
    }.build()
}

/** Shows or hides the travel-mode chips. A dot marks that the map is filtered, even with the chips hidden. */
@Composable
private fun FilterButton(open: Boolean, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Tip(if (open) "Hide the travel mode filters" else "Filter the map by how you travelled", modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (open) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            shadowElevation = 3.dp,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                BadgedBox(badge = { if (active) Badge(containerColor = MaterialTheme.colorScheme.primary) }) {
                    Icon(FilterIcon, contentDescription = if (open) "Hide travel mode filters" else "Filter by travel mode")
                }
            }
        }
    }
}

@Composable
private fun ModeFilterRow(filter: Set<TravelMode>) {
    val all = filter.containsAll(TravelMode.entries)
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
        // Top-level modes only: a mode's chip covers its sub-modes too (PublicTransit includes Bus).
        for (m in TravelMode.topLevel + TravelMode.UNKNOWN) {
            val on = !all && m in filter
            FilterChip(
                selected = on,
                onClick = {
                    // From "All", tapping a mode shows just that mode; after that, taps add or remove modes.
                    val group = m.withChildren.toSet()
                    val next = when {
                        all -> group
                        on -> filter - group
                        else -> filter + group
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
    onOpen: () -> Unit,
) {
    val currentId by Prefs.currentMode.collectAsStateWithLifecycle()
    val current = currentId?.let { TravelMode.fromId(it) }
    val atPlace by Prefs.atPlace.collectAsStateWithLifecycle()
    // Tapping the tile (anywhere but the button) picks the kind of trip and the recording interval.
    Surface(
        onClick = onOpen,
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
            Tip(if (tracking) "Stop recording" else "Start recording your location") {
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
                    if (current != null) {
                        Text(" · ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ModeDot(current, 8.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(current.fullLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val auto by Prefs.autoInterval.collectAsStateWithLifecycle()
                val live by Prefs.liveIntervalSec.collectAsStateWithLifecycle()
                Text(
                    if (auto) "Auto · ${if (tracking) "every ${formatInterval(live)}" else "adapts to you"}" else "Every ${formatInterval(if (tracking) live else intervalSec)}",
                    style = MaterialTheme.typography.titleLarge,
                )
                // At a saved place recording slows down; say why.
                if (tracking) atPlace?.let { p ->
                    Text(
                        "At ${p.name} · slower while you're settled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Tip("Trip type and interval") {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
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
