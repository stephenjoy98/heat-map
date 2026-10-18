package io.github.stephenjoy98.heatmap.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.Stats
import io.github.stephenjoy98.heatmap.map.DarkTiles
import io.github.stephenjoy98.heatmap.map.HeatmapController
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The map is always dark, so keep light system bar icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { TickerTheme { TickerScreen() } }
    }
}

@Composable
private fun TickerScreen() {
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val tracking by Prefs.tracking.collectAsStateWithLifecycle()
    val intervalSec by Prefs.intervalSec.collectAsStateWithLifecycle()
    val mode by Prefs.displayMode.collectAsStateWithLifecycle()
    val changes by store.changes.collectAsStateWithLifecycle()
    val stats by produceState<Stats?>(null, changes) {
        value = withContext(Dispatchers.IO) { store.stats() }
    }

    val mapView = remember { createMapView(context) }
    val controller = remember { HeatmapController(mapView, store, scope) }
    var centered by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    resumeTick++
                }
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(mode) { controller.mode = mode }
    LaunchedEffect(changes) { controller.requestRender(400) }
    LaunchedEffect(stats) {
        val latest = stats?.latest
        if (!centered && latest != null) {
            mapView.controller.setZoom(15.0)
            mapView.controller.setCenter(GeoPoint(latest.lat, latest.lon))
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

    fun startRecording() {
        val missing = missingStartPermissions(context)
        if (missing.isEmpty()) LocationTickerService.start(context) else permissionLauncher.launch(missing)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        StatsBar(
            stats = stats,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            SnackbarHost(snackbar)
            SmallFloatingActionButton(
                onClick = {
                    val latest = stats?.latest
                    if (latest == null) {
                        scope.launch { snackbar.showSnackbar("No points recorded yet") }
                    } else {
                        mapView.controller.animateTo(GeoPoint(latest.lat, latest.lon), 16.0, 600L)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
            ) { Icon(Icons.Filled.LocationOn, contentDescription = "Go to my latest position") }
            Spacer(Modifier.size(8.dp))
            Text(
                "© OpenStreetMap contributors © CARTO",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Start).padding(start = 4.dp, bottom = 4.dp),
            )
            ControlPanel(
                tracking = tracking,
                intervalSec = intervalSec,
                onToggle = { if (tracking) LocationTickerService.stop(context) else startRecording() },
                onSettings = { showSettings = true },
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            store = store,
            snackbar = snackbar,
            resumeTick = resumeTick,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun StatsBar(stats: Stats?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(60.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (tracking) RecordRed else HeatOrange,
                ),
            ) {
                if (tracking) {
                    // Stop symbol.
                    Box(Modifier.size(20.dp).background(Color.White, RoundedCornerShape(3.dp)))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start recording", modifier = Modifier.size(34.dp))
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

private fun createMapView(context: Context): MapView =
    MapView(context).apply {
        setTileSource(DarkTiles)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        setTilesScaledToDpi(true)
        setHorizontalMapRepetitionEnabled(false)
        setVerticalMapRepetitionEnabled(false)
        val maxLat = MapView.getTileSystem().maxLatitude
        setScrollableAreaLimitLatitude(maxLat, -maxLat, 0)
        setMinZoomLevel(3.0)
        setMaxZoomLevel(19.0)
        controller.setZoom(3.0)
        controller.setCenter(GeoPoint(20.0, 0.0))
    }

private fun missingStartPermissions(context: Context): Array<String> {
    val needed = mutableListOf<String>()
    if (!LocationTickerService.hasLocationPermission(context)) {
        needed += Manifest.permission.ACCESS_FINE_LOCATION
        needed += Manifest.permission.ACCESS_COARSE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        needed += Manifest.permission.POST_NOTIFICATIONS
    }
    return needed.toTypedArray()
}
