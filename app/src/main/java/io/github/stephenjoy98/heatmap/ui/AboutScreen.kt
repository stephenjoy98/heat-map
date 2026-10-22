package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.stephenjoy98.heatmap.BuildConfig
import io.github.stephenjoy98.heatmap.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val GITHUB_USER = "stephenjoy98"
private const val GITHUB_PROFILE = "https://github.com/$GITHUB_USER"
private const val REPO_URL = "https://github.com/$GITHUB_USER/heat-map"

/** What the app does, in a few lines for the About page. */
// What the app can do today. Keep this up to date with every release (see CLAUDE.md).
private val FEATURES = listOf(
    R.drawable.ic_map to "A heatmap of everywhere you've been, on OpenStreetMap, with terrain, 3D buildings and adjustable colours and brightness",
    R.drawable.ic_timer to "Automatic recording, from every second on the move to every minute when still",
    R.drawable.ic_home to "Saved places like home and work, where recording slows down to save battery",
    R.drawable.ic_track_changes to "Say what trip you're on from the recording tile, or let the app work it out",
    R.drawable.ic_directions_bike to "Travel modes and sub-modes you can add, rename, recolour and delete",
    R.drawable.ic_calendar_month to "A Timeline by day, week, month or year, with back-to-back trips grouped",
    R.drawable.ic_edit to "Edit trips: change their mode or times, select several, add trips, delete or restore dots",
    R.drawable.ic_merge to "Join several trips into one, with the loose dots between them",
    R.drawable.ic_undo to "Undo any change on the Timeline or any answer to a question",
    R.drawable.ic_fullscreen to "A full-screen map with only its buttons, and the blue location dot can be hidden",
    R.drawable.ic_explore to "A speedometer and a compass that turns the map north, then tilts it into 3D",
    R.drawable.ic_navigation to "Follow me: long-press the location button to keep your dot centred while you travel",
    R.drawable.ic_edit to "Long-press a route on the Timeline map to edit that trip or the loose dots there",
    R.drawable.ic_system_update to "Red dots lead to what a notification was about, and \"New\" marks sections that changed since your last look",
    R.drawable.ic_add_road to "Join sections and snap them to the road you took, or pick a road on the map",
    R.drawable.ic_help_outline to "A Questions tab on the Timeline: unclear trips, roads and heights (bridges, floors), one after another",
    R.drawable.ic_terrain to "Smooth 3D routes at one agreed height per place, following the same line as the map",
    R.drawable.ic_public to "Your profile: places explored, distance explored and time captured",
    R.drawable.ic_location_city to "Distance in each city, and the new areas you've explored and where",
    R.drawable.ic_settings to "Settings as a page per section, or all in one list",
    R.drawable.ic_auto_awesome to "Insights from on-device AI, with steps from Health Connect",
    R.drawable.ic_battery_charging_full to "Battery profiles (Performance, Default, Battery saver or your own) and battery use per interval",
    R.drawable.ic_explore to "Which satellites (GPS, Galileo, GLONASS, BeiDou…) fixed your location, and how often",
    R.drawable.ic_cloud to "Automatic backups of your dots, trips, answers and settings, with storage used on the phone and in the cloud",
    R.drawable.ic_group to "Friends: their profiles, a map of where they've been, and comparisons with yours",
    R.drawable.ic_system_update to "Updates itself from its GitHub releases: stable, new features only or beta, automatically or when you choose",
)

/** The app's launcher icon, as installed. */
@Composable
private fun AppIcon(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember {
        runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(256, 256).asImageBitmap() }.getOrNull()
    }
    if (icon != null) {
        Image(icon, contentDescription = "Location Ticker icon", modifier = modifier.clip(RoundedCornerShape(24.dp)))
    }
}

/** A library or data source the app uses, and the licence it's used under. */
private class Credit(val name: String, val licence: String, val url: String)

