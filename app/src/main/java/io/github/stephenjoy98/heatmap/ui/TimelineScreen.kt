package io.github.stephenjoy98.heatmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.rememberDatePickerState
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.ModeColors
import io.github.stephenjoy98.heatmap.data.PeriodStat
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.map.Route3dTracks
import io.github.stephenjoy98.heatmap.map.TileMath
import io.github.stephenjoy98.heatmap.map.heatTilePx
import io.github.stephenjoy98.heatmap.review.Insights
import io.github.stephenjoy98.heatmap.timeline.DayEntry
import io.github.stephenjoy98.heatmap.timeline.TimelineData
import io.github.stephenjoy98.heatmap.timeline.TimelinePeriod
import io.github.stephenjoy98.heatmap.timeline.TimelineRepository
import io.github.stephenjoy98.heatmap.timeline.TimelineScale
import io.github.stephenjoy98.heatmap.timeline.TimelineTiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpOffset
import io.github.stephenjoy98.heatmap.data.DeleteResult
import io.github.stephenjoy98.heatmap.data.EditablePoint
import io.github.stephenjoy98.heatmap.data.Trip
import io.github.stephenjoy98.heatmap.data.RoadQuestion
import io.github.stephenjoy98.heatmap.data.LevelQuestion
import io.github.stephenjoy98.heatmap.data.HeightGrid
import io.github.stephenjoy98.heatmap.roads.RoadSnapper
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.mutableStateMapOf
import io.github.stephenjoy98.heatmap.timeline.flattened
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.key
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.stephenjoy98.heatmap.data.Polyline
import io.github.stephenjoy98.heatmap.data.TrackRules
import io.github.stephenjoy98.heatmap.data.Units
import io.github.stephenjoy98.heatmap.roads.RoadPath
import io.github.stephenjoy98.heatmap.roads.Valhalla
import io.github.stephenjoy98.heatmap.roads.TrackSnapper
import io.github.stephenjoy98.heatmap.roads.SnapLog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.ln
import kotlin.math.min

