package io.github.stephenjoy98.heatmap.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.cloud.FirebaseGate
import io.github.stephenjoy98.heatmap.data.AiEngine
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.review.OnDeviceAi
import kotlinx.coroutines.launch

private class EngineInfo(val engine: AiEngine, val title: String, val summary: String, val details: String)

private val engines = listOf(
    EngineInfo(
        AiEngine.ON_DEVICE,
        "On-device AI (Gemini Nano)",
        "Google's Gemini Nano writes your insights right on your phone. Nothing leaves the device, and it works offline.",
        "Runs through Android's AICore system service. It's only available on recent phones, such as the " +
            "Pixel 9 series and newer and the Galaxy S24 series and newer. Android downloads the model once " +
            "(a large download, best done on Wi-Fi). Answers are shorter and simpler than the cloud model's, " +
            "and it can't look anything up online. If it isn't ready when you ask, the app shows the " +
            "statistics instead.",
    ),
    EngineInfo(
        AiEngine.CLOUD,
        "Cloud AI (Gemini)",
        "Google's Gemini writes your insights in the cloud. It works on any phone and gives the richest answers.",
        "When you ask for insights, a short summary of the selected period is sent to Google through " +
            "Firebase AI Logic: distances per travel mode, how many areas you explored, the names of cities and " +
            "countries, your longest travel day and step totals. Your raw location points and routes are never " +
            "sent. It needs an internet connection and this build's Firebase setup, and Google's Gemini API " +
            "terms apply. Nothing is sent until you tap to get insights.",
    ),
    EngineInfo(
        AiEngine.STATS,
        "Statistics only (no AI)",
        "No AI. Insights shows highlights calculated from your own numbers on the phone. Fast, private and works everywhere.",
        "Highlights like total distance, your main way of getting around, new areas explored, cities visited, " +
            "your longest travel day and average steps are worked out directly from your data. You can still ask " +
            "simple questions, like \"how far did I cycle?\", and get answers from the numbers, but it won't " +
            "write free-form text.",
    ),
)

fun engineLabel(engine: AiEngine?): String = when (engine) {
    AiEngine.ON_DEVICE -> "On-device · Gemini Nano"
    AiEngine.CLOUD -> "Cloud · Gemini"
    AiEngine.STATS -> "Statistics only"
    null -> "Not chosen"
}

/** The three insight options, each with a short summary and expandable details. */
@Composable
fun AiEngineChooser(selected: AiEngine?, onSelect: (AiEngine) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusTick by remember { mutableIntStateOf(0) }
    val nano by produceState<OnDeviceAi.Status?>(null, statusTick) { value = OnDeviceAi.status() }
    var downloadText by remember { mutableStateOf<String?>(null) }
    val firebase = remember { FirebaseGate.isConfigured(context) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (info in engines) {
            val availability: String? = when (info.engine) {
                AiEngine.ON_DEVICE -> when (nano) {
                    null -> "Checking this phone…"
                    OnDeviceAi.Status.AVAILABLE -> "Ready on this phone"
                    OnDeviceAi.Status.DOWNLOADABLE -> "Supported; needs a one-time download"
                    OnDeviceAi.Status.DOWNLOADING -> "Downloading…"
                    OnDeviceAi.Status.UNAVAILABLE -> "Not supported on this phone"
                }
                AiEngine.CLOUD -> if (firebase) "Available" else "Needs Firebase set up in this build"
                AiEngine.STATS -> "Available"
            }
            val enabled = when (info.engine) {
                AiEngine.ON_DEVICE -> nano != OnDeviceAi.Status.UNAVAILABLE
                AiEngine.CLOUD -> firebase
                AiEngine.STATS -> true
            }
            EngineCard(
                info = info,
                selected = selected == info.engine,
                enabled = enabled,
                availability = availability,
                onClick = { onSelect(info.engine) },
            ) {
                if (info.engine == AiEngine.ON_DEVICE && nano == OnDeviceAi.Status.DOWNLOADABLE) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            runCatching {
                                OnDeviceAi.download().collect { downloadText = it }
                            }.onFailure { downloadText = "Download failed: ${it.message}" }
                            statusTick++
                        }
                    }) { Text("Download Gemini Nano") }
                    downloadText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun EngineCard(
    info: EngineInfo,
    selected: Boolean,
    enabled: Boolean,
    availability: String?,
    onClick: () -> Unit,
    extra: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val alpha = if (enabled) 1f else 0.5f
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onClick, enabled = enabled)
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                    availability?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(info.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(top = 2.dp)) {
                Text(if (expanded) "Less" else "More details")
            }
            if (expanded) {
                Text(info.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            extra()
        }
    }
}

/** Settings version of the chooser, plus the cloud model name for advanced users. */
@Composable
fun AiChoiceDialog(onDismiss: () -> Unit) {
    val current by Prefs.aiEngine.collectAsStateWithLifecycle()
    val model by Prefs.cloudModel.collectAsStateWithLifecycle()
    var choice by remember { mutableStateOf(current ?: AiEngine.STATS) }
    var modelText by remember { mutableStateOf(model) }
    AlertDialog(
        onDismissRequest = onDismiss,
        // Wider than the standard dialog, so the option cards have room to breathe.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        title = { Text("Who writes your insights") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Choose who writes the insights on your Insights page.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                AiEngineChooser(choice, { choice = it })
                if (choice == AiEngine.CLOUD) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = modelText,
                        onValueChange = { modelText = it },
                        label = { Text("Gemini model") },
                        supportingText = { Text("Change if Google retires the default (${Prefs.DEFAULT_CLOUD_MODEL})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Prefs.setAiEngine(choice)
                Prefs.setCloudModel(modelText)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
