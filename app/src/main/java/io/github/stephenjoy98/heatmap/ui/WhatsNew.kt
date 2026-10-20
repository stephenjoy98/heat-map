package io.github.stephenjoy98.heatmap.ui

import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.stephenjoy98.heatmap.BuildConfig
import io.github.stephenjoy98.heatmap.data.Changelog
import io.github.stephenjoy98.heatmap.data.Release
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val releaseDateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun Release.dateLabel(): String? =
    date?.let { runCatching { LocalDate.parse(it).format(releaseDateFormat) }.getOrDefault(it) }

/** Shown once after an update: the changes in every version since the one the user last saw. */
@Composable
fun WhatsNewDialog(releases: List<Release>, onDismiss: () -> Unit, onFullChangelog: () -> Unit) {
    AlertDialog(
        // As wide as the other full-width cards, rather than the platform's narrow dialog width.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        onDismissRequest = onDismiss,
        icon = { JoyEmoji("🎉", size = 32) },
        title = {
            Text(if (releases.size == 1) "What's new in ${releases[0].version}" else "What's new since your last update")
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                releases.forEachIndexed { i, r ->
                    if (i > 0) Spacer(Modifier.height(16.dp))
                    ReleaseNotes(r, showHeader = releases.size > 1)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onFullChangelog()
            }) { Text("Full changelog") }
        },
    )
}

/** One release: an optional version and date header, then each section's items as bullets. */
@Composable
fun ReleaseNotes(release: Release, showHeader: Boolean = true) {
    Column {
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Version ${release.version}", style = MaterialTheme.typography.titleMedium)
                if (release.version.toString() == BuildConfig.VERSION_NAME) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("Installed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            release.dateLabel()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
        }
        release.sections.forEach { section ->
            Text(
                section.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
            section.items.forEach { item ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Box(
                        Modifier
                            .padding(top = 8.dp, end = 10.dp)
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                    )
                    Text(item, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Every release, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val releases = remember { Changelog.load(context) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Changelog") },
                navigationIcon = {
                    BackButton(onBack)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = inner,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
            item {
                Text(
                    "Versions follow Semantic Versioning: MAJOR for changes that break existing data or sharing, " +
                        "MINOR for new features, PATCH for fixes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(releases, key = { it.version.toString() }) { r ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.padding(20.dp)) { ReleaseNotes(r) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
