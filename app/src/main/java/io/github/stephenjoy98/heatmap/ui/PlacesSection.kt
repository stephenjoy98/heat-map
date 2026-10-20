package io.github.stephenjoy98.heatmap.ui

import android.location.Geocoder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.data.PlaceKind
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.data.SavedPlace
import io.github.stephenjoy98.heatmap.data.TrackRules
import io.github.stephenjoy98.heatmap.data.Units
import io.github.stephenjoy98.heatmap.tracking.LocationTickerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

// How often to record while settled at a place.
private val PLACE_INTERVALS = listOf(60, 300, 600, 900, 1800)

/** A spot suggested from your history, with a street name when one could be found. */
private class Suggestion(val lat: Double, val lon: Double, val hours: Double, val name: String?)

private fun kindIcon(kind: PlaceKind): ImageVector = when (kind) {
    PlaceKind.HOME -> Icons.Filled.Home
    PlaceKind.WORK -> WorkIcon
    PlaceKind.SCHOOL -> Icons.Filled.Star
    PlaceKind.OTHER -> Icons.Filled.Place
}

/**
 * Settings → Places: home, work, school and other places you spend time. While you're at one and settled,
 * recording slows to the interval set for it (every 5 minutes, say); when you start moving and leave, it goes
 * back to its usual pace. Suggestions come from where you've spent the most time.
 */
