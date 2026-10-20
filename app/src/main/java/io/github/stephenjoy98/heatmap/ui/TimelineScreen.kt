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
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.ModeColors
import io.github.stephenjoy98.heatmap.data.PeriodStat
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.TravelMode
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
import io.github.stephenjoy98.heatmap.data.Trip
import io.github.stephenjoy98.heatmap.data.RoadQuestion
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
    val scope = rememberCoroutineScope()
    val tapRadiusPx = 32 * context.resources.displayMetrics.density
    var layersOpen by remember { mutableStateOf(false) }
    val route3d = remember { { box: GeoBox -> store.elevationTracksIn(box, TravelMode.entries.toSet()) } }
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
    val startFix = { from: Long, to: Long, mode: TravelMode ->
        vias.clear()
        roads = emptyList()
        chosenRoad = -1
        roadsError = null
        fixMode = mode
        fixing = from..to
    }
    val rangeEditing = retiming != null || adding != null || fixing != null
    val mapEditing = editing || rangeEditing
    // Picking several trips to change their mode together; null when not picking.
    var picked by remember { mutableStateOf<Set<Long>?>(null) }
    val select = { ids: List<Long>, on: Boolean ->
        val now = picked.orEmpty()
        picked = if (on) now + ids else now - ids.toSet()
    }

    val layers = remember(data, dark, mapEditing) {
        val d = data ?: return@remember emptyList()
        // While editing, the routes fade back so the dots stand out.
        listOf(MapLayer("timeline-${d.period}-$colors", TimelineTiles(d.routes, tilePx, casing.toArgb()), alpha = if (mapEditing) 0.35f else 1f))
    }

    LaunchedEffect(retiming, adding, fixing) {
        val window = retiming?.let { it.startTime..it.endTime } ?: adding ?: fixing ?: return@LaunchedEffect
        retimeLoading = true
        val from = minOf(period.fromMs, window.first)
        val to = maxOf(period.toMs, window.last + 1)
        val pts = withContext(Dispatchers.IO) { store.editablePoints(from, to) }
        retime.load(pts)
        if (pts.isNotEmpty()) {
            var first = pts.indexOfFirst { it.time >= window.first }
            var last = pts.indexOfLast { it.time <= window.last }
            if (first < 0 || last < first) {
                // Nothing in the stretch: start from the middle of the day's dots.
                first = pts.size * 2 / 5
                last = (pts.size * 3 / 5).coerceAtLeast(minOf(first + 1, pts.size - 1))
            }
            retime.range = first..last.coerceAtMost(pts.size - 1)
        }
        retimeLoading = false
        frameDots(camera, retime.target)
    }
    BackHandler(enabled = rangeEditing && !LocalCovered.current) {
        retiming = null
        adding = null
        fixing = null
    }
    // Ways between the first and last dot picked, through any points tapped on the map.
    fun findRoads() {
        val pts = retime.points
        if (pts.isEmpty()) return
        val a = pts[retime.range.first]
        val b = pts[retime.range.last]
        val costing = Valhalla.costing(fixMode) ?: "auto"
        val stops = listOf(a.lat to a.lon) + vias + (b.lat to b.lon)
        roadsLoading = true
        roadsError = null
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    if (vias.isEmpty()) Valhalla.routes(a.lat, a.lon, b.lat, b.lon, costing)
                    else listOfNotNull(Valhalla.routeThrough(stops, costing))
                }
            }
            roads = found.getOrDefault(emptyList())
            chosenRoad = if (roads.isNotEmpty()) 0 else -1
            roadsError = when {
                found.isFailure -> "Couldn't reach the routing service. Check your connection and try again."
                roads.isEmpty() -> "No road found between these points."
                else -> null
            }
            roadsLoading = false
        }
    }
    BackHandler(enabled = picked != null && !LocalCovered.current) { picked = null }
    val changeMode = { trip: Trip, mode: TravelMode ->
        scope.launch {
            withContext(Dispatchers.IO) { store.setTripMode(trip.id, mode) }
            val day = Instant.ofEpochMilli(trip.startTime).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            afterDotEdit(context, DeleteResult(setOf(day), emptyList()))
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

    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding())) {
        TopAppBar(
            title = {
                Text(
                    when {
                        retiming != null -> "Change times"
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
                onMapClick = when {
                    editing -> { lat, lon -> nearestDot(camera, edit, lat, lon, tapRadiusPx)?.let { edit.selected = it } }
                    // Tapping a dot moves whichever end of the trip is nearer to it.
                    // Fixing a route: a tap says the route went through there.
                    fixing != null -> { lat, lon ->
                        if (vias.size < MAX_VIAS) {
                            vias += lat to lon
                            findRoads()
                        }
                    }
                    rangeEditing -> { lat, lon ->
                        nearestDot(camera, retime, lat, lon, tapRadiusPx)?.let { i ->
                            val r = retime.range
                            retime.range = if (abs(i - r.first) <= abs(i - r.last)) i..r.last else r.first..i
                        }
                    }
                    else -> null
                },
            )
            ScaleBar(camera, Modifier.align(Alignment.BottomStart).padding(8.dp))
            if (editing) {
                DotsOverlay(camera, edit, Modifier.fillMaxSize())
                // Map type and 3D buildings, right where you're editing.
                LayersButton(onClick = { layersOpen = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                SnackbarHost(editSnackbar, Modifier.align(Alignment.TopCenter))
            } else if (rangeEditing) {
                DotsOverlay(camera, retime, Modifier.fillMaxSize())
                if (fixing != null) RouteLinesOverlay(camera, roads, chosenRoad, vias, Modifier.fillMaxSize())
                LayersButton(onClick = { layersOpen = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            } else if (data == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        if (editing) {
            Box(Modifier.padding(12.dp)) {
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
            Box(Modifier.padding(12.dp)) {
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
                    onClearVias = {
                        vias.clear()
                        roads = emptyList()
                        chosenRoad = -1
                    },
                    onCancel = { fixing = null },
                    onSave = { start, end ->
                        val road = roads.getOrNull(chosenRoad)
                        val mode = fixMode
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                store.addTrip(start, end, mode).also { r ->
                                    // The road picked becomes the trip's shape; otherwise it's matched to roads as usual.
                                    if (road != null && r.createdTripId > 0) {
                                        val n = road.points.size / 2
                                        val poly = Polyline.encode(DoubleArray(n) { road.points[it * 2] }, DoubleArray(n) { road.points[it * 2 + 1] })
                                        store.saveTripPath(r.createdTripId, RoadSnapper.STATUS_SNAPPED, poly, poly)
                                    }
                                }
                            }
                            afterDotEdit(context, result)
                            fixing = null
                        }
                    },
                )
            }
        } else if (rangeEditing) {
            val trip = retiming
            Box(Modifier.padding(12.dp)) {
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
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    if (trip != null) store.retimeTrip(trip.id, start, end)
                                    else store.addTrip(start, end, mode ?: TravelMode.UNKNOWN)
                                }
                                afterDotEdit(context, result)
                                retiming = null
                                adding = null
                            }
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
                if (questions.isNotEmpty()) {
                    item(key = "questions") {
                        TripQuestionsCard(
                            questions = questions,
                            onAnswer = { trip, mode -> changeMode(trip, mode) },
                            onShowDay = { trip ->
                                anchor = Instant.ofEpochMilli(trip.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
                                scale = TimelineScale.DAY
                                focusTrip = trip.id
                            },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                if (roadQuestions.isNotEmpty()) {
                    item(key = "roads") {
                        RoadQuestionsCard(
                            questions = roadQuestions,
                            onAnswer = { q, choice ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { RoadSnapper.answer(context, q, choice) }
                                }
                            },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                item { Summary(d) }
                when (scale) {
                    TimelineScale.DAY -> {
                        if (d.entries.isEmpty()) item { Empty("Nothing recorded on this day.") }
                        items(d.entries, key = { "${it.javaClass.simpleName}-${it.start}" }) { entry ->
                            when (entry) {
                                is DayEntry.Move -> MoveRow(
                                    entry = entry,
                                    onClick = { entry.box?.let { frame(camera, it) } },
                                    onMode = { changeMode(entry.trip, it) },
                                    onRetime = { retiming = entry.trip },
                                    onFix = { startFix(entry.trip.startTime, entry.trip.endTime, entry.trip.mode) },
                                    selection = picked,
                                    onSelect = select,
                                )
                                is DayEntry.Stay -> StayRow(
                                    entry = entry,
                                    onClick = { camera.animateTo(entry.lat, entry.lon, 16f) },
                                    onAddTrip = { adding = (entry.start + 1)..(entry.end - 1) },
                                )
                                is DayEntry.Group -> GroupRow(
                                    group = entry,
                                    expanded = expanded[entry.start] == true,
                                    onToggle = { expanded[entry.start] = expanded[entry.start] != true },
                                    onClick = { entry.box?.let { frame(camera, it) } },
                                    onModeAll = { m -> entry.moves.forEach { changeMode(it.trip, m) } },
                                    onSectionClick = { mv -> mv.box?.let { frame(camera, it) } },
                                    onSectionMode = { mv, m -> changeMode(mv.trip, m) },
                                    onSectionRetime = { mv -> retiming = mv.trip },
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
                                        "Trips in a row in the same mode are shown together: long-press to see each part."
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
                SelectionBar(
                    count = ids.size,
                    onMode = { mode ->
                        val trips = data?.entries.orEmpty().flattened().filterIsInstance<DayEntry.Move>().map { it.trip }.filter { it.id in ids }
                        scope.launch {
                            withContext(Dispatchers.IO) { store.setTripModes(ids, mode) }
                            val days = trips.map { Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.toSet()
                            afterDotEdit(context, DeleteResult(days, emptyList()))
                        }
                        picked = null
                    },
                    onCancel = { picked = null },
                )
            }
        }
    }

    if (layersOpen) {
        MapLayersSheet(onDismiss = { layersOpen = false })
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
    onSectionClick: (DayEntry.Move) -> Unit,
    onSectionMode: (DayEntry.Move, TravelMode) -> Unit,
    onSectionRetime: (DayEntry.Move) -> Unit,
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
                text = { Text("Fix route on the map") },
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
                    Text(
                        "How did you travel?",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                    )
                    ModeChips(selected = mode, onPick = { mode = it }, modifier = Modifier.padding(horizontal = 12.dp))
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

/** While picking trips: how many are picked, and a menu to change all of their modes at once. */
@Composable
private fun SelectionBar(count: Int, onMode: (TravelMode) -> Unit, onCancel: () -> Unit) {
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
                if (count == 0) "Tap trips to select them" else if (count == 1) "1 trip selected" else "$count trips selected",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
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
                ModeChips(selected = mode, onPick = onMode, modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.height(8.dp))
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
                                label = { Text((r.streetName?.let { "Via $it" } ?: "Road ${i + 1}") + " · " + Units.distance(polylineKm(r.points))) },
                                leadingIcon = { Box(Modifier.size(10.dp).background(roadColors[i % roadColors.size], CircleShape)) },
                            )
                        }
                    }
                }
                Text(
                    "Tap the map where you went to make the road pass there (up to $MAX_VIAS points). " +
                        "Without a road picked, the joined trip is matched to roads automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            saving = true
                            onSave(first.time, last.time)
                        },
                        enabled = !saving,
                    ) { Text(if (chosen >= 0) "Save with this road" else "Join and snap") }
                }
            }
        }
    }
}

/** Length of an interleaved [lat, lon, …] line in kilometres. */
private fun polylineKm(pts: DoubleArray): Double {
    var m = 0.0
    for (k in 1 until pts.size / 2) m += TrackRules.distanceMeters(pts[k * 2 - 2], pts[k * 2 - 1], pts[k * 2], pts[k * 2 + 1])
    return m / 1000
}

/** A stay between trips. Long-press it to add a trip in its place (if you were moving after all). */
@Composable
private fun StayRow(entry: DayEntry.Stay, onClick: () -> Unit, onAddTrip: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        TimelineRow(
            color = MaterialTheme.colorScheme.outline,
            onClick = onClick,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menu = true
            },
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