/** Browse your history by day, week, month or year, like Google Maps' Timeline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(contentPadding: PaddingValues, resumeTick: Int, onProfile: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PointStore.get(context) }
    val dark = isDarkTheme()
    var scale by rememberSaveable { mutableStateOf(TimelineScale.DAY) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    val period = TimelinePeriod(scale, anchor)
    val changes by store.changes.collectAsStateWithLifecycle()

    // Routes are drawn in their travel mode's colour, so they're reloaded when a colour changes.
    val colors = ModeColors.version
    val data by produceState<TimelineData?>(null, period, changes, resumeTick, colors) {
        value = withContext(Dispatchers.IO) { TimelineRepository.load(context, period) }
    }

    // Trips the app couldn't classify, answered at the top of the list.
    val questions by produceState(emptyList<Trip>(), changes, resumeTick) {
        value = withContext(Dispatchers.IO) { store.tripsNeedingReview() }
    }
    val roadQuestions by produceState(emptyList<Pair<RoadQuestion, Trip?>>(), changes, resumeTick) {
        value = withContext(Dispatchers.IO) { store.openRoadQuestions().take(30).map { it to store.trip(it.tripId) } }
    }
    val levelQuestions by produceState(emptyList<LevelQuestion>(), changes, resumeTick) {
        value = withContext(Dispatchers.IO) { store.openLevelQuestions() }
    }
    // A trip to zoom to once its day has loaded (from "Show on Timeline").
    var focusTrip by remember { mutableStateOf<Long?>(null) }
    // Grouped trips that are opened up into their parts, by start time.
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    val camera = rememberMapCamera()
    val tilePx = remember { heatTilePx(context.resources.displayMetrics.density) }
    val casing = if (dark) Color(0xCC000000) else Color(0xCCFFFFFF)
    // Editing dots: pick a day, narrow down with the start–end slider or tap a dot, delete or restore.
    var editing by rememberSaveable { mutableStateOf(false) }
    val edit = remember { DotEditState(anchor) }
    var editLoading by remember { mutableStateOf(false) }
    var editReload by remember { mutableIntStateOf(0) }
    var framedDay by remember { mutableStateOf<LocalDate?>(null) }
    // Undo shows over the map, so it never covers the edit panel's buttons.
    val editSnackbar = remember { SnackbarHostState() }
    val undoSnackbar = remember { SnackbarHostState() }
    // Goes up when an answer is undone, so its question comes back to the queue.
    var questionsReset by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val tapRadiusPx = 32 * context.resources.displayMetrics.density
    var layersOpen by remember { mutableStateOf(false) }
    val route3d = remember { { box: GeoBox -> Route3dTracks.load(store, box, TravelMode.entries.toSet()) } }
    // Changing a trip's times: the day's dots with the trip's stretch picked out, adjusted with a slider.
    var retiming by remember { mutableStateOf<Trip?>(null) }
    val retime = remember { DotEditState(anchor) }
    var retimeLoading by remember { mutableStateOf(false) }
    // Adding a trip: the same slider over the day's dots, starting from this stretch (start to end times).
    var adding by remember { mutableStateOf<LongRange?>(null) }
    // Fixing a route: a stretch of dots joined into one trip, matched to roads or to a road you pick.
    var fixing by remember { mutableStateOf<LongRange?>(null) }
    var fixMode by remember { mutableStateOf(TravelMode.UNKNOWN) }
    val vias = remember { mutableStateListOf<Pair<Double, Double>>() }
    var roads by remember { mutableStateOf<List<RoadPath>>(emptyList()) }
    var chosenRoad by remember { mutableIntStateOf(-1) }
    var roadsLoading by remember { mutableStateOf(false) }
    var roadsError by remember { mutableStateOf<String?>(null) }
    // Fixing a route: the line as it's drawn now, shown under the new ones until you save.
    var fixCurrent by remember { mutableStateOf<List<DoubleArray>>(emptyList()) }
    val startFix = { from: Long, to: Long, mode: TravelMode ->
        vias.clear()
        roads = emptyList()
        chosenRoad = -1
        roadsError = null
        fixMode = mode
        fixing = from..to
    }
    // Breaking a trip in two: the trip's dots, with the first part picked out up to the split.
    var splitting by remember { mutableStateOf<Trip?>(null) }
    val rangeEditing = retiming != null || adding != null || fixing != null || splitting != null
    val mapEditing = editing || rangeEditing
    // Picking several trips to change their mode together; null when not picking.
    var picked by remember { mutableStateOf<Set<Long>?>(null) }
    // A section long-pressed on the map, waiting for you to confirm what to do with it.
    var mapPick by remember { mutableStateOf<MapPick?>(null) }
    // The trip (or trips) tapped in the list or long-pressed on the map, drawn on top so you can see which it is.
    var highlight by remember { mutableStateOf<List<DoubleArray>>(emptyList()) }
    // Editing panels (dots, times, a new trip, fixing a route, splitting) share this height limit and scroll inside.
    val panelMax = (LocalConfiguration.current.screenHeightDp * 0.46f).dp
    LaunchedEffect(period) { highlight = emptyList() }
    fun showTrips(trips: List<Trip>, box: GeoBox?) {
        box?.let { frame(camera, it) }
        scope.launch { highlight = withContext(Dispatchers.IO) { trips.map { store.tripDisplayPath(it.id) } } }
    }
    val mapHaptics = LocalHapticFeedback.current
    val select = { ids: List<Long>, on: Boolean ->
        val now = picked.orEmpty()
        picked = if (on) now + ids else now - ids.toSet()
    }

    val layers = remember(data, dark, mapEditing) {
        val d = data ?: return@remember emptyList()
        // While editing, the routes fade back so the dots stand out.
        listOf(MapLayer("timeline-${d.period}-$colors", TimelineTiles(d.routes, tilePx, casing.toArgb()), alpha = if (mapEditing) 0.35f else 1f))
    }

    LaunchedEffect(retiming, adding, fixing, splitting) {
        val window = retiming?.let { it.startTime..it.endTime } ?: splitting?.let { it.startTime..it.endTime } ?: adding ?: fixing
            ?: return@LaunchedEffect
        retimeLoading = true
        val from = minOf(period.fromMs, window.first)
        val to = maxOf(period.toMs, window.last + 1)
        val pts = withContext(Dispatchers.IO) { store.editablePoints(from, to) }
        retime.load(pts)
        if (pts.isNotEmpty()) {
            var first = pts.indexOfFirst { it.time >= window.first }
            var last = pts.indexOfLast { it.time <= window.last }
            if (first < 0 || last < first) {
                // No dots inside the stretch (a stay with a dot only at each end, say): take the dots on either side
                // of it, so the range stays where it was asked for instead of jumping elsewhere in the day.
                val before = pts.indexOfLast { it.time <= window.first }
                val after = pts.indexOfFirst { it.time >= window.last }
                first = if (before >= 0) before else after.coerceAtLeast(0)
                last = if (after >= 0) after else pts.size - 1
                if (last <= first) {
                    if (last < pts.size - 1) last = first + 1 else first = (last - 1).coerceAtLeast(0)
                }
            }
            retime.range = first..last.coerceAtMost(pts.size - 1)
            // Splitting starts half way through the trip.
            if (splitting != null && last - first >= 2) retime.range = first..(first + last) / 2
        }
        retimeLoading = false
        frameDots(camera, retime.target)
    }
    BackHandler(enabled = rangeEditing && !LocalCovered.current) {
        retiming = null
        adding = null
        fixing = null
        splitting = null
    }
    // Ways between the first and last dot picked, through any points tapped on the map.
    // Snapping shows each step in a dialog as it goes, so when it fails you can see which step it was.
    val snapSteps = remember { mutableStateListOf<SnapStep>() }
    var snapDialog by remember { mutableStateOf(false) }
    fun runSnap(empty: String, work: (SnapLog) -> List<RoadPath>) {
        snapSteps.clear()
        snapDialog = true
        roadsLoading = true
        roadsError = null
        val log = object : SnapLog {
            override fun step(text: String) {
                scope.launch(Dispatchers.Main) {
                    snapSteps.finishRunning(failed = false)
                    snapSteps += SnapStep(text, SnapStep.State.RUNNING)
                }
            }
            override fun fail(text: String) {
                scope.launch(Dispatchers.Main) {
                    snapSteps.finishRunning(failed = true)
                    snapSteps += SnapStep(text, SnapStep.State.FAILED)
                }
            }
        }
        scope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { work(log) } }
            roads = found.getOrDefault(emptyList())
            chosenRoad = if (roads.isNotEmpty()) 0 else -1
            val error = found.exceptionOrNull()
            when {
                error != null -> {
                    val why = "${error.javaClass.simpleName}: ${error.message ?: "no details"}"
                    snapSteps.finishRunning(failed = true)
                    snapSteps += SnapStep(why, SnapStep.State.FAILED)
                    roadsError = "Snapping failed. $why"
                }
                roads.isEmpty() -> {
                    if (snapSteps.none { it.state == SnapStep.State.FAILED }) {
                        snapSteps.finishRunning(failed = true)
                        snapSteps += SnapStep(empty, SnapStep.State.FAILED)
                    }
                    roadsError = snapSteps.lastOrNull { it.state == SnapStep.State.FAILED }?.text ?: empty
                }
                else -> {
                    snapSteps.finishRunning(failed = false)
                    val km = Units.distance(polylineKm(roads.first().points))
                    snapSteps += SnapStep(if (roads.size == 1) "Done: $km" else "Done: ${roads.size} ways, the first $km", SnapStep.State.DONE)
                    delay(700)
                    snapDialog = false
                }
            }
            roadsLoading = false
        }
    }
    fun findRoads() {
        val pts = retime.points
        if (pts.isEmpty()) return
        val a = pts[retime.range.first]
        val b = pts[retime.range.last]
        val costing = Valhalla.costing(fixMode) ?: "auto"
        // Trains and boats follow the railway or ferry route instead of roads.
        val network = TrackSnapper.networkFor(fixMode)
        val stretch = pts.subList(retime.range.first, retime.range.last + 1)
        val stops = listOf(a.lat to a.lon) + vias + (b.lat to b.lon)
        val empty = when (network) {
            TrackSnapper.Network.FERRY -> "No ferry route found between these points."
            TrackSnapper.Network.RAIL -> "No railway found between these points."
            null -> "No road found between these points."
        }
        runSnap(empty) { log ->
            when {
                network != null && vias.isEmpty() -> listOfNotNull(
                    TrackSnapper.snap(DoubleArray(stretch.size) { stretch[it].lat }, DoubleArray(stretch.size) { stretch[it].lon }, network, log)
                        ?.let { RoadPath(it, null) }
                )
                network != null -> listOfNotNull(TrackSnapper.route(stops, network, log)?.let { RoadPath(it, null) })
                vias.isEmpty() -> {
                    log.step("Asking the Valhalla routing service for roads between the two ends")
                    Valhalla.routes(a.lat, a.lon, b.lat, b.lon, costing).also { if (it.isEmpty()) log.fail("Valhalla found no road between these points") }
                }
                else -> {
                    log.step("Asking the Valhalla routing service for the road through ${stops.size} points")
                    listOfNotNull(Valhalla.routeThrough(stops, costing)).also { if (it.isEmpty()) log.fail("Valhalla found no road through these points") }
                }
            }
        }
    }
    // The stretch matched to roads (or tracks) the way it would be on its own, to look at before saving.
    fun previewSnap() {
        val pts = retime.points
        if (pts.size < 2) return
        val stretch = pts.subList(retime.range.first, retime.range.last + 1)
        if (stretch.size < 2) return
        val network = TrackSnapper.networkFor(fixMode)
        val costing = Valhalla.costing(fixMode) ?: "auto"
        // Long stretches are thinned, like trips matched in the background.
        val step = maxOf(1, stretch.size / 1500)
        val keep = (stretch.indices step step).toMutableList().apply { if (last() != stretch.lastIndex) add(stretch.lastIndex) }
        val lat = DoubleArray(keep.size) { stretch[keep[it]].lat }
        val lon = DoubleArray(keep.size) { stretch[keep[it]].lon }
        runSnap("Couldn't match this stretch. Try Find roads, or tap the map where you went.") { log ->
            val line = if (network != null) {
                TrackSnapper.snap(lat, lon, network, log)
            } else {
                log.step("Sending ${lat.size} dots to the Valhalla routing service to match them to roads")
                Valhalla.match(lat, lon, costing).also { if (it == null) log.fail("Valhalla couldn't match these dots to roads") }
            }
            listOfNotNull(line?.let { RoadPath(it, AUTO_MATCH) })
        }
    }
    // While fixing, keep the line as it's drawn now on the map, for the stretch picked.
    LaunchedEffect(fixing, retime.range, retime.points) {
        if (fixing == null || retime.points.size < 2) {
            fixCurrent = emptyList()
            return@LaunchedEffect
        }
        // A new stretch needs its road found or snapped again before saving.
        roads = emptyList()
        chosenRoad = -1
        delay(250)
        val r = retime.range
        val from = retime.points[r.first.coerceIn(0, retime.points.lastIndex)].time
        val to = retime.points[r.last.coerceIn(0, retime.points.lastIndex)].time
        fixCurrent = withContext(Dispatchers.IO) {
            val trips = store.tripsBetween(from, to + 1).filter { it.endTime >= from && it.startTime <= to }
            if (trips.isEmpty()) {
                val pts = store.editablePoints(from, to + 1)
                listOf(DoubleArray(pts.size * 2) { if (it % 2 == 0) pts[it / 2].lat else pts[it / 2].lon })
            } else {
                trips.map { store.tripDisplayPath(it.id) }
            }
        }
    }
    BackHandler(enabled = picked != null && !LocalCovered.current) { picked = null }
    // Every change to trips can be undone for a while: how trips looked around it is kept, then put back on Undo.
    val undoable = { label: String, from: Long, to: Long, change: () -> DeleteResult ->
        scope.launch {
            val (snap, result) = withContext(Dispatchers.IO) { store.snapshot(from, to) to change() }
            afterDotEdit(context, result)
            undoSnackbar.currentSnackbarData?.dismiss()
            if (undoSnackbar.showSnackbar(label, actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                afterDotEdit(context, withContext(Dispatchers.IO) { store.restore(snap) })
                questionsReset++
            }
        }
        Unit
    }
    // Moving a dot, or adding one by hand, can be undone too: the dot goes back and the trips around it with it.
    fun dotEdit(label: String, dot: EditablePoint, change: () -> DeleteResult, back: () -> DeleteResult) {
        if (edit.busy) return
        edit.busy = true
        scope.launch {
            val (snap, result) = withContext(Dispatchers.IO) { store.snapshot(dot.time, dot.time) to change() }
            afterDotEdit(context, result)
            editReload++
            editSnackbar.currentSnackbarData?.dismiss()
            if (editSnackbar.showSnackbar(label, actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                afterDotEdit(context, withContext(Dispatchers.IO) { back(); store.restore(snap) })
                edit.reselect = dot.id
                editReload++
            }
        }
    }
    fun moveDot(dot: EditablePoint, lat: Double, lon: Double) {
        edit.placing = null
        edit.reselect = dot.id
        dotEdit("Dot moved", dot, { store.movePoint(dot.id, lat, lon) }, { store.movePoint(dot.id, dot.lat, dot.lon, dot.accuracy) })
    }
    fun addDot(after: EditablePoint, lat: Double, lon: Double) {
        var added = -1L
        dotEdit(
            "Dot added", after,
            {
                val made = store.addPointAfter(after.id, lat, lon)
                added = made?.first ?: -1L
                // The new dot is picked next, so taps keep adding along the way.
                edit.reselect = made?.first ?: after.id
                made?.second ?: DeleteResult(emptySet(), emptyList())
            },
            { if (added > 0) store.removeAddedPoint(added) else DeleteResult(emptySet(), emptyList()) },
        )
    }
    val changeModes = { trips: List<Trip>, mode: TravelMode ->
        if (trips.isNotEmpty()) {
            val label = if (trips.size == 1) "Changed to ${mode.fullLabel}" else "${trips.size} trips changed to ${mode.fullLabel}"
            undoable(label, trips.minOf { it.startTime }, trips.maxOf { it.endTime }) {
                store.setTripModes(trips.map { it.id }, mode)
                DeleteResult(trips.map { Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.toSet(), emptyList())
            }
        }
        Unit
    }
    val changeMode = { trip: Trip, mode: TravelMode -> changeModes(listOf(trip), mode) }
    // Picked trips and stays together (stays are picked by -start, see stayKey): Change mode turns each stay into a
    // trip of that mode and changes the trips, all in one undoable step.
    val changePicked = { trips: List<Trip>, stays: List<DayEntry.Stay>, mode: TravelMode ->
        if (stays.isEmpty()) {
            changeModes(trips, mode)
        } else {
            val from = minOf(trips.minOfOrNull { it.startTime } ?: Long.MAX_VALUE, stays.minOf { it.start })
            val to = maxOf(trips.maxOfOrNull { it.endTime } ?: Long.MIN_VALUE, stays.maxOf { it.end })
            val n = trips.size + stays.size
            undoable(if (n == 1) "Changed to ${mode.fullLabel}" else "$n changed to ${mode.fullLabel}", from, to) {
                val days = HashSet<String>()
                val remote = ArrayList<String>()
                if (trips.isNotEmpty()) {
                    store.setTripModes(trips.map { it.id }, mode)
                    trips.forEach { days += Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
                }
                for (s in stays) {
                    val r = store.addTrip(s.start + 1, s.end - 1, mode)
                    days += r.days
                    remote += r.removedRemoteIds
                }
                DeleteResult(days, remote)
            }
        }
        Unit
    }
    // Joins trips into one, from the first one's start to the last one's end, taking in any loose dots
    // between them. It keeps the mode of the longest of them.
    val joinTrips = { trips: List<Trip> ->
        if (trips.size >= 2) {
            val mode = trips.maxBy { it.distanceM }.mode
            val start = trips.minOf { it.startTime }
            val end = trips.maxOf { it.endTime }
            undoable("${trips.size} trips joined", start, end) { store.addTrip(start, end, mode) }
        }
        Unit
    }
    // Joins picked trips and stays into one trip, from the first one's start to the last one's end (the stays in
    // it become part of the trip). It keeps the mode of the longest trip, or Other if only stays were picked.
    val joinPicked = { trips: List<Trip>, stays: List<DayEntry.Stay> ->
        if (stays.isEmpty()) {
            joinTrips(trips)
        } else if (trips.size + stays.size >= 2) {
            val mode = trips.maxByOrNull { it.distanceM }?.mode ?: TravelMode.UNKNOWN
            val start = minOf(trips.minOfOrNull { it.startTime } ?: Long.MAX_VALUE, stays.minOf { it.start + 1 })
            val end = maxOf(trips.maxOfOrNull { it.endTime } ?: Long.MIN_VALUE, stays.maxOf { it.end - 1 })
            undoable("Joined into one trip", start, end) { store.addTrip(start, end, mode) }
        }
        Unit
    }

    LaunchedEffect(editing, edit.day, edit.showingDeleted, editReload) {
        if (!editing) {
            framedDay = null
            return@LaunchedEffect
        }
        editLoading = true
        val zone = ZoneId.systemDefault()
        val from = edit.day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = edit.day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val (pts, deleted) = withContext(Dispatchers.IO) {
            val deleted = store.deletedPoints(from, to)
            (if (edit.showingDeleted) deleted else store.editablePoints(from, to)) to deleted.size
        }
        edit.deletedCount = deleted
        edit.load(pts)
        editLoading = false
        if (framedDay != edit.day) {
            framedDay = edit.day
            frameDots(camera, pts)
        }
    }
    val stopEditing = {
        // Land on the day that was being edited.
        anchor = edit.day
        scale = TimelineScale.DAY
        editing = false
    }
    BackHandler(enabled = editing && !LocalCovered.current) { stopEditing() }
    // Frame the period's travels once when a new period loads. Reloads of the same period (every new dot
    // while recording) leave the camera alone, so a section you tapped stays in view.
    var framedPeriod by remember { mutableStateOf<TimelinePeriod?>(null) }
    LaunchedEffect(data) {
        val d = data ?: return@LaunchedEffect
        val box = d.bounds ?: return@LaunchedEffect
        if (d.period != framedPeriod) {
            framedPeriod = d.period
            frame(camera, box)
        }
        focusTrip?.let { id ->
            d.entries.flattened().firstOrNull { it is DayEntry.Move && it.trip.id == id }?.let { e ->
                (e as DayEntry.Move).box?.let { frame(camera, it) }
                focusTrip = null
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(
            title = {
                Text(
                    when {
                        retiming != null -> "Change times"
                        splitting != null -> "Split trip"
                        adding != null -> "Add a trip"
                        fixing != null -> "Fix route"
                        picked != null -> "${picked!!.size} selected"
                        editing -> "Edit dots"
                        else -> "Timeline"
                    }
                )
            },
            actions = {
                if (!mapEditing && scale == TimelineScale.DAY && picked == null) {
                    Tip("Add a trip on this day") {
                        IconButton(onClick = { adding = period.fromMs..period.fromMs }) { Icon(Icons.Filled.Add, contentDescription = "Add a trip") }
                    }
                }
                if (!mapEditing) {
                    Tip("Edit or delete dots for this day") {
                        IconButton(onClick = {
                            edit.day = anchor
                            edit.showingDeleted = false
                            editing = true
                        }) { Icon(Icons.Filled.Edit, contentDescription = "Edit dots") }
                    }
                }
                ProfileButton(onClick = onProfile)
            },
        )
        if (!mapEditing && picked == null) {
            val waiting = questions.size + roadQuestions.size + levelQuestions.size
            SecondaryTabRow(selectedTabIndex = if (TimelineTabs.questions) 1 else 0) {
                Tab(selected = !TimelineTabs.questions, onClick = { TimelineTabs.questions = false }, text = { Text("Trips") })
                Tab(
                    selected = TimelineTabs.questions,
                    onClick = { TimelineTabs.questions = true },
                    text = {
                        // The count beside the word, not over it.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Questions")
                            if (waiting > 0) {
                                Spacer(Modifier.width(6.dp))
                                Badge { Text(if (waiting > 99) "99+" else waiting.toString()) }
                            }
                        }
                    },
                )
            }
        }
        if (TimelineTabs.questions && !mapEditing && picked == null) {
            QuestionsPane(
                trips = questions,
                roads = roadQuestions,
                levels = levelQuestions,
                onTrip = { trip, mode -> changeMode(trip, mode) },
                onShowDay = { trip ->
                    anchor = Instant.ofEpochMilli(trip.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
                    scale = TimelineScale.DAY
                    focusTrip = trip.id
                    TimelineTabs.questions = false
                },
                onRoad = { q, choice ->
                    scope.launch {
                        withContext(Dispatchers.IO) { RoadSnapper.answer(context, q, choice) }
                        undoSnackbar.currentSnackbarData?.dismiss()
                        if (undoSnackbar.showSnackbar("Road saved", actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                            withContext(Dispatchers.IO) { RoadSnapper.answer(context, q, -1) }
                            questionsReset++
                        }
                    }
                },
                onLevel = { q, different ->
                    scope.launch {
                        withContext(Dispatchers.IO) { store.answerLevel(q.area, if (different) HeightGrid.DIFFERENT else HeightGrid.SAME) }
                        undoSnackbar.currentSnackbarData?.dismiss()
                        if (undoSnackbar.showSnackbar("Answer saved", actionLabel = "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                            withContext(Dispatchers.IO) { store.answerLevel(q.area, HeightGrid.ASK) }
                            questionsReset++
                        }
                    }
                },
                reset = questionsReset,
                modifier = Modifier.weight(1f),
            )
        } else {
            if (!mapEditing) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    TimelineScale.entries.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = s == scale,
                            onClick = { scale = s },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = TimelineScale.entries.size),
                        ) { Text(s.label, maxLines = 1) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tip("Previous ${scale.label.lowercase()}") {
                        IconButton(onClick = { anchor = period.previous().anchor }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
                        }
                    }
                    TextButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                        Text(period.label, style = MaterialTheme.typography.titleMedium)
                    }
                    Tip("Next ${scale.label.lowercase()}") {
                        IconButton(onClick = { anchor = period.next().anchor }, enabled = !period.isCurrent) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (mapEditing) Modifier.weight(1f) else Modifier.height(260.dp))
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                AppMap(
                    camera = camera,
                    layers = layers,
                    modifier = Modifier.fillMaxSize(),
                    refreshKey = data,
                    cacheKey = "timeline",
                    route3d = route3d,
                    // Long-press a route to edit that section, after a confirmation.
                    onMapLongClick = if (mapEditing || picked != null) null else { lat, lon ->
                        val maxM = maxOf(30.0, tapRadiusPx * camera.metersPerPixel.toDouble())
                        scope.launch {
                            val pick = withContext(Dispatchers.IO) { findMapPick(store, period.fromMs, period.toMs, lat, lon, maxM) }
                            if (pick != null) {
                                mapHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                mapPick = pick
                                highlight = withContext(Dispatchers.IO) {
                                    val trip = pick.trip
                                    if (trip != null) listOf(store.tripDisplayPath(trip.id))
                                    else store.editablePoints(pick.window.first, pick.window.last + 1).let { pts ->
                                        listOf(DoubleArray(pts.size * 2) { if (it % 2 == 0) pts[it / 2].lat else pts[it / 2].lon })
                                    }
                                }
                            }
                        }
                    },
                    onMapClick = when {
                        editing -> { lat, lon ->
                            val dot = edit.selected?.let { edit.points.getOrNull(it) }
                            when {
                                dot != null && edit.placing == DotPlacing.MOVE -> moveDot(dot, lat, lon)
                                dot != null && edit.placing == DotPlacing.ADD -> addDot(dot, lat, lon)
                                else -> nearestDot(camera, edit, lat, lon, tapRadiusPx)?.let { edit.selected = it }
                            }
                        }
                        // Tapping a dot moves whichever end of the trip is nearer to it.
                        // Fixing a route: a tap says the route went through there.
                        fixing != null -> { lat, lon ->
                            if (vias.size < MAX_VIAS) {
                                vias += lat to lon
                                findRoads()
                            }
                        }
                        // Splitting: a tap picks the dot the first part ends on.
                        splitting != null -> { lat, lon ->
                            val trip = splitting
                            nearestDot(camera, retime, lat, lon, tapRadiusPx)?.let { i ->
                                val pts = retime.points
                                val first = pts.indexOfFirst { it.time >= trip!!.startTime }
                                val last = pts.indexOfLast { it.time <= trip!!.endTime }
                                if (first >= 0 && last - first >= 2) retime.range = first..i.coerceIn(first, last - 1)
                            }
                        }
                        rangeEditing -> { lat, lon ->
                            nearestDot(camera, retime, lat, lon, tapRadiusPx)?.let { i ->
                                val r = retime.range
                                retime.range = if (abs(i - r.first) <= abs(i - r.last)) i..r.last else r.first..i
                            }
                        }
                        // A tap on the map lets go of a highlighted trip.
                        highlight.isNotEmpty() -> { _, _ -> highlight = emptyList() }
                        else -> null
                    },
                )
                if (!editing && !rangeEditing && highlight.isNotEmpty()) TripHighlight(camera, highlight, Modifier.fillMaxSize())
                ScaleBar(camera, Modifier.align(Alignment.BottomStart).padding(8.dp))
                // Routes or dots: Dots shows every recorded dot of the day; tap one to pick it and delete it.
                if (!rangeEditing && picked == null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                        shadowElevation = 3.dp,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    ) {
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = !editing,
                                onClick = { if (editing) stopEditing() },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("Routes") }
                            SegmentedButton(
                                selected = editing,
                                onClick = {
                                    if (!editing) {
                                        edit.day = anchor
                                        edit.showingDeleted = false
                                        editing = true
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("Dots") }
                        }
                    }
                }
                if (editing) {
                    DotsOverlay(camera, edit, Modifier.fillMaxSize())
                    // Map type and 3D buildings, right where you're editing.
                    LayersButton(onClick = { layersOpen = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                    SwipeSnackbarHost(editSnackbar, Modifier.align(Alignment.TopCenter))
                } else if (rangeEditing) {
                    DotsOverlay(camera, retime, Modifier.fillMaxSize())
                    if (fixing != null) {
                        // The route as it's drawn now (white), under the new one to pick or confirm.
                        if (fixCurrent.isNotEmpty()) TripHighlight(camera, fixCurrent, Modifier.fillMaxSize())
                        RouteLinesOverlay(camera, roads, chosenRoad, vias, Modifier.fillMaxSize())
                    }
                    LayersButton(onClick = { layersOpen = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                } else if (data == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            if (editing) {
                Box(Modifier.padding(12.dp).heightIn(max = panelMax)) {
                    EditPanel(
                        state = edit,
                        loading = editLoading,
                        onDay = { edit.day = it },
                        onFocus = { camera.animateTo(it.lat, it.lon, 17f) },
                        onShow = { frameDots(camera, it) },
                        onDelete = { doomed ->
                            scope.launch {
                                afterDotEdit(context, withContext(Dispatchers.IO) { store.deletePoints(doomed.map { it.id }) })
                                editReload++
                                val undo = editSnackbar.showSnackbar(
                                    if (doomed.size == 1) "Dot deleted" else "${doomed.size} dots deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Long,
                                )
                                if (undo == SnackbarResult.ActionPerformed) {
                                    afterDotEdit(context, withContext(Dispatchers.IO) { store.restorePoints(doomed.map { it.id }) })
                                    editReload++
                                }
                            }
                        },
                        onRestore = { back ->
                            scope.launch {
                                afterDotEdit(context, withContext(Dispatchers.IO) { store.restorePoints(back.map { it.id }) })
                                editReload++
                                editSnackbar.showSnackbar(if (back.size == 1) "Dot restored" else "${back.size} dots restored")
                            }
                        },
                        onClose = stopEditing,
                    )
                }
            } else if (fixing != null) {
                Box(Modifier.padding(12.dp).heightIn(max = panelMax)) {
                    RouteFixPanel(
                        state = retime,
                        loading = retimeLoading,
                        mode = fixMode,
                        onMode = { fixMode = it },
                        roads = roads,
                        chosen = chosenRoad,
                        onChoose = { chosenRoad = it },
                        roadsLoading = roadsLoading,
                        error = roadsError,
                        vias = vias.size,
                        onFindRoads = ::findRoads,
                        onPreview = ::previewSnap,
                        onClearVias = {
                            vias.clear()
                            roads = emptyList()
                            chosenRoad = -1
                        },
                        onCancel = { fixing = null },
                        onSave = { start, end ->
                            val road = roads.getOrNull(chosenRoad)
                            val mode = fixMode
                            undoable("Route fixed", start, end) {
                                store.addTrip(start, end, mode).also { r ->
                                    // The road picked becomes the trip's shape; otherwise it's matched to roads as usual.
                                    if (road != null && r.createdTripId > 0) {
                                        val n = road.points.size / 2
                                        val poly = Polyline.encode(DoubleArray(n) { road.points[it * 2] }, DoubleArray(n) { road.points[it * 2 + 1] })
                                        store.saveTripPath(r.createdTripId, RoadSnapper.STATUS_SNAPPED, poly, poly)
                                    }
                                }
                            }
                            fixing = null
                        },
                    )
                }
            } else if (splitting != null) {
                val trip = splitting!!
                Box(Modifier.padding(12.dp).heightIn(max = panelMax)) {
                    SplitPanel(
                        trip = trip,
                        state = retime,
                        loading = retimeLoading,
                        onCancel = { splitting = null },
                        onSave = { at ->
                            undoable("Trip split", trip.startTime, trip.endTime) { store.splitTrip(trip.id, at) }
                            splitting = null
                        },
                    )
                }
            } else if (rangeEditing) {
                val trip = retiming
                Box(Modifier.padding(12.dp).heightIn(max = panelMax)) {
                    key(trip?.id, adding) {
                        RetimePanel(
                            trip = trip,
                            state = retime,
                            loading = retimeLoading,
                            entries = data?.entries.orEmpty(),
                            onCancel = {
                                retiming = null
                                adding = null
                            },
                            onSave = { start, end, mode ->
                                if (trip != null) {
                                    undoable("Times changed", minOf(start, trip.startTime), maxOf(end, trip.endTime)) { store.retimeTrip(trip.id, start, end) }
                                } else {
                                    undoable("Trip added", start, end) { store.addTrip(start, end, mode ?: TravelMode.UNKNOWN) }
                                }
                                retiming = null
                                adding = null
                            },
                        )
                    }
                }
            } else {
                val d = data
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (d == null) return@LazyColumn
                    item { Summary(d) }
                    when (scale) {
                        TimelineScale.DAY -> {
                            if (d.entries.isEmpty()) item { Empty("Nothing recorded on this day.") }
                            items(d.entries, key = { "${it.javaClass.simpleName}-${it.start}" }) { entry ->
                                when (entry) {
                                    is DayEntry.Move -> MoveRow(
                                        entry = entry,
                                        onClick = { showTrips(listOf(entry.trip), entry.box) },
                                        onMode = { changeMode(entry.trip, it) },
                                        onRetime = { retiming = entry.trip },
                                        onSplit = { splitting = entry.trip },
                                        onFix = { startFix(entry.trip.startTime, entry.trip.endTime, entry.trip.mode) },
                                        selection = picked,
                                        onSelect = select,
                                    )
                                    is DayEntry.Stay -> StayRow(
                                        entry = entry,
                                        onClick = {
                                            highlight = emptyList()
                                            camera.animateTo(entry.lat, entry.lon, 16f)
                                        },
                                        onAddTrip = { adding = (entry.start + 1)..(entry.end - 1) },
                                        picked = picked?.contains(stayKey(entry)),
                                        onSelect = { on -> select(listOf(stayKey(entry)), on) },
                                    )
                                    is DayEntry.Group -> GroupRow(
                                        group = entry,
                                        expanded = expanded[entry.start] == true,
                                        onToggle = { expanded[entry.start] = expanded[entry.start] != true },
                                        onClick = { showTrips(entry.moves.map { it.trip }, entry.box) },
                                        onModeAll = { m -> changeModes(entry.moves.map { it.trip }, m) },
                                        onJoin = { joinTrips(entry.moves.map { it.trip }) },
                                        onSectionClick = { mv -> showTrips(listOf(mv.trip), mv.box) },
                                        onSectionMode = { mv, m -> changeMode(mv.trip, m) },
                                        onSectionRetime = { mv -> retiming = mv.trip },
                                        onSectionSplit = { mv -> splitting = mv.trip },
                                        onFix = { mv -> startFix(mv?.trip?.startTime ?: entry.start, mv?.trip?.endTime ?: entry.end, mv?.trip?.mode ?: entry.mode) },
                                        selection = picked,
                                        onSelect = select,
                                    )
                                }
                            }
                            if (d.entries.any { it is DayEntry.Move || it is DayEntry.Group }) {
                                item {
                                    Hint(
                                        "Long-press a trip to change how you travelled or when it started and ended. " +
                                            "Trips in a row in the same mode are shown together: long-press to see each part. To make several trips one, " +
                                            "pick Join into one trip from a group's menu, or Select them and tap Join."
                                    )
                                }
                            }
                        }
                        else -> {
                            if (d.stats.isEmpty()) item { Empty("Nothing recorded in this ${scale.label.lowercase()}.") }
                            val max = d.stats.maxOfOrNull { it.meters } ?: 0.0
                            items(d.stats, key = { it.key }) { stat ->
                                PeriodRow(stat, scale, max) {
                                    if (scale == TimelineScale.YEAR) {
                                        anchor = YearMonth.parse(stat.key).atDay(1)
                                        scale = TimelineScale.MONTH
                                    } else {
                                        anchor = LocalDate.parse(stat.key)
                                        scale = TimelineScale.DAY
                                    }
                                }
                            }
                        }
                    }
                }
                picked?.let { ids ->
                    val flat = data?.entries.orEmpty().flattened()
                    val pickedTrips = flat.filterIsInstance<DayEntry.Move>().map { it.trip }.filter { it.id in ids }
                    val pickedStays = flat.filterIsInstance<DayEntry.Stay>().filter { stayKey(it) in ids }
                    SelectionBar(
                        trips = pickedTrips.size,
                        stays = pickedStays.size,
                        onJoin = {
                            joinPicked(pickedTrips, pickedStays)
                            picked = null
                        },
                        onMode = { mode ->
                            changePicked(pickedTrips, pickedStays, mode)
                            picked = null
                        },
                        onCancel = { picked = null },
                    )
                }
            }
        }
    }
        // "Undo" after each change to trips and each answer: at the top, clear of the Skip and Save buttons below.
        SwipeSnackbarHost(undoSnackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }

    if (layersOpen) {
        MapLayersSheet(onDismiss = { layersOpen = false })
    }

    if (snapDialog) SnapProgressDialog(snapSteps, running = roadsLoading, onDismiss = { snapDialog = false })

    mapPick?.let { pick ->
        MapPickDialog(
            pick = pick,
            onDismiss = { mapPick = null },
            onRetime = { retiming = it },
            onSplit = { splitting = it },
            onMode = { trip, m -> changeMode(trip, m) },
            onFix = { startFix(pick.window.first, pick.window.last, pick.trip?.mode ?: pick.prev?.mode ?: TravelMode.UNKNOWN) },
            onMakeTrip = { adding = pick.window },
            onJoin = { joinTrips(listOfNotNull(pick.prev, pick.next)) },
            onEditDots = { trip ->
                edit.day = Instant.ofEpochMilli(trip.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
                edit.showingDeleted = false
                editing = true
            },
            onUnsnap = { trip ->
                undoable("Showing the recorded route", trip.startTime, trip.endTime) {
                    store.saveTripPath(trip.id, RoadSnapper.STATUS_RECORDED, null, null)
                    DeleteResult(emptySet(), emptyList())
                }
            },
        )
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = anchor.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { anchor = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** Centres and zooms the map so [box] fits in a roughly 340 × 240 dp view. */
internal fun frame(camera: MapCamera, box: GeoBox) {
    val lonSpan = (box.east - box.west).coerceAtLeast(0.0005)
    val mercSpan = (TileMath.mercY(box.north) - TileMath.mercY(box.south)).coerceAtLeast(0.0005)
    val log2 = { v: Double -> ln(v) / ln(2.0) }
    val zoom = min(log2(340 * 360.0 / (256 * lonSpan)), log2(240 * 360.0 / (256 * mercSpan))) - 0.4
    camera.animateTo((box.north + box.south) / 2, (box.east + box.west) / 2, zoom.toFloat().coerceIn(3f, 17f))
}