@Composable
fun PlacesSection() {
    val context = LocalContext.current
    val places by Prefs.savedPlaces.collectAsStateWithLifecycle()
    val at by Prefs.atPlace.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SavedPlace?>(null) }
    val suggestions by produceState(emptyList<Suggestion>(), places) {
        value = withContext(Dispatchers.IO) { suggest(context, places) }
    }
    fun save(list: List<SavedPlace>) {
        Prefs.setSavedPlaces(list)
        // Re-registers the geofences and picks the new intervals up straight away.
        if (Prefs.tracking.value) LocationTickerService.start(context)
    }

    Section("Places") {
        Text(
            "At home, work or another place you spend time, recording slows down while you're settled there " +
                "and speeds back up when you start moving and leave.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        )
        places.forEach { p ->
            ItemDivider()
            Row(
                Modifier.fillMaxWidth().clickable { editing = p }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(kindIcon(p.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${Units.length(p.radiusM.toDouble())} around · every ${formatInterval(p.intervalSec)} while there" +
                            if (at?.id == p.id) " · you're here now" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ItemDivider()
        Row(
            Modifier.fillMaxWidth().clickable { editing = newPlace(context, null) }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text("Add a place", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (suggestions.isNotEmpty()) {
            ItemDivider()
            Text(
                "Where you spend the most time",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
            suggestions.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name ?: "Unnamed spot", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${s.hours.roundToInt()} h recorded here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { editing = newPlace(context, s) }) { Text("Add") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    editing?.let { p ->
        val existing = places.any { it.id == p.id }
        PlaceEditor(
            place = p,
            isNew = !existing,
            onDismiss = { editing = null },
            onDelete = {
                save(places.filter { it.id != p.id })
                editing = null
            },
            onSave = { updated ->
                save(if (existing) places.map { if (it.id == updated.id) updated else it } else places + updated)
                editing = null
            },
        )
    }
}

/** A new place: at a suggestion, or where you were last recorded. */
private fun newPlace(context: android.content.Context, s: Suggestion?): SavedPlace {
    val latest = if (s == null) PointStore.get(context).latest() else null
    val taken = Prefs.savedPlaces.value.map { it.kind }
    val kind = when {
        PlaceKind.HOME !in taken -> PlaceKind.HOME
        PlaceKind.WORK !in taken -> PlaceKind.WORK
        else -> PlaceKind.OTHER
    }
    return SavedPlace(
        id = System.currentTimeMillis(),
        name = if (kind == PlaceKind.OTHER) s?.name.orEmpty() else kind.label,
        kind = kind,
        lat = s?.lat ?: latest?.lat ?: 0.0,
        lon = s?.lon ?: latest?.lon ?: 0.0,
        radiusM = SavedPlace.DEFAULT_RADIUS_M,
        intervalSec = SavedPlace.DEFAULT_INTERVAL_SEC,
    )
}

/** Top spots from your history that aren't saved places yet, a few hundred metres apart. */
@Suppress("DEPRECATION") // The callback geocoder needs API 33; this runs on a background thread.
private fun suggest(context: android.content.Context, places: List<SavedPlace>): List<Suggestion> {
    val picked = mutableListOf<Suggestion>()
    val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null
    for (spot in PointStore.get(context).frequentSpots()) {
        if (spot.hours < 2) break
        if (places.any { it.distanceTo(spot.lat, spot.lon) < it.radiusM + 200 }) continue
        if (picked.any { TrackRules.distanceMeters(it.lat, it.lon, spot.lat, spot.lon) < 400 }) continue
        val name = runCatching {
            geocoder?.getFromLocation(spot.lat, spot.lon, 1)?.firstOrNull()?.let { a ->
                listOfNotNull(a.thoroughfare ?: a.featureName, a.locality).distinct().joinToString(", ").ifEmpty { null }
            }
        }.getOrNull()
        picked += Suggestion(spot.lat, spot.lon, spot.hours, name)
        if (picked.size == 3) break
    }
    return picked
}

/**
 * Full-screen editor for a place: move the map to put the pin on it (the circle shows its size), name it,
 * pick its kind, size and how often to record while you're there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceEditor(
    place: SavedPlace,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (SavedPlace) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val camera = rememberMapCamera(place.lat, place.lon, if (place.lat == 0.0 && place.lon == 0.0) 2f else 16f)
    var name by remember { mutableStateOf(place.name) }
    var kind by remember { mutableStateOf(place.kind) }
    var radius by remember { mutableFloatStateOf(place.radiusM.toFloat()) }
    var interval by remember { mutableIntStateOf(place.intervalSec) }
    LaunchedEffect(Unit) { camera.moveTo(place.lat, place.lon, if (place.lat == 0.0 && place.lon == 0.0) 2f else 16f) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Tip("Cancel") { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } }
                    Text(if (isNew) "Add a place" else "Edit place", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            val c = camera.center ?: (place.lat to place.lon)
                            onSave(place.copy(name = name.trim().ifEmpty { kind.label }, kind = kind, lat = c.first, lon = c.second, radiusM = radius.roundToInt(), intervalSec = interval))
                        },
                    ) { Text("Save") }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    AppMap(camera = camera, layers = emptyList(), modifier = Modifier.fillMaxSize())
                    // The place is always at the middle of the map: a circle of its size and a pin.
                    val ring = MapAccent
                    Canvas(Modifier.fillMaxSize()) {
                        camera.cameraTick // Redraw as the map moves or zooms.
                        val mpp = camera.metersPerPixel
                        val c = Offset(size.width / 2, size.height / 2)
                        if (mpp > 0f) {
                            val r = radius / mpp
                            drawCircle(ring.copy(alpha = 0.18f), radius = r, center = c)
                            drawCircle(ring, radius = r, center = c, style = Stroke(2.dp.toPx()))
                        }
                        drawCircle(ring, radius = 7.dp.toPx(), center = c)
                        drawCircle(androidx.compose.ui.graphics.Color.White, radius = 7.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                    }
                    Text(
                        "Move the map to put the pin on the place",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val p = withContext(Dispatchers.IO) { PointStore.get(context).latest() }
                            if (p != null) camera.animateTo(p.lat, p.lon, 16f)
                        }
                    }) {
                        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Where I am now")
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlaceKind.entries.forEach { k ->
                            FilterChip(
                                selected = k == kind,
                                onClick = {
                                    // Keep a name the user typed; otherwise follow the kind.
                                    if (name.isBlank() || PlaceKind.entries.any { it.label == name }) name = k.label
                                    kind = k
                                },
                                label = { Text(k.label) },
                                leadingIcon = { Icon(kindIcon(k), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Size: ${Units.length(radius.toDouble())} around the pin", style = MaterialTheme.typography.labelLarge)
                    Slider(value = radius, onValueChange = { radius = (it / 10).roundToInt() * 10f }, valueRange = 50f..500f)
                    Text("While you're settled here, record every", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PLACE_INTERVALS.forEach { sec ->
                            FilterChip(selected = sec == interval, onClick = { interval = sec }, label = { Text(formatInterval(sec)) })
                        }
                    }
                    Text(
                        "As soon as you start walking or driving, recording checks about every 20 seconds, and goes back " +
                            "to its usual pace once you're out of the circle. Android also reports leaving it when location " +
                            "is allowed all the time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (!isNew) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onDelete) { Text("Delete this place", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

/** A briefcase, for work places; the core icon set has none. */
private val WorkIcon: ImageVector by lazy {
    ImageVector.Builder("Work", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes(
                "M20,6h-4L16,4c0,-1.11 -0.89,-2 -2,-2h-4c-1.11,0 -2,0.89 -2,2v2L4,6c-1.11,0 -1.99,0.89 -1.99,2L2,19c0,1.11 " +
                    "0.89,2 2,2h16c1.11,0 2,-0.89 2,-2L22,8c0,-1.11 -0.89,-2 -2,-2zM14,6h-4L10,4h4v2z"
            ),
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        )
    }.build()
}