private val libraries = listOf(
    Credit("Jetpack Compose, Material 3 and AndroidX", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    Credit("Kotlin and kotlinx.coroutines", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    Credit("MapLibre Native for Android", "BSD 2-Clause License", "https://github.com/maplibre/maplibre-native"),
    Credit("Firebase Android SDK", "Apache License 2.0", "https://github.com/firebase/firebase-android-sdk"),
    Credit("Health Connect client", "Apache License 2.0", "https://developer.android.com/health-and-fitness/guides/health-connect"),
    Credit("Coil", "Apache License 2.0", "https://github.com/coil-kt/coil"),
    Credit("Material icons", "Apache License 2.0", "https://github.com/google/material-design-icons"),
    Credit("ZXing", "Apache License 2.0", "https://github.com/zxing/zxing"),
    Credit(
        "Google Play services (location, code scanner, sign-in) and ML Kit GenAI",
        "Android SDK License and Google APIs Terms of Service (not open source)",
        "https://developers.google.com/terms",
    ),
)

private val dataSources = listOf(
    Credit("OpenStreetMap data", "© OpenStreetMap contributors, ODbL 1.0", "https://www.openstreetmap.org/copyright"),
    Credit("OpenFreeMap map tiles and OpenMapTiles schema", "© OpenMapTiles, OpenFreeMap", "https://openfreemap.org/"),
    Credit("Terrain Tiles (elevation)", "Mapzen, USGS, NASA SRTM, EU-DEM and others", "https://github.com/tilezen/joerd/blob/master/docs/attribution.md"),
    Credit("Valhalla map matching (FOSSGIS server)", "MIT License; FOSSGIS usage policy", "https://valhalla.openstreetmap.de/"),
    Credit("JoyPixels emoji artwork (flags and globes)", "JoyPixels Free License, with attribution", "https://www.joypixels.com/licenses"),
)

/** Who made the app, its open-source licence, and the licences of everything it builds on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onChangelog: () -> Unit) {
    val uri = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    BackButton(onBack)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(Modifier.size(80.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Location Ticker", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Location Ticker quietly records where you go and turns it into a heatmap of your life: " +
                            "the streets you walk, ride and drive most glow brightest.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FEATURES.forEach { (icon, text) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MaterialIcon(icon, size = 20, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    MadeWith(onProfile = { uri.openUri(GITHUB_PROFILE) })
                }
            }

            Card {
                Column {
                    LinkRow("Changelog", "What's new in each version", dot = Attention.changelogUnread()) { onChangelog() }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    LinkRow("GitHub", "@$GITHUB_USER") { uri.openUri(GITHUB_PROFILE) }
                }
            }

            Card {
                Column(Modifier.padding(20.dp)) {
                    Text("Open source", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Location Ticker is free and open source under the GNU General Public License v3.0 or later. " +
                            "You're free to use, study, share and change it; versions you share must stay open under " +
                            "the same licence, with their source code.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Read the licence",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { uri.openUri("$REPO_URL/blob/main/LICENSE") }.padding(vertical = 8.dp),
                    )
                }
            }

            CreditsCard("Open-source libraries", libraries) { uri.openUri(it) }
            CreditsCard("Maps and data", dataSources) { uri.openUri(it) }

            Text(
                "Your location history stays on this phone unless you sign in to back it up or share routes with friends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

/** "Made with ♥ by @stephenjoy98 with the help of ✳ Claude by Anthropic in 🇨🇦 Canada". */
@Composable
private fun MadeWith(onProfile: () -> Unit) {
    val inline = mapOf(
        "heart" to inlineIcon { MaterialIcon(R.drawable.ic_favorite, size = 20, tint = Color(0xFFE53935)) },
        "claude" to inlineIcon { ClaudeMark(Modifier.size(20.dp)) },
        "canada" to inlineIcon { JoyEmoji("🇨🇦", size = 20) },
    )
    val accent = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        append("Made with ")
        appendInlineContent("heart", "love")
        append(" by ")
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("@$GITHUB_USER") }
        append("\nwith the help of ")
        appendInlineContent("claude", "Claude logo")
        append(" ")
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Claude") }
        append(" by Anthropic\nin ")
        appendInlineContent("canada", "Canadian flag")
        append(" Canada")
    }
    Text(
        text,
        inlineContent = inline,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.clickable(onClick = onProfile),
    )
}

// Centred on the middle of the letters (not the line box), and centred within its space, so each picture sits
// level with the words around it.
private fun inlineIcon(content: @Composable () -> Unit) = InlineTextContent(
    Placeholder(width = 1.3.em, height = 1.3.em, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** A simplified drawing of Claude's spark mark: tapered rays of uneven length in Claude's orange. */
@Composable
fun ClaudeMark(modifier: Modifier = Modifier) {
    val orange = Color(0xFFD97757)
    // Relative ray lengths, going round clockwise from the top.
    val rays = floatArrayOf(1f, 0.78f, 0.92f, 0.7f, 1f, 0.82f, 0.9f, 0.74f, 0.96f, 0.8f, 0.88f, 0.72f)
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val max = size.minDimension / 2
        val base = max * 0.12f
        rays.forEachIndexed { i, len ->
            val a = (i * 2 * PI / rays.size - PI / 2).toFloat()
            val tip = Offset(c.x + cos(a) * max * len, c.y + sin(a) * max * len)
            val side = Offset(-sin(a) * base, cos(a) * base)
            drawPath(
                Path().apply {
                    moveTo(c.x + side.x, c.y + side.y)
                    lineTo(tip.x + side.x * 0.35f, tip.y + side.y * 0.35f)
                    lineTo(tip.x - side.x * 0.35f, tip.y - side.y * 0.35f)
                    lineTo(c.x - side.x, c.y - side.y)
                    close()
                },
                orange,
            )
        }
        drawCircle(orange, radius = base * 1.4f, center = c)
    }
}

@Composable
private fun LinkRow(title: String, value: String, dot: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (dot) AttentionDot()
    }
}

@Composable
private fun CreditsCard(title: String, credits: List<Credit>, open: (String) -> Unit) {
    Card {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            for (c in credits) {
                Column(Modifier.fillMaxWidth().clickable { open(c.url) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                    Text(c.licence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
