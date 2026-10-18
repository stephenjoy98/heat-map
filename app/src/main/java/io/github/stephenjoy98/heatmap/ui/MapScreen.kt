package io.github.stephenjoy98.heatmap.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberTileOverlayState
import io.github.stephenjoy98.heatmap.BuildConfig
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.Stats
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.map.HeatRamps
import io.github.stephenjoy98.heatmap.map.HeatTileProvider
import io.github.stephenjoy98.heatmap.map.MapStyles
import io.github.stephenjoy98.heatmap.map.OwnTracks
import io.github.stephenjoy98.heatmap.tracking.ActivityTracker
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MapScreen(contentPadding: PaddingValues, snackbar: SnackbarHostState, resumeTick: Int) {
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    val scope = rememberCoroutineScope()
    val dark = isDarkTheme()

    val tracking by Prefs.tracking.collectAsStateWithLifecycle()
    val intervalSec by Prefs.intervalSec.collectAsStateWithLifecycle()
    val mode by Prefs.displayMode.collectAsStateWithLifecycle()
    val filter by Prefs.modeFilter.collectAsStateWithLifecycle()
    val changes by store.changes.collectAsStateWithLifecycle()
    val stats by produceState<Stats?>(null, changes) {
        value = withContext(Dispatchers.IO) { store.stats() }
    }
    val hasLocation = remember(resumeTick, tracking) { LocationTickerService.hasLocationPermission(context) }

    var showSettings by remember { mutableStateOf(false) }
    var centered by rememberSaveable { mutableStateOf(false) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)
    }

    val tileState = rememberTileOverlayState()
    val provider = remember(filter, mode, dark) {
        HeatTileProvider(OwnTracks(store, filter), if (dark) HeatRamps.darkHeat else HeatRamps.lightHeat, mode)
    }
    // New points arrive every tick; refresh tiles at most every couple of seconds.
    LaunchedEffect(changes) {
        delay(2000)
        tileState.clearTileCache()
    }
    LaunchedEffect(stats) {
        val latest = stats?.latest
        if (!centered && latest != null) {
            camera.position = CameraPosition.fromLatLngZoom(LatLng(latest.lat, latest.lon), 15f)
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
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(
                mapStyleOptions = MapStyleOptions(if (dark) MapStyles.dark else MapStyles.light),
                isMyLocationEnabled = hasLocation,
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = false,
            ),
        ) {
            // Recreate the overlay when its provider changes (filter, display mode, theme).
            key(provider) {
                TileOverlay(tileProvider = provider, state = tileState, fadeIn = false)
            }
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatsBar(stats)
            Spacer(Modifier.size(8.dp))
            ModeFilterRow(filter)
            if (!BuildConfig.HAS_MAPS_KEY) {
                Spacer(Modifier.size(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "This build has no Google Maps API key, so map tiles won't load. Your routes still draw. See the README.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            SmallFloatingActionButton(
                onClick = {
                    val latest = stats?.latest
                    if (latest == null) {
                        scope.launch { snackbar.showSnackbar("No points recorded yet") }
                    } else {
                        scope.launch {
                            camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(latest.lat, latest.lon), 16f), 600)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
            ) { Icon(Icons.Filled.LocationOn, contentDescription = "Go to my latest position") }
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
                onSettings = { showSettings = true },
            )
        }
    }

    if (showSettings) {
        SettingsSheet(store = store, snackbar = snackbar, resumeTick = resumeTick, onDismiss = { showSettings = false })
    }
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
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
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
private fun StatsBar(stats: Stats?) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
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
    onSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
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
                Text("Every ${formatInterval(intervalSec)}", style = MaterialTheme.typography.titleLarge)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