@Composable
private fun Summary(d: TimelineData) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            if (d.trips == 0 && d.totalKm == 0.0) "No trips" else "${Insights.km(d.totalKm)} · ${d.trips} ${if (d.trips == 1) "trip" else "trips"}",
            style = MaterialTheme.typography.titleLarge,
        )
        if (d.kmByMode.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (TravelMode.ordered + TravelMode.UNKNOWN).forEach { m ->
                    val km = d.kmByMode[m] ?: return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(Color(m.color), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("${m.label} ${Insights.km(km)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

private val timeFormat: DateFormat get() = DateFormat.getTimeInstance(DateFormat.SHORT)

private fun duration(ms: Long): String {
    val min = ms / 60_000
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}

/** One step of the day: a coloured rail on the left, details on the right. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    color: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    picked: Boolean? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (picked == true) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(color.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
            Box(Modifier.padding(top = 6.dp).size(12.dp).background(color, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) { content() }
        trailing?.let { Box(Modifier.align(Alignment.CenterVertically)) { it() } }
        // While picking trips: a tick box (the row itself handles the tap).
        picked?.let { Checkbox(checked = it, onCheckedChange = null, modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp)) }
    }
}

/**
 * Back-to-back trips in the same mode, as one row. Long-press (or the arrow) opens it up into its parts, each
 * of which can be edited like any trip; the menu changes the mode of all of them at once. While picking trips
 * ([selection] non-null), tapping the row picks or unpicks all its parts.
 */
@Composable
private fun GroupRow(
    group: DayEntry.Group,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onModeAll: (TravelMode) -> Unit,
    onJoin: () -> Unit,
    onSectionClick: (DayEntry.Move) -> Unit,
    onSectionMode: (DayEntry.Move, TravelMode) -> Unit,
    onSectionRetime: (DayEntry.Move) -> Unit,
    onSectionSplit: (DayEntry.Move) -> Unit,
    onFix: (DayEntry.Move?) -> Unit,
    selection: Set<Long>?,
    onSelect: (List<Long>, Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val ids = group.moves.map { it.trip.id }
    val allPicked = selection != null && selection.containsAll(ids)
    Column {
        Box {
            TimelineRow(
                color = Color(group.mode.color),
                onClick = { if (selection != null) onSelect(ids, !allPicked) else onClick() },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
                picked = if (selection != null) allPicked else null,
                trailing = {
                    Row {
                        if (selection == null) {
                            Tip("Change all ${group.moves.size} parts") {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Change all parts") }
                            }
                        }
                        Tip(if (expanded) "Hide the parts" else "Show the parts") {
                            IconButton(onClick = onToggle) {
                                Icon(
                                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (expanded) "Hide the parts" else "Show the parts",
                                )
                            }
                        }
                    }
                },
            ) {
                Text("${group.mode.fullLabel} · ${Insights.km(group.distanceM / 1000)}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${timeFormat.format(Date(group.start))} – ${timeFormat.format(Date(group.end))} · ${duration(group.end - group.start)} · " +
                        "${group.moves.size} parts" + (if (group.needsReview) " · needs your answer" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(48.dp, 0.dp)) {
                Text(
                    "How did you travel? (all ${group.moves.size} parts)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                ModeMenuItems(current = group.mode) { m ->
                    menu = false
                    if (m != group.mode || group.needsReview) onModeAll(m)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text("Join into one trip") },
                    leadingIcon = { MaterialIcon(R.drawable.ic_merge) },
                    onClick = {
                        menu = false
                        onJoin()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Join parts and fix route") },
                    leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                    onClick = {
                        menu = false
                        onFix(null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Select parts…") },
                    leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                    onClick = {
                        menu = false
                        if (!expanded) onToggle()
                        onSelect(emptyList(), true)
                    },
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 20.dp)) {
                group.moves.forEach { mv ->
                    MoveRow(
                        entry = mv,
                        onClick = { onSectionClick(mv) },
                        onMode = { onSectionMode(mv, it) },
                        onRetime = { onSectionRetime(mv) },
                        onSplit = { onSectionSplit(mv) },
                        onFix = { onFix(mv) },
                        selection = selection,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

/**
 * A trip. Long-press it to change how it was travelled or its start and end, or to start picking several
 * trips. While picking ([selection] non-null), a tap picks or unpicks it.
 */
@Composable
private fun MoveRow(
    entry: DayEntry.Move,
    onClick: () -> Unit,
    onMode: (TravelMode) -> Unit,
    onRetime: () -> Unit,
    onSplit: () -> Unit,
    onFix: () -> Unit,
    selection: Set<Long>?,
    onSelect: (List<Long>, Boolean) -> Unit,
) {
    val t = entry.trip
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val picked = selection?.contains(t.id)
    Box {
        TimelineRow(
            color = Color(t.mode.color),
            onClick = { if (picked != null) onSelect(listOf(t.id), !picked) else onClick() },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (picked != null) {
                    onSelect(listOf(t.id), !picked)
                } else {
                    menu = true
                }
            },
            picked = picked,
        ) {
            Text("${t.mode.fullLabel} · ${Insights.km(t.distanceM / 1000)}", style = MaterialTheme.typography.titleSmall)
            Text(
                "${timeFormat.format(Date(t.startTime))} – ${timeFormat.format(Date(t.endTime))} · ${duration(t.endTime - t.startTime)}" +
                    (if (t.needsReview) " · needs your answer (long-press, or see above)" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(48.dp, 0.dp)) {
            Text(
                "How did you travel?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ModeMenuItems(current = t.mode) { m ->
                menu = false
                if (m != t.mode || t.needsReview) onMode(m)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Change times") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    menu = false
                    onRetime()
                },
            )
            DropdownMenuItem(
                text = { Text("Split into two trips") },
                leadingIcon = { MaterialIcon(R.drawable.ic_content_cut) },
                onClick = {
                    menu = false
                    onSplit()
                },
            )
            // Fix route works on part of a trip too: narrow its range and only that stretch is snapped.
            DropdownMenuItem(
                text = { Text("Snap to a road (all or part)") },
                leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                onClick = {
                    menu = false
                    onFix()
                },
            )
            DropdownMenuItem(
                text = { Text("Select…") },
                leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                onClick = {
                    menu = false
                    onSelect(listOf(t.id), true)
                },
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

/**
 * A section long-pressed on the Timeline's map: a [trip], or dots outside any trip (trip null) between [prev]
 * and [next]. [window] is the stretch of time an edit starts from.
 */
private class MapPick(val trip: Trip?, val prev: Trip?, val next: Trip?, val window: LongRange, val snapped: Boolean = false)

/** What's drawn nearest a long-press on the map, or null if no route is close. Call off the main thread. */
private fun findMapPick(store: PointStore, from: Long, to: Long, lat: Double, lon: Double, maxM: Double): MapPick? {
    val (time, tripId) = store.sectionNear(from, to, lat, lon, maxM) ?: return null
    if (tripId > 0) {
        val trip = store.trip(tripId) ?: return null
        return MapPick(trip, null, null, trip.startTime..trip.endTime, snapped = store.tripPathRow(trip.id)?.poly != null)
    }
    val day = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
    val dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    val near = store.tripsBetween(dayStart - 86_400_000L, dayEnd + 1)
    val prev = near.lastOrNull { it.endTime <= time }
    val next = near.firstOrNull { it.startTime >= time }
    val start = maxOf(dayStart, (prev?.endTime ?: dayStart) + 1)
    val end = minOf(dayEnd, (next?.startTime ?: dayEnd) - 1)
    return MapPick(null, prev, next, start..maxOf(start, end))
}

/** Confirms what to do with a section long-pressed on the map, in a sheet that slides up from the bottom. */
@Composable
private fun MapPickDialog(
    pick: MapPick,
    onDismiss: () -> Unit,
    onRetime: (Trip) -> Unit,
    onSplit: (Trip) -> Unit,
    onMode: (Trip, TravelMode) -> Unit,
    onFix: () -> Unit,
    onMakeTrip: () -> Unit,
    onJoin: () -> Unit,
    onEditDots: (Trip) -> Unit,
    onUnsnap: (Trip) -> Unit,
) {
    val trip = pick.trip
    val prev = pick.prev
    val next = pick.next
    // Loose dots between two trips that are close together can join them into one.
    val canJoin = trip == null && prev != null && next != null && next.startTime - prev.endTime <= 30 * 60_000L
    // What was picked runs once the sheet has slid back down.
    var then by remember { mutableStateOf<(() -> Unit)?>(null) }
    SlideUpSheet(onClosed = {
        onDismiss()
        then?.invoke()
    }) { close ->
        fun act(block: () -> Unit) {
            then = block
            close()
        }
        Text(if (trip != null) "Edit this trip?" else "Edit these dots?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        // Scrolls on small screens, keeping Cancel in view.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (trip != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(Color(trip.mode.color), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("${trip.mode.fullLabel} · ${Insights.km(trip.distanceM / 1000)}", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "${timeFormat.format(Date(trip.startTime))} – ${timeFormat.format(Date(trip.endTime))} · ${duration(trip.endTime - trip.startTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ModeDropdown(
                    selected = trip.mode,
                    onPick = { m -> act { if (m != trip.mode || trip.needsReview) onMode(trip, m) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                Text(
                    "Dots from ${timeFormat.format(Date(pick.window.first))} to ${timeFormat.format(Date(pick.window.last))} that aren't " +
                        "part of any trip. They're drawn grey and have no row on the Timeline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (trip != null) {
                PickAction("Change times", Icons.Filled.Edit) { act { onRetime(trip) } }
                PickAction("Split into two trips", null, R.drawable.ic_content_cut) { act { onSplit(trip) } }
                PickAction("Snap to a road (all or part)", Icons.Filled.Place) { act(onFix) }
                // A road match that went wrong (a straight line across the map, say): back to the recorded line.
                if (pick.snapped) PickAction("Draw the recorded route instead of the road", null, R.drawable.ic_route) { act { onUnsnap(trip) } }
                PickAction("Edit its dots (delete or move stray ones)", null, R.drawable.ic_open_with) { act { onEditDots(trip) } }
            } else {
                if (canJoin && prev != null && next != null) PickAction("Join the ${prev.mode.label} and ${next.mode.label} trips around them", null) { act(onJoin) }
                PickAction("Make them a trip", Icons.Filled.Add) { act(onMakeTrip) }
                PickAction("Join and fix route on the map", Icons.Filled.Place) { act(onFix) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = close) { Text("Cancel") }
        }
    }
}

@Composable
private fun PickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, drawable: Int = R.drawable.ic_merge, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null) else MaterialIcon(drawable)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** What an entry is called in the change-times panel. */
private fun entryLabel(e: DayEntry): String = when (e) {
    is DayEntry.Move -> e.trip.mode.label
    is DayEntry.Stay -> e.place ?: "Stay"
    is DayEntry.Group -> e.mode.label
}

/**
 * Picks a stretch of the day's dots with a start–end slider, in the style of Edit dots: either the new start
 * and end of [trip] (the sections before and after it grow or shrink to match), or, with [trip] null, a new
 * trip to add, in the mode picked here (it can go between other sections, or before or after them all).
 */
@Composable
private fun RetimePanel(
    trip: Trip?,
    state: DotEditState,
    loading: Boolean,
    entries: List<DayEntry>,
    onCancel: () -> Unit,
    onSave: (Long, Long, TravelMode?) -> Unit,
) {
    val pts = state.points
    var saving by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf<TravelMode?>(null) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tip("Cancel") { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } }
                if (trip != null) {
                    Box(Modifier.size(12.dp).background(Color(trip.mode.color), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("${trip.mode.fullLabel} trip", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("New trip", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (loading || pts.isEmpty()) {
                Hint(if (loading) "Loading dots…" else if (trip != null) "No dots for this trip." else "No dots on this day.")
            } else {
                val first = pts[state.range.first]
                val last = pts[state.range.last]
                val count = state.range.last - state.range.first + 1
                Text(
                    "${timeFormat.format(Date(first.time))} – ${timeFormat.format(Date(last.time))} · " +
                        "${duration(last.time - first.time)} · $count dots",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                if (pts.size > 1) {
                    RangeSlider(
                        value = state.range.first.toFloat()..state.range.last.toFloat(),
                        onValueChange = { v -> state.range = v.start.roundToInt()..v.endInclusive.roundToInt() },
                        valueRange = 0f..(pts.size - 1).toFloat(),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    RangeSteppers(state, Modifier.padding(horizontal = 4.dp))
                }
                val flat = entries.flattened()
                val changes = if (trip != null) {
                    val i = flat.indexOfFirst { it is DayEntry.Move && it.trip.id == trip.id }
                    val before = flat.getOrNull(i - 1)?.takeIf { i > 0 }
                    val after = flat.getOrNull(i + 1)?.takeIf { i >= 0 }
                    buildList {
                        if (before != null) add("${entryLabel(before)} now ends at ${timeFormat.format(Date(first.time))}")
                        if (after != null) add("${entryLabel(after)} now starts at ${timeFormat.format(Date(last.time))}")
                    }
                } else {
                    // Which sections the new trip takes dots from.
                    val covered = flat.filter { it.end >= first.time && it.start <= last.time }
                    if (covered.isEmpty()) listOf("Nothing else is recorded at this time.")
                    else listOf("Takes over from: " + covered.joinToString { entryLabel(it) } + ". Trips it lands in the middle of are split around it.")
                }
                if (changes.isNotEmpty()) {
                    Text(
                        changes.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                if (trip == null) {
                    ModeDropdown(selected = mode, onPick = { mode = it }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Tap a dot to move the nearer end",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    val changed = trip == null || first.time != trip.startTime || last.time != trip.endTime
                    Button(
                        onClick = {
                            saving = true
                            onSave(first.time, last.time, mode)
                        },
                        enabled = !saving && count >= 2 && changed && (trip != null || mode != null),
                    ) { Text(if (trip == null) "Add" else "Save") }
                }
            }
        }
    }
}

/**
 * Breaking a trip in two: a slider (and minus and plus) picks the dot the first part ends on; the first part is
 * highlighted on the map, and a tap on a dot puts the split there.
 */
@Composable
private fun SplitPanel(trip: Trip, state: DotEditState, loading: Boolean, onCancel: () -> Unit, onSave: (Long) -> Unit) {
    val pts = state.points
    val first = pts.indexOfFirst { it.time >= trip.startTime }
    val last = pts.indexOfLast { it.time <= trip.endTime }
    val canSplit = !loading && first >= 0 && last - first >= 2
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tip("Cancel") { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } }
                Box(Modifier.size(12.dp).background(Color(trip.mode.color), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("Split this ${trip.mode.fullLabel} trip", style = MaterialTheme.typography.titleMedium)
            }
            if (!canSplit) {
                Hint(if (loading) "Loading dots…" else "This trip has too few dots to split.")
            } else {
                val split = state.range.last.coerceIn(first, last - 1)
                val endsAt = pts[split].time
                val startsAt = pts[split + 1].time
                Text(
                    "First part ${timeFormat.format(Date(trip.startTime))} – ${timeFormat.format(Date(endsAt))}, " +
                        "second part ${timeFormat.format(Date(startsAt))} – ${timeFormat.format(Date(trip.endTime))}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Slider(
                    value = split.toFloat(),
                    onValueChange = { state.range = first..it.roundToInt().coerceIn(first, last - 1) },
                    valueRange = first.toFloat()..(last - 1).toFloat(),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { state.range = first..(split - 1).coerceAtLeast(first) }, enabled = split > first) { Text("− 1 dot") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { state.range = first..(split + 1).coerceAtMost(last - 1) }, enabled = split < last - 1) { Text("+ 1 dot") }
                }
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Tap a dot to split there",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onSave(startsAt) }) { Text("Split") }
                }
            }
        }
    }
}

/** While picking trips: how many are picked, joining them into one, and a menu to change all of their modes at once. */
@Composable
private fun SelectionBar(trips: Int, stays: Int, onJoin: () -> Unit, onMode: (TravelMode) -> Unit, onCancel: () -> Unit) {
    val count = trips + stays
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Tip("Stop selecting") { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Stop selecting") } }
            Text(
                when {
                    count == 0 -> "Tap trips or stays to select them"
                    stays == 0 -> if (trips == 1) "1 trip selected" else "$trips trips selected"
                    trips == 0 -> if (stays == 1) "1 stay selected" else "$stays stays selected"
                    else -> "${if (trips == 1) "1 trip" else "$trips trips"}, ${if (stays == 1) "1 stay" else "$stays stays"}"
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onJoin, enabled = count >= 2) { Text("Join") }
            Box {
                Button(onClick = { menu = true }, enabled = count > 0) { Text("Change mode") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    ModeMenuItems(current = null) { m ->
                        menu = false
                        onMode(m)
                    }
                }
            }
        }
    }
}

private const val MAX_VIAS = 5
private val roadColors = listOf(Color(0xFF3D9BFF), Color(0xFFFF7A1A), Color(0xFF66BB6A))

/** Candidate roads (the picked one bold, others faint) and the points tapped to route through. */
/**
 * The trips picked in the list (or long-pressed on the map) drawn over everything else: a white line with a dark
 * edge, green where it starts and red where it ends, so it's clear which section is meant.
 */
@Composable
private fun TripHighlight(camera: MapCamera, paths: List<DoubleArray>, modifier: Modifier) {
    Canvas(modifier) {
        camera.cameraTick // Redraw whenever the camera moves.
        val halo = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val line = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val drawn = paths.mapNotNull { pts ->
            if (pts.size < 2) return@mapNotNull null
            val path = Path()
            for (k in 0 until pts.size / 2) {
                val o = camera.toScreen(pts[k * 2], pts[k * 2 + 1]) ?: return@Canvas
                if (k == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            path
        }
        drawn.forEach { drawPath(it, Color.Black.copy(alpha = 0.55f), style = halo) }
        drawn.forEach { drawPath(it, Color.White, style = line) }
        val first = paths.firstOrNull { it.size >= 2 } ?: return@Canvas
        val last = paths.lastOrNull { it.size >= 2 } ?: return@Canvas
        camera.toScreen(first[0], first[1])?.let {
            drawCircle(Color.White, 8.dp.toPx(), it)
            drawCircle(Color(0xFF43A047), 6.dp.toPx(), it)
        }
        camera.toScreen(last[last.size - 2], last[last.size - 1])?.let {
            drawCircle(Color.White, 8.dp.toPx(), it)
            drawCircle(Color(0xFFE53935), 6.dp.toPx(), it)
        }
    }
}

@Composable
private fun RouteLinesOverlay(camera: MapCamera, roads: List<RoadPath>, chosen: Int, vias: List<Pair<Double, Double>>, modifier: Modifier) {
    val pin = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        camera.cameraTick // Redraw whenever the camera moves.
        // The picked road last, so it's on top.
        val order = roads.indices.sortedBy { if (it == chosen) 1 else 0 }
        for (i in order) {
            val pts = roads[i].points
            val path = Path()
            var started = false
            for (k in 0 until pts.size / 2) {
                val o = camera.toScreen(pts[k * 2], pts[k * 2 + 1]) ?: return@Canvas
                if (started) path.lineTo(o.x, o.y) else path.moveTo(o.x, o.y)
                started = true
            }
            val color = roadColors[i % roadColors.size]
            drawPath(path, if (i == chosen) color else color.copy(alpha = 0.45f), style = Stroke(width = (if (i == chosen) 6 else 3).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        vias.forEach { (lat, lon) ->
            val o = camera.toScreen(lat, lon) ?: return@forEach
            drawCircle(Color.White, radius = 9.dp.toPx(), center = o)
            drawCircle(pin, radius = 6.dp.toPx(), center = o)
        }
    }
}

/**
 * Fix route: pick a stretch of dots with the slider (it can run across several trips and the gaps between
 * them), and it's joined into one trip. Either let it be matched to roads as usual, or find the roads between
 * its ends and pick one; tapping the map adds points the road must go through.
 */
@Composable
private fun RouteFixPanel(
    state: DotEditState,
    loading: Boolean,
    mode: TravelMode,
    onMode: (TravelMode) -> Unit,
    roads: List<RoadPath>,
    chosen: Int,
    onChoose: (Int) -> Unit,
    roadsLoading: Boolean,
    error: String?,
    vias: Int,
    onFindRoads: () -> Unit,
    onPreview: () -> Unit,
    onClearVias: () -> Unit,
    onCancel: () -> Unit,
    onSave: (Long, Long) -> Unit,
) {
    val pts = state.points
    var saving by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tip("Cancel") { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") } }
                Text("Join and fix the route", style = MaterialTheme.typography.titleMedium)
            }
            if (loading || pts.size < 2) {
                Hint(if (loading) "Loading dots…" else "Not enough dots here.")
            } else {
                val first = pts[state.range.first]
                val last = pts[state.range.last]
                Text(
                    "${timeFormat.format(Date(first.time))} – ${timeFormat.format(Date(last.time))} · ${state.range.last - state.range.first + 1} dots, joined into one trip",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                RangeSlider(
                    value = state.range.first.toFloat()..state.range.last.toFloat(),
                    onValueChange = { v -> state.range = v.start.roundToInt()..v.endInclusive.roundToInt() },
                    valueRange = 0f..(pts.size - 1).toFloat(),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                RangeSteppers(state, Modifier.padding(horizontal = 4.dp))
                ModeDropdown(selected = mode, onPick = onMode, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onFindRoads, enabled = !roadsLoading) {
                        Text(if (roadsLoading) "Finding roads…" else if (roads.isEmpty()) "Find roads" else "Find again")
                    }
                    if (vias > 0) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onClearVias) { Text(if (vias == 1) "Clear point" else "Clear $vias points") }
                    }
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                if (roads.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        roads.forEachIndexed { i, r ->
                            FilterChip(
                                selected = i == chosen,
                                onClick = { onChoose(if (i == chosen) -1 else i) },
                                label = {
                                    val name = when (r.streetName) {
                                        AUTO_MATCH -> "Snapped to your dots"
                                        null -> "Road ${i + 1}"
                                        else -> "Via ${r.streetName}"
                                    }
                                    Text(name + " · " + Units.distance(polylineKm(r.points)))
                                },
                                leadingIcon = { Box(Modifier.size(10.dp).background(roadColors[i % roadColors.size], CircleShape)) },
                            )
                        }
                    }
                }
                Text(
                    "White is the route now, colour is the new one. Tap the map to route through a spot (up to $MAX_VIAS).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    // Nothing is saved before you've seen it: first the snapped line, then Save.
                    if (chosen >= 0) {
                        Button(
                            onClick = {
                                saving = true
                                onSave(first.time, last.time)
                            },
                            enabled = !saving && !roadsLoading,
                        ) { Text("Save this route") }
                    } else {
                        Button(onClick = onPreview, enabled = !roadsLoading) { Text(if (roadsLoading) "Snapping…" else "Show snapped route") }
                    }
                }
            }
        }
    }
}

/** One step of snapping a route, as shown in the progress dialog. */
private class SnapStep(val text: String, val state: State) {
    enum class State { RUNNING, DONE, FAILED }
}

/** Ends the step that's running, as done or failed. */
private fun MutableList<SnapStep>.finishRunning(failed: Boolean) {
    val i = indexOfLast { it.state == SnapStep.State.RUNNING }
    if (i >= 0) this[i] = SnapStep(this[i].text, if (failed) SnapStep.State.FAILED else SnapStep.State.DONE)
}

/** Each step of snapping as it happens: a spinner on the one running, a tick when done, the reason if it fails. */
@Composable
private fun SnapProgressDialog(steps: List<SnapStep>, running: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        title = { Text(if (running) "Snapping the route…" else if (steps.any { it.state == SnapStep.State.FAILED }) "Snapping didn't work" else "Snapped") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (steps.isEmpty()) Text("Starting…", style = MaterialTheme.typography.bodyMedium)
                steps.forEach { s ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            when (s.state) {
                                SnapStep.State.RUNNING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                SnapStep.State.DONE -> Icon(Icons.Filled.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
                                SnapStep.State.FAILED -> Icon(Icons.Filled.Close, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            s.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (s.state == SnapStep.State.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (running) "Hide" else "Close") } },
    )
}

/** Marks the line Show snapped route made, so its chip can say so. */
private const val AUTO_MATCH = "\u0000auto"

/** Length of an interleaved [lat, lon, …] line in kilometres. */
private fun polylineKm(pts: DoubleArray): Double {
    var m = 0.0
    for (k in 1 until pts.size / 2) m += TrackRules.distanceMeters(pts[k * 2 - 2], pts[k * 2 - 1], pts[k * 2], pts[k * 2 + 1])
    return m / 1000
}

/** How a stay is kept among the picked trip ids: by its start, negated, so it can't clash with a trip id. */
private fun stayKey(stay: DayEntry.Stay): Long = -stay.start

/**
 * A stay between trips. Long-press it to add a trip in its place (if you were moving after all), or to start
 * selecting with it; while selecting, a tap picks it like a trip.
 */
@Composable
private fun StayRow(
    entry: DayEntry.Stay,
    onClick: () -> Unit,
    onAddTrip: () -> Unit,
    picked: Boolean?,
    onSelect: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        TimelineRow(
            color = MaterialTheme.colorScheme.outline,
            onClick = { if (picked != null) onSelect(!picked) else onClick() },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (picked != null) onSelect(!picked) else menu = true
            },
            picked = picked,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(entry.place ?: "Stayed here", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "${timeFormat.format(Date(entry.start))} – ${timeFormat.format(Date(entry.end))} · ${duration(entry.end - entry.start)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(48.dp, 0.dp)) {
            DropdownMenuItem(
                text = { Text("Add a trip here") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    menu = false
                    onAddTrip()
                },
            )
            DropdownMenuItem(
                text = { Text("Select…") },
                leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                onClick = {
                    menu = false
                    onSelect(true)
                },
            )
        }
    }
}

/** A day (or a month, in the year view) with its distance and a bar split by travel mode. */
@Composable
private fun PeriodRow(stat: PeriodStat, scale: TimelineScale, max: Double, onClick: () -> Unit) {
    val title = if (scale == TimelineScale.YEAR) {
        YearMonth.parse(stat.key).format(DateTimeFormatter.ofPattern("MMMM"))
    } else {
        LocalDate.parse(stat.key).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                if (stat.trips > 0) "${Insights.km(stat.meters / 1000)} · ${stat.trips} ${if (stat.trips == 1) "trip" else "trips"}" else "No trips",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (max > 0) {
                val shown = (stat.meters / max).toFloat().coerceIn(0f, 1f)
                (TravelMode.ordered + TravelMode.UNKNOWN).forEach { m ->
                    val part = stat.metersByMode[m] ?: return@forEach
                    if (part <= 0) return@forEach
                    Box(Modifier.fillMaxHeight().weight((part / stat.meters).toFloat() * shown).background(Color(m.color)))
                }
                if (shown < 1f) Spacer(Modifier.weight(1f - shown))
            }
        }
    }
}
