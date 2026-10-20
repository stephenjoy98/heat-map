package io.github.stephenjoy98.heatmap.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.BuildConfig
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.update.Updater
import kotlinx.coroutines.launch

/**
 * Settings › Updates: this version, whether a newer one is out on GitHub, and a button to get it, with its
 * changelog and download progress. Automatic updates download new versions and install them once you've
 * left the app.
 */
@Composable
internal fun UpdatesSection(resumeTick: Int) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsStateWithLifecycle()
    val checked by Updater.lastChecked.collectAsStateWithLifecycle()
    val auto by Prefs.autoUpdate.collectAsStateWithLifecycle()
    val wifiOnly by Prefs.updateWifiOnly.collectAsStateWithLifecycle()
    // Android asks once whether this app may install apps (its own updates).
    val canInstall = remember(resumeTick) { context.packageManager.canRequestPackageInstalls() }

    val release = when (val s = state) {
        is Updater.State.Available -> s.release
        is Updater.State.Downloading -> s.release
        is Updater.State.Ready -> s.release
        is Updater.State.Installing -> s.release
        is Updater.State.Failed -> s.release
        else -> null
    }
    val status = when (val s = state) {
        Updater.State.Idle -> "Not checked yet"
        Updater.State.Checking -> "Checking…"
        Updater.State.UpToDate -> "Up to date" +
            (if (checked > 0) " · checked ${DateUtils.getRelativeTimeSpanString(checked, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}" else "")
        is Updater.State.Available -> "Version ${s.release.label} is available"
        is Updater.State.Downloading -> "Downloading ${s.release.label}… ${(s.fraction * 100).toInt()}%"
        is Updater.State.Ready -> "Version ${s.release.label} is downloaded and ready to install"
        is Updater.State.Installing -> "Installing ${s.release.label}… the app will close and update"
        is Updater.State.Failed -> s.message
    }

    Section("Updates") {
        Column(Modifier.padding(16.dp)) {
            Text("Location Ticker ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (state is Updater.State.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            (state as? Updater.State.Downloading)?.let {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { it.fraction }, modifier = Modifier.fillMaxWidth())
            }
            if (release != null && release.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("What's new in ${release.label}", style = MaterialTheme.typography.titleSmall)
                Text(
                    release.notes.lines().filterNot { it.startsWith("<!--") }.joinToString("\n") { it.removePrefix("### ") }.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (val s = state) {
                    is Updater.State.Available, is Updater.State.Failed -> {
                        val r = release
                        if (r != null) {
                            Button(onClick = { scope.launch { Updater.downloadAndInstall(context, r, whenAway = false) } }) {
                                Text(if (s is Updater.State.Failed) "Try again" else "Download and install")
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    is Updater.State.Ready -> {
                        Button(onClick = { scope.launch { Updater.install(context, s.release, s.file) } }) { Text("Install now") }
                        Spacer(Modifier.width(8.dp))
                    }
                    else -> Unit
                }
                val busy = state is Updater.State.Checking || state is Updater.State.Downloading || state is Updater.State.Installing
                OutlinedButton(enabled = !busy, onClick = { scope.launch { Updater.check(context) } }) { Text("Check now") }
            }
            if (release != null && release.pageUrl.isNotEmpty()) {
                TextButton(onClick = { uri.openUri(release.pageUrl) }) { Text("Release page on GitHub") }
            }
        }
        if (!canInstall) {
            ItemDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow installing updates", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Android needs your OK once for Location Ticker to install its own updates",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                        )
                    }
                }) { Text("Allow") }
            }
        }
        ItemDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Update automatically", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Checks when the app opens and every 6 hours, downloads new versions and installs them once you've " +
                        "left the app. The first update asks you to confirm; after that Android 12 and later install them " +
                        "without asking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = auto, onCheckedChange = Prefs::setAutoUpdate)
        }
        ItemDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Download on Wi-Fi only", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Updates are about 65 MB. On mobile data you'll get a notification instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = wifiOnly, enabled = auto, onCheckedChange = Prefs::setUpdateWifiOnly)
        }
    }
}
