package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.update.Updater
import kotlinx.coroutines.launch

/**
 * When the app opens and finds a newer version (see [Updater]), offers it with its changelog: Update now
 * downloads and installs it straight away, showing progress; Later puts it off until the app is next opened
 * (or, with automatic updates on, until you leave the app).
 */
@Composable
fun UpdateBanner(resumeTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsStateWithLifecycle()
    val auto by Prefs.autoUpdate.collectAsStateWithLifecycle()
    // Releases put off with Later, until the app is opened again.
    var later by remember { mutableStateOf(setOf<String>()) }
    // Opening the app again (bringing it back to the front) offers it again.
    LaunchedEffect(resumeTick) { later = emptySet() }
    var updating by remember { mutableStateOf(false) }

    val release = when (val s = state) {
        is Updater.State.Available -> s.release
        is Updater.State.Ready -> s.release
        is Updater.State.Downloading -> s.release.takeIf { updating }
        is Updater.State.Installing -> s.release.takeIf { updating }
        is Updater.State.Failed -> s.release?.takeIf { updating }
        else -> null
    } ?: return
    val key = release.label + release.build
    if (key in later) return
    val close = {
        later = later + key
        updating = false
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        onDismissRequest = close,
        icon = { MaterialIcon(R.drawable.ic_system_update, size = 32) },
        title = { Text("Location Ticker ${release.label} is available") },
        text = {
            Column {
                when (val s = state) {
                    is Updater.State.Downloading -> {
                        Text("Downloading… ${(s.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                    }
                    is Updater.State.Installing -> {
                        Text("Installing… the app will close and open as the new version.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                    }
                    is Updater.State.Failed -> {
                        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                    }
                    else -> Unit
                }
                Text("What's new", style = MaterialTheme.typography.titleSmall)
                Text(
                    notesText(release),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
                if (!updating) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (auto) "Later installs it on its own once you've left the app (on Wi-Fi, if you chose that)."
                        else "You can update any time from Settings › Updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            val busy = state is Updater.State.Downloading || state is Updater.State.Installing
            if (!busy) {
                Button(onClick = {
                    updating = true
                    scope.launch {
                        val ready = state as? Updater.State.Ready
                        if (ready != null) Updater.install(context, ready.release, ready.file)
                        else Updater.downloadAndInstall(context, release, whenAway = false)
                    }
                }) { Text(if (state is Updater.State.Failed) "Try again" else "Update now") }
            }
        },
        dismissButton = {
            TextButton(onClick = close) { Text(if (updating) "Hide" else "Later") }
        },
    )
}
