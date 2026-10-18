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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import io.github.stephenjoy98.heatmap.data.MapProvider
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    store: PointStore,
    snackbar: SnackbarHostState,
    resumeTick: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracking by Prefs.tracking.collectAsStateWithLifecycle()
    val intervalSec by Prefs.intervalSec.collectAsStateWithLifecycle()
    val highAccuracy by Prefs.highAccuracy.collectAsStateWithLifecycle()
    val mode by Prefs.displayMode.collectAsStateWithLifecycle()
    val mapTheme by Prefs.mapTheme.collectAsStateWithLifecycle()
    val mapProvider by Prefs.mapProvider.collectAsStateWithLifecycle()
    val dynamicColor by Prefs.dynamicColor.collectAsStateWithLifecycle()
    val aiEngine by Prefs.aiEngine.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var showAiChoice by remember { mutableStateOf(false) }
    var healthTick by remember { mutableIntStateOf(0) }

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

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* State is re-read below on the next recomposition. */ }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted && tracking) LocationTickerService.start(context) }

    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { healthTick++ }
    val healthAvailability = remember { HealthSteps.availability(context) }
    val healthGranted by produceState(false, resumeTick, healthTick) {
        value = runCatching { HealthSteps.hasPermission(context) }.getOrDefault(false)
    }
    val hasActivity = remember(resumeTick) { ActivityTracker.hasPermission(context) }

    // Re-checked whenever the app resumes, e.g. after returning from system settings.
    val hasBackground = remember(resumeTick) { LocationTickerService.hasBackgroundPermission(context) }
    val ignoresBattery = remember(resumeTick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            SectionTitle("Recording interval")
            IntervalSlider(intervalSec, ::setInterval)
            CustomInterval(intervalSec, ::setInterval)
            Text(
                "Points less than 15 minutes apart are joined into routes; slower ticks show as dots.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Divider()
            SettingRow(
                title = "High accuracy (GPS)",
                subtitle = if (highAccuracy) "Precise points, more battery" else "Wi-Fi/cell positioning, less battery",
            ) {
                Switch(checked = highAccuracy, onCheckedChange = {
                    Prefs.setHighAccuracy(it)
                    applyToService()
                })
            }

            Divider()
            SectionTitle("Map")
            Text("Theme", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            SegmentedChoice(
                options = listOf(MapTheme.DARK to "Dark", MapTheme.LIGHT to "Light", MapTheme.SYSTEM to "System"),
                selected = mapTheme,
                onSelect = Prefs::setMapTheme,
            )
            Spacer(Modifier.height(12.dp))
            Text("Show", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            SegmentedChoice(
                options = listOf(DisplayMode.ROUTES to "Routes", DisplayMode.POINTS to "Points"),
                selected = mode,
                onSelect = Prefs::setDisplayMode,
            )
            if (BuildConfig.HAS_MAPS_KEY) {
                Spacer(Modifier.height(12.dp))
                Text("Map", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SegmentedChoice(
                    options = listOf(MapProvider.AUTO to "Google Maps", MapProvider.OSM to "OpenStreetMap"),
                    selected = if (mapProvider == MapProvider.OSM) MapProvider.OSM else MapProvider.AUTO,
                    onSelect = Prefs::setMapProvider,
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(8.dp))
                SettingRow(
                    title = "Wallpaper colours",
                    subtitle = "Material You: match the app's colours to your wallpaper",
                ) {
                    Switch(checked = dynamicColor, onCheckedChange = Prefs::setDynamicColor)
                }
            }

            Divider()
            SectionTitle("Insights and activity")
            SettingRow(title = "Review insights", subtitle = engineLabel(aiEngine)) {
                OutlinedButton(onClick = { showAiChoice = true }) { Text("Change") }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SettingRow(
                    title = "Detect how you travel",
                    subtitle = if (hasActivity) "Using motion sensors to tell walking, cycling and vehicles apart"
                    else "Allow physical activity access for better walking, cycling and vehicle detection",
                ) {
                    if (!hasActivity) {
                        OutlinedButton(onClick = { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Allow") }
                    }
                }
            }
            SettingRow(
                title = "Steps from Health Connect",
                subtitle = when {
                    healthAvailability == HealthSteps.Availability.UNSUPPORTED -> "Health Connect isn't available on this phone"
                    healthAvailability == HealthSteps.Availability.NEEDS_INSTALL -> "Install or update Health Connect to show steps"
                    healthGranted -> "Connected"
                    else -> "Show your daily steps on the Review tab"
                },
            ) {
                when {
                    healthAvailability == HealthSteps.Availability.NEEDS_INSTALL -> OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) { Text("Get") }
                    healthAvailability == HealthSteps.Availability.AVAILABLE && !healthGranted ->
                        OutlinedButton(onClick = { healthLauncher.launch(HealthSteps.permissions) }) { Text("Connect") }
                    else -> Unit
                }
            }

            if (!hasBackground || !ignoresBattery) {
                Divider()
                SectionTitle("Keep recording reliably")
                if (!hasBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    SettingRow(
                        title = "Allow location all the time",
                        subtitle = "Lets recording resume by itself after a reboot",
                    ) {
                        OutlinedButton(onClick = {
                            if (LocationTickerService.hasLocationPermission(context)) {
                                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            } else {
                                scope.launch { snackbar.showSnackbar("Start recording once first") }
                            }
                        }) { Text("Allow") }
                    }
                }
                if (!ignoresBattery) {
                    SettingRow(
                        title = "Ignore battery optimization",
                        subtitle = "Stops the system from pausing long-interval recording",
                    ) {
                        OutlinedButton(onClick = {
                            @SuppressLint("BatteryLife")
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            )
                            runCatching { context.startActivity(intent) }
                        }) { Text("Allow") }
                    }
                }
            }

            Divider()
            SectionTitle("Data")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("location-ticker-${LocalDate.now()}.gpx") }) {
                    Text("Export GPX")
                }
                TextButton(onClick = { confirmClear = true }) {
                    Text("Delete all points", color = MaterialTheme.colorScheme.error)
                }
            }
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

@Composable
private fun IntervalSlider(intervalSec: Int, onChange: (Int) -> Unit) {
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
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        action()
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(vertical = 16.dp))
}

private fun nearestPreset(seconds: Int): Int =
    INTERVAL_PRESETS.indices.minBy { abs(INTERVAL_PRESETS[it] - seconds) }
