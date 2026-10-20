package io.github.stephenjoy98.heatmap.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.data.DisplayMode
import io.github.stephenjoy98.heatmap.data.MapTheme
import io.github.stephenjoy98.heatmap.roads.RoadSnapWorker
import io.github.stephenjoy98.heatmap.BuildConfig
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import io.github.stephenjoy98.heatmap.review.HealthSteps
import io.github.stephenjoy98.heatmap.tracking.ActivityTracker
import androidx.health.connect.client.PermissionController
import androidx.compose.runtime.produceState
import io.github.stephenjoy98.heatmap.data.GpxExporter
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.UnitSystem
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class IntervalUnit(val label: String, val seconds: Int) {
    SEC("sec", 1), MIN("min", 60), HOUR("h", 3600), DAY("day", 86_400)
}

/** Full-screen settings, grouped into sections the way Android's own Settings app is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    store: PointStore,
    resumeTick: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val tracking by Prefs.tracking.collectAsStateWithLifecycle()
    val intervalSec by Prefs.intervalSec.collectAsStateWithLifecycle()
    val highAccuracy by Prefs.highAccuracy.collectAsStateWithLifecycle()
    val autoInterval by Prefs.autoInterval.collectAsStateWithLifecycle()
    val mode by Prefs.displayMode.collectAsStateWithLifecycle()
    val mapTheme by Prefs.mapTheme.collectAsStateWithLifecycle()
    val units by Prefs.units.collectAsStateWithLifecycle()
    val dynamicColor by Prefs.dynamicColor.collectAsStateWithLifecycle()
    val smoothRoutes by Prefs.smoothRoutes.collectAsStateWithLifecycle()
    val snapToRoads by Prefs.snapToRoads.collectAsStateWithLifecycle()
    val aiEngine by Prefs.aiEngine.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var showAiChoice by remember { mutableStateOf(false) }

    // A running recorder picks up new settings when it is (re)started.
    fun applyToService() {
        if (tracking) LocationTickerService.start(context)
    }

    fun setInterval(seconds: Int) {
        if (seconds == intervalSec) return
        Prefs.setIntervalSec(seconds)
        applyToService()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { GpxExporter.export(store, it) }
                }.isSuccess
            }
            snackbar.showSnackbar(if (ok) "Exported GPX" else "Export failed")
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    BackButton(onBack)
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Section("Recording") {
                SettingRow(
                    title = "Automatic interval",
                    subtitle = "Chooses how often to record from your movement and activity",
                ) {
                    Switch(checked = autoInterval, onCheckedChange = {
                        Prefs.setAutoInterval(it)
                        applyToService()
                    })
                }
                ItemDivider()
                Column(Modifier.padding(16.dp)) {
                    if (autoInterval) {
                        Text("Automatic", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Every second in a vehicle, 2 s cycling and 3 s walking, so routes trace every turn; " +
                                "every 15 seconds when still, and every minute after staying put for a while. " +
                                "Fast recording uses more battery while you're on the move. " +
                                "It switches as soon as activity recognition notices you moving, so allow " +
                                "physical activity access below for the best results.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Record a point every", style = MaterialTheme.typography.labelLarge)
                        IntervalSlider(intervalSec, ::setInterval)
                        CustomInterval(intervalSec, ::setInterval)
                        Text(
                            "Points less than 15 minutes apart are joined into routes; slower ticks show as dots.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ItemDivider()
                SettingRow(
                    title = "High accuracy (GPS)",
                    subtitle = if (highAccuracy) "Precise points, more battery" else "Wi-Fi/cell positioning, less battery",
                ) {
                    Switch(checked = highAccuracy, onCheckedChange = {
                        Prefs.setHighAccuracy(it)
                        applyToService()
                    })
                }
            }

            Section("Appearance") {
                Column(Modifier.padding(16.dp)) {
                    Text("Theme", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(
                        options = listOf(MapTheme.LIGHT to "Light", MapTheme.DARK to "Dark", MapTheme.SYSTEM to "System"),
                        selected = mapTheme,
                        onSelect = Prefs::setMapTheme,
                    )
                }
                ItemDivider()
                Column(Modifier.padding(16.dp)) {
                    Text("Units", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(
                        options = listOf(UnitSystem.METRIC to "Metric (km, m)", UnitSystem.IMPERIAL to "Imperial (mi, ft)"),
                        selected = units,
                        onSelect = Prefs::setUnits,
                    )
                }
                ItemDivider()
                SettingRow(
                    title = "Material expressive",
                    subtitle = if (expressiveSupported) {
                        "Colours from your wallpaper through Android's Monet engine, with rounder shapes and springier motion"
                    } else {
                        "Needs Android 12 or later for wallpaper colours"
                    },
                ) {
                    Switch(
                        checked = dynamicColor && expressiveSupported,
                        enabled = expressiveSupported,
                        onCheckedChange = Prefs::setDynamicColor,
                    )
                }
            }

            Section("Map & routes") {
                Column(Modifier.padding(16.dp)) {
                    Text("Show", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(
                        options = listOf(DisplayMode.ROUTES to "Routes", DisplayMode.POINTS to "Points"),
                        selected = mode,
                        onSelect = Prefs::setDisplayMode,
                    )
                }
                ItemDivider()
                SettingRow(
                    title = "Smooth routes",
                    subtitle = "Draw curves instead of straight hops between recorded points",
                ) {
                    Switch(checked = smoothRoutes, onCheckedChange = Prefs::setSmoothRoutes)
                }
                ItemDivider()
                SettingRow(
                    title = "Snap routes to roads",
                    subtitle = "Matches finished trips to OpenStreetMap roads and asks when unsure. " +
                        "Trip coordinates (never times) are sent to the free Valhalla service run by FOSSGIS.",
                ) {
                    Switch(checked = snapToRoads, onCheckedChange = {
                        Prefs.setSnapToRoads(it)
                        if (it) RoadSnapWorker.runNow(context)
                    })
                }
            }

            TravelModesSection()

            Section("Insights") {
                SettingRow(title = "Who writes your insights", subtitle = engineLabel(aiEngine)) {
                    OutlinedButton(onClick = { showAiChoice = true }) { Text("Change") }
                }
            }

            PermissionsSection(resumeTick) {
                scope.launch { snackbar.showSnackbar("Allow location first, then \"all the time\"") }
            }

            Section("Data") {
                ActionRow(
                    title = "Export GPX",
                    subtitle = "Save your whole history as a GPX file",
                    onClick = { exportLauncher.launch("location-ticker-${LocalDate.now()}.gpx") },
                )
                ItemDivider()
                ActionRow(
                    title = "Delete all points",
                    subtitle = "Remove your location history from this phone",
                    color = MaterialTheme.colorScheme.error,
                    onClick = { confirmClear = true },
                )
            }

            Text(
                "Location Ticker ${BuildConfig.VERSION_NAME}\nEmoji artwork provided by JoyPixels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showAiChoice) {
        AiChoiceDialog(onDismiss = { showAiChoice = false })
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all points?") },
            text = { Text("Your whole location history will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) { store.deleteAll() }
                        snackbar.showSnackbar("All points deleted")
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** A titled group of settings on a rounded card. */
@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
internal fun ItemDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun IntervalSlider(intervalSec: Int, onChange: (Int) -> Unit) {
    // Drag locally, apply once on release, so the recorder isn't restarted for every slider step.
    var position by remember(intervalSec) { mutableFloatStateOf(nearestPreset(intervalSec).toFloat()) }
    val preview = INTERVAL_PRESETS[position.roundToInt()]
    val shown = if (nearestPreset(intervalSec) == position.roundToInt()) intervalSec else preview

    Text(formatInterval(shown), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    Slider(
        value = position,
        onValueChange = { position = it },
        onValueChangeFinished = { onChange(INTERVAL_PRESETS[position.roundToInt()]) },
        valueRange = 0f..(INTERVAL_PRESETS.size - 1).toFloat(),
        steps = INTERVAL_PRESETS.size - 2,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("15 s", style = MaterialTheme.typography.labelSmall)
        Text("1 day", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CustomInterval(intervalSec: Int, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var unitIndex by remember { mutableIntStateOf(IntervalUnit.MIN.ordinal) }
    val unit = IntervalUnit.entries[unitIndex]
    val seconds = text.toLongOrNull()?.times(unit.seconds)
    val valid = seconds != null && seconds in Prefs.MIN_INTERVAL_SEC.toLong()..Prefs.MAX_INTERVAL_SEC.toLong()

    Spacer(Modifier.height(12.dp))
    Text("Or set an exact interval", style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
            modifier = Modifier.width(96.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = text.isNotEmpty() && !valid,
        )
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IntervalUnit.entries.forEach { u ->
                FilterChip(
                    selected = u == unit,
                    onClick = { unitIndex = u.ordinal },
                    label = { Text(u.label) },
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (text.isNotEmpty() && !valid) "Must be between 15 s and 1 day" else "Current: every ${formatInterval(intervalSec)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (text.isNotEmpty() && !valid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(enabled = valid, onClick = {
            onChange(seconds!!.toInt())
            text = ""
        }) { Text("Set") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        action()
    }
}

private fun nearestPreset(seconds: Int): Int =
    INTERVAL_PRESETS.indices.minBy { abs(INTERVAL_PRESETS[it] - seconds) }
