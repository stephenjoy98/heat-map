package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.update.Updater
import kotlinx.coroutines.launch

/** The update sheet is offered once per app session (see [MainActivity] for when a session starts again). */
internal object UpdatePrompt {
    var offeredThisSession = false
}

/**
 * A sheet that slides up from the bottom; [content] gets a `close` that slides it back down before calling
 * [onClosed]. Used for the update offer and "What's new".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideUpSheet(onClosed: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onClosed() } }
    ModalBottomSheet(
        onDismissRequest = onClosed,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) { content(close) }
    }
}

/**
 * Offers a newer version (see [Updater]) with its changelog, in a sheet that slides up on the map screen
 * ([onHome]) once per app session: Update now downloads and installs it, showing progress; Later slides it
 * away until the app is next opened (or, with automatic updates on, until it installs once you've left the app).
 */
@Composable
fun UpdateSheet(onHome: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsStateWithLifecycle()
    val auto by Prefs.autoUpdate.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    val offered = when (val s = state) {
        is Updater.State.Available -> s.release
        is Updater.State.Ready -> s.release
        else -> null
    }
    LaunchedEffect(offered, onHome) {
        if (offered != null && onHome && !UpdatePrompt.offeredThisSession) {
            UpdatePrompt.offeredThisSession = true
            open = true
        }
    }
    if (!open) return
    val release = when (val s = state) {
        is Updater.State.Available -> s.release
        is Updater.State.Ready -> s.release
        is Updater.State.Downloading -> s.release
        is Updater.State.Installing -> s.release
        is Updater.State.Failed -> s.release
        else -> null
    }
    if (release == null) {
        SideEffect { open = false }
        return
    }

    SlideUpSheet(onClosed = {
        open = false
        updating = false
    }) { close ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            MaterialIcon(R.drawable.ic_system_update, size = 28, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Location Ticker ${release.label} is available", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text(if (updating) "Hide" else "Later") }
            val busy = state is Updater.State.Downloading || state is Updater.State.Installing
            if (!busy) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    updating = true
                    scope.launch {
                        val ready = state as? Updater.State.Ready
                        if (ready != null) Updater.install(context, ready.release, ready.file)
                        else Updater.downloadAndInstall(context, release, whenAway = false)
                    }
                }) { Text(if (state is Updater.State.Failed) "Try again" else "Update now") }
            }
        }
    }
}
