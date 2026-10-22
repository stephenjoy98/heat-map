package io.github.stephenjoy98.heatmap.ui

import androidx.compose.ui.unit.Dp
import android.app.Activity
import android.content.Context
import android.graphics.PointF
import android.view.View
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import io.github.stephenjoy98.heatmap.data.Route3d
import io.github.stephenjoy98.heatmap.data.GeoBox
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.ElevationPoint
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.geometry.LatLng
import io.github.stephenjoy98.heatmap.data.MapLayerType
import io.github.stephenjoy98.heatmap.data.Prefs
import io.github.stephenjoy98.heatmap.map.TileBitmapSource
import kotlinx.coroutines.delay
import io.github.stephenjoy98.heatmap.map.TileOverlayView
import io.github.stephenjoy98.heatmap.map.VectorMaps
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.WeakHashMap
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import org.maplibre.android.style.layers.CircleLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MLatLng

/** A tile layer drawn over the base map, independent of which map engine is used. */
class MapLayer(val id: String, val source: TileBitmapSource, val alpha: Float = 1f, val zIndex: Float = 0f)

/** Moves the camera of an [AppMap], whichever engine it uses. */
class MapCamera(val initialLat: Double, val initialLon: Double, val initialZoom: Float) {
    // zoom NaN keeps the current zoom; durationMs > 0 glides there at a steady pace (for following you).
    internal class Command(val lat: Double, val lon: Double, val zoom: Float, val animate: Boolean, val durationMs: Int = 0)

    internal enum class Action { ZOOM_IN, ZOOM_OUT, NORTH_UP }

    // Replays the latest move so a map created afterwards still starts in the right place.
    internal val commands = MutableSharedFlow<Command>(replay = 1, extraBufferCapacity = 4)

    // One-off button actions; never replayed.
    internal val actions = MutableSharedFlow<Action>(extraBufferCapacity = 8)

    /** The map's current rotation in degrees clockwise from north, kept up to date by the engine. */
    var bearing by mutableFloatStateOf(0f)
        internal set

    /** Changes whenever the camera moves, so overlays that use [toScreen] know to redraw. */
    var cameraTick by mutableIntStateOf(0)
        internal set

    /** Ground distance one screen pixel covers at the middle of the map, in metres (0 until known). */
    var metersPerPixel by mutableFloatStateOf(0f)
        internal set

    /** The coordinate at the middle of the map (latitude, longitude); null until the map is ready. */
    var center by mutableStateOf<Pair<Double, Double>?>(null)
        internal set

    /** True while the map is being moved or zoomed, by a gesture or an animation. */
    var moving by mutableStateOf(false)
        internal set

    internal var projector: ((Double, Double) -> Offset?)? = null

    /** Where a coordinate is on the map, in pixels from its top-left corner; null until the map is ready. */
    fun toScreen(lat: Double, lon: Double): Offset? = projector?.invoke(lat, lon)

    fun zoomIn() {
        actions.tryEmit(Action.ZOOM_IN)
    }

    fun zoomOut() {
        actions.tryEmit(Action.ZOOM_OUT)
    }

    /** Turns the map back so north is up. */
    fun northUp() {
        actions.tryEmit(Action.NORTH_UP)
    }

    fun moveTo(lat: Double, lon: Double, zoom: Float) {
        commands.tryEmit(Command(lat, lon, zoom, animate = false))
    }

    fun animateTo(lat: Double, lon: Double, zoom: Float) {
        commands.tryEmit(Command(lat, lon, zoom, animate = true))
    }

    /** Glides the map's middle to a spot at a steady pace over [durationMs], keeping the zoom. */
    fun glideTo(lat: Double, lon: Double, durationMs: Int) {
        commands.tryEmit(Command(lat, lon, Float.NaN, animate = true, durationMs = durationMs))
    }

    /** Goes up each time the map is dragged by hand, so a "follow me" mode knows to stop. */
    var panTick by mutableIntStateOf(0)
        internal set
}

@Composable
fun rememberMapCamera(lat: Double = 20.0, lon: Double = 0.0, zoom: Float = 2f) = remember { MapCamera(lat, lon, zoom) }

/**
 * The app's map: a dark or light base map with heat (and other) tile layers on top.
 * [refreshKey] changes when the layers' data changes; tiles are re-rendered shortly after.
 */
@Composable
fun AppMap(
    camera: MapCamera,
    layers: List<MapLayer>,
    modifier: Modifier = Modifier,
    refreshKey: Any? = null,
    latest: LatLng? = null,
    onMapClick: ((lat: Double, lon: Double) -> Unit)? = null,
    cacheKey: String? = null,
    onMapLongClick: ((lat: Double, lon: Double) -> Unit)? = null,
    // Which way you're heading, in degrees from north, for an arrow on the location dot; null hides it.
    heading: Float? = null,
    // How much of the map's bottom is covered (by the tab bar), so the OpenStreetMap credit stays above it.
    bottomInset: Dp = 0.dp,
    // Loads routes with heights for an area; with 3D buildings on, they're drawn as a 3D route. Runs off
    // the main thread.
    route3d: ((GeoBox) -> List<List<ElevationPoint>>)? = null,
) {
    val dark = isDarkTheme()
    val mapLayer by Prefs.mapLayer.collectAsStateWithLifecycle()
    val buildings by Prefs.buildings3d.collectAsStateWithLifecycle()
    val style = MapLook(dark, mapLayer == MapLayerType.TERRAIN, buildings)
    VectorAppMap(camera, layers, style, refreshKey, latest, onMapClick, onMapLongClick, cacheKey, route3d, modifier, bottomInset, heading)
}

/** How the base map looks: theme, terrain shading and 3D buildings (which also tilt the camera). */
private data class MapLook(val dark: Boolean, val terrain: Boolean = false, val buildings: Boolean = false)

private const val TILT_3D = 45f

// The OpenStreetMap credit sits this far up from the map's bottom, above the recording panel and GPS button.
private const val ATTRIBUTION_BOTTOM_DP = 180f

// Tilting further than this by hand switches on 3D buildings, and back below the second switches them off.
private const val AUTO_3D_TILT = 8f
private const val AUTO_FLAT_TILT = 2f

/**
 * The base map's colours and brightness, as chosen in the layers sheet (one setting of each per theme): how
 * colourful it is (muted to vivid), and a lighter or darker sheet over it.
 */
@Composable
private fun MapBrightness(holder: VectorMapHolder, dark: Boolean) {
    val brightness by (if (dark) Prefs.mapBrightnessDark else Prefs.mapBrightnessLight).collectAsStateWithLifecycle()
    val colour by (if (dark) Prefs.mapColourDark else Prefs.mapColourLight).collectAsStateWithLifecycle()
    val roads by Prefs.mapColourRoads.collectAsStateWithLifecycle()
    // The light style is left exactly as OpenFreeMap draws it until colour is turned up; once recoloured,
    // turning it back down paints the muted palette (a freshly loaded style starts untouched again).
    val recoloured = remember(holder, holder.styleVersion) { booleanArrayOf(false) }
    LaunchedEffect(holder, holder.styleVersion, colour, roads) {
        if (dark || colour > 0f || recoloured[0]) {
            recoloured[0] = true
            holder.withMap { m -> m.style?.let { VectorMaps.applyColours(it, dark, colour, roads) } }
        }
    }
    LaunchedEffect(holder, holder.styleVersion, brightness) {
        holder.withMap { m -> m.style?.let { VectorMaps.setTint(it, brightness) } }
    }
}

/**
 * Two-finger tilt that doesn't need both fingers to land at the same moment. MapLibre only starts its own
 * tilt when the fingers touch down together; a second finger that lands a little later just pans the map.
 * This watches the map's touches: once two fingers side by side both move up or down, it takes over from
 * the map's gesture and tilts the camera itself until the fingers lift. Anything else (pans, pinches,
 * twists) is left to the map.
 */
private class TwoFingerTilt(private val holder: VectorMapHolder, density: Float) : View.OnTouchListener {
    private enum class State { IDLE, WATCHING, PASS, TILTING, DONE }

    private val slop = 12 * density
    private var state = State.IDLE
    private val ids = IntArray(2)
    private val startX = FloatArray(2)
    private val startY = FloatArray(2)
    private var lastY = 0f

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        val m = holder.map
        if (m == null || !m.uiSettings.isTiltGesturesEnabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> state = State.IDLE
            MotionEvent.ACTION_POINTER_DOWN -> when (state) {
                State.IDLE -> if (e.pointerCount == 2) {
                    for (i in 0..1) {
                        ids[i] = e.getPointerId(i)
                        startX[i] = e.getX(i)
                        startY[i] = e.getY(i)
                    }
                    state = State.WATCHING
                }
                // A third finger: leave it to the map, or keep tilting from where the fingers are now.
                State.WATCHING -> state = State.PASS
                State.TILTING -> lastY = averageY(e, skip = -1)
                else -> Unit
            }
            MotionEvent.ACTION_MOVE -> when (state) {
                State.WATCHING -> decide(e)
                State.TILTING -> {
                    val y = averageY(e, skip = -1)
                    val dy = y - lastY
                    lastY = y
                    // Same rate as MapLibre's own tilt: a tenth of a degree per pixel, up tilts further.
                    val tilt = (m.cameraPosition.tilt - TILT_PER_PIXEL * dy).coerceIn(0.0, MAX_HAND_TILT)
                    holder.handTilting = true
                    m.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.tiltTo(tilt))
                    holder.handTilting = false
                }
                else -> Unit
            }
            MotionEvent.ACTION_POINTER_UP -> when (state) {
                State.WATCHING -> state = State.PASS
                State.TILTING -> if (e.pointerCount - 1 < 2) state = State.DONE else lastY = averageY(e, skip = e.actionIndex)
                else -> Unit
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val mine = state == State.TILTING || state == State.DONE
                state = State.IDLE
                return mine
            }
        }
        return state == State.TILTING || state == State.DONE
    }

    /** Tilt when both fingers, side by side, move the same way up or down; give up on anything else. */
    private fun decide(e: MotionEvent) {
        val a = e.findPointerIndex(ids[0])
        val b = e.findPointerIndex(ids[1])
        if (a < 0 || b < 0) {
            state = State.PASS
            return
        }
        val dx0 = e.getX(a) - startX[0]
        val dy0 = e.getY(a) - startY[0]
        val dx1 = e.getX(b) - startX[1]
        val dy1 = e.getY(b) - startY[1]
        val sideBySide = abs(startY[0] - startY[1]) < abs(startX[0] - startX[1]) * 1.2f
        val vertical0 = abs(dy0) > slop && abs(dy0) > abs(dx0) * 1.5f
        val vertical1 = abs(dy1) > slop && abs(dy1) > abs(dx1) * 1.5f
        if (sideBySide && vertical0 && vertical1 && (dy0 > 0) == (dy1 > 0)) {
            state = State.TILTING
            lastY = averageY(e, skip = -1)
            // Stop the map's own pan or zoom; from here on the touches are ours.
            val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
            holder.mapView.onTouchEvent(cancel)
            cancel.recycle()
        } else if (hypot(dx0, dy0) > slop * 3 || hypot(dx1, dy1) > slop * 3) {
            state = State.PASS
        }
    }

    private fun averageY(e: MotionEvent, skip: Int): Float {
        var sum = 0f
        var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            sum += e.getY(i)
            n++
        }
        return if (n == 0) lastY else sum / n
    }
}

private const val TILT_PER_PIXEL = 0.1
private const val MAX_HAND_TILT = 60.0

/** Owns a MapLibre MapView, follows the Compose lifecycle, and hands out the map once it's ready. */
private class VectorMapHolder(context: Context) {
    val mapView = MapView(context).apply { onCreate(null) }
    var map: MapLibreMap? = null
    var styleLook: Any? = null
    private val readyCallbacks = mutableListOf<(MapLibreMap) -> Unit>()

    // For full maps: the heat overlay, and what the map's listeners report to. Kept here so a cached map
    // keeps its tiles and doesn't gain a second set of listeners when it's shown again.
    val overlay by lazy { TileOverlayView(context) }
    var camera: MapCamera? = null
    var onClick: ((Double, Double) -> Unit)? = null
    var onLongClick: ((Double, Double) -> Unit)? = null
    var setUp = false

    // Whether the current camera move is the user's own gesture, and whether 3D was just switched on by one.
    var gesture = false
    var tiltedByGesture = false

    // True while TwoFingerTilt is tilting the camera, so its moves count as the user's own gesture.
    var handTilting = false

    /** Goes up each time a style finishes loading, so extra layers can be added back. */
    var styleVersion by mutableIntStateOf(0)

    fun destroy() {
        overlay.release()
        mapView.onDestroy()
    }

    init {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = true
            // Tilting is always allowed; tilting by hand switches on 3D buildings.
            m.uiSettings.isTiltGesturesEnabled = true
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.attributionGravity = Gravity.BOTTOM or Gravity.START
            // Keep the OpenStreetMap credit visible above the recording panel and the accuracy button.
            val dp = context.resources.displayMetrics.density
            m.uiSettings.setAttributionMargins((12 * dp).toInt(), 0, 0, (ATTRIBUTION_BOTTOM_DP * dp).toInt())
            mapView.setOnTouchListener(TwoFingerTilt(this, dp))
            readyCallbacks.forEach { it(m) }
            readyCallbacks.clear()
        }
    }

    fun withMap(block: (MapLibreMap) -> Unit) {
        map?.let(block) ?: readyCallbacks.add(block)
    }

    /**
     * Loads the vector style for [look] (recoloured for dark, with terrain shading and 3D buildings if
     * asked for), then calls [onLoaded]. Does nothing if that look is already showing.
     */
    fun setLook(look: MapLook, onLoaded: (Style) -> Unit = {}) = withMap { m ->
        if (styleLook == look) return@withMap
        styleLook = look
        m.setStyle(Style.Builder().fromUri(VectorMaps.STYLE_URL)) { style ->
            if (look.dark) VectorMaps.applyDark(style)
            if (look.terrain) VectorMaps.addTerrain(style, look.dark)
            if (look.buildings) VectorMaps.addBuildings(style, look.dark)
            onLoaded(style)
            styleVersion++
        }
    }
}

/**
 * MapLibre maps kept alive per activity, one per key (e.g. each tab's map), so switching tabs shows the map
 * as it was instead of loading it again. They're destroyed with the activity.
 */
private object VectorMapCache {
    private val byActivity = WeakHashMap<Activity, HashMap<String, VectorMapHolder>>()

    fun get(context: Context, key: String): VectorMapHolder {
        val activity = context.findActivity()
            ?: return VectorMapHolder(context)
        val maps = byActivity.getOrPut(activity) {
            HashMap<String, VectorMapHolder>().also { maps ->
                (activity as? LifecycleOwner)?.lifecycle?.addObserver(LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        maps.values.forEach { it.destroy() }
                        maps.clear()
                        byActivity.remove(activity)
                    }
                })
            }
        }
        return maps.getOrPut(key) { VectorMapHolder(activity) }
    }
}

@Composable
private fun rememberVectorMap(cacheKey: String? = null): VectorMapHolder {
    val context = LocalContext.current
    val holder = remember(cacheKey) { if (cacheKey != null) VectorMapCache.get(context, cacheKey) else VectorMapHolder(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, holder) {
        val mapView = holder.mapView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        // Adding the observer replays ON_START/ON_RESUME if the screen is already showing.
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            // A cached map is only put to sleep; the cache destroys it with the activity.
            if (cacheKey == null) holder.destroy()
        }
    }
    return holder
}

/** Metres covered by one screen pixel across the middle of the map. */
private fun metersPerPixel(m: MapLibreMap, view: View): Float {
    val w = view.width
    if (w <= 0) return 0f
    val y = view.height / 2f
    val a = m.projection.fromScreenLocation(PointF(w / 2f - 50f, y))
    val b = m.projection.fromScreenLocation(PointF(w / 2f + 50f, y))
    return (a.distanceTo(b) / 100.0).toFloat()
}

// The app's zoom levels follow the usual web-map convention of 256 px tiles; MapLibre counts 512 px tiles,
// so it's one level lower.
private fun Float.toVectorZoom(): Double = (this - 1f).toDouble().coerceAtLeast(0.0)

@OptIn(FlowPreview::class)
@Composable
private fun VectorAppMap(
    camera: MapCamera,
    layers: List<MapLayer>,
    look: MapLook,
    refreshKey: Any?,
    latest: LatLng?,
    onMapClick: ((Double, Double) -> Unit)?,
    onMapLongClick: ((Double, Double) -> Unit)?,
    cacheKey: String?,
    route3d: ((GeoBox) -> List<List<ElevationPoint>>)?,
    modifier: Modifier,
    bottomInset: Dp,
    heading: Float?,
) {
    val holder = rememberVectorMap(cacheKey)
    val context = LocalContext.current
    LaunchedEffect(holder, bottomInset) {
        val dp = context.resources.displayMetrics.density
        holder.withMap { m -> m.uiSettings.setAttributionMargins((12 * dp).toInt(), 0, 0, ((ATTRIBUTION_BOTTOM_DP + bottomInset.value) * dp).toInt()) }
    }
    val loadRoute3d by rememberUpdatedState(route3d)
    val overlay = holder.overlay
    SideEffect {
        holder.onClick = onMapClick
        holder.onLongClick = onMapLongClick
    }

    DisposableEffect(camera) {
        holder.camera = camera
        holder.withMap { m ->
            // Listeners are added once per map and report to whichever camera is current.
            if (!holder.setUp) {
                holder.setUp = true
                m.moveCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        MLatLng(camera.initialLat, camera.initialLon), camera.initialZoom.toVectorZoom(),
                    )
                )
                overlay.attach(m)
                val report = {
                    holder.camera?.let {
                        it.bearing = m.cameraPosition.bearing.toFloat()
                        it.cameraTick++
                        it.metersPerPixel = metersPerPixel(m, holder.mapView)
                        it.center = m.cameraPosition.target?.let { t -> t.latitude to t.longitude }
                    }
                }
                m.addOnCameraMoveStartedListener { reason ->
                    holder.gesture = holder.handTilting || reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
                    holder.camera?.moving = true
                }
                m.addOnCameraMoveListener {
                    report()
                    // Tilting by hand switches 3D buildings on; tilting back flat switches them off.
                    if (holder.gesture) {
                        val tilt = m.cameraPosition.tilt
                        if (tilt > AUTO_3D_TILT && !Prefs.buildings3d.value) {
                            holder.tiltedByGesture = true
                            Prefs.setBuildings3d(true)
                        } else if (tilt < AUTO_FLAT_TILT && Prefs.buildings3d.value) {
                            Prefs.setBuildings3d(false)
                        }
                    }
                }
                m.addOnCameraIdleListener {
                    report()
                    holder.gesture = false
                    holder.camera?.moving = false
                }
                // Returning false lets the map handle the tap as usual.
                m.addOnMapClickListener { p ->
                    holder.onClick?.invoke(p.latitude, p.longitude)
                    false
                }
                m.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                    override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) {
                        holder.camera?.let { it.panTick++ }
                    }
                    override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
                    override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) = Unit
                })
                m.addOnMapLongClickListener { p ->
                    val handler = holder.onLongClick
                    handler?.invoke(p.latitude, p.longitude)
                    handler != null
                }
            }
            camera.bearing = m.cameraPosition.bearing.toFloat()
            camera.projector = { lat, lon ->
                m.projection.toScreenLocation(MLatLng(lat, lon)).let { Offset(it.x, it.y) }
            }
        }
        onDispose {
            camera.projector = null
            if (holder.camera === camera) holder.camera = null
        }
    }

    LaunchedEffect(look) { holder.setLook(look) }
    MapBrightness(holder, look.dark)

    LaunchedEffect(look.buildings) {
        holder.withMap { m ->
            // Switched on by tilting: keep the angle the user chose.
            if (look.buildings && holder.tiltedByGesture) {
                holder.tiltedByGesture = false
                return@withMap
            }
            val tilt = if (look.buildings) TILT_3D.toDouble() else 0.0
            if (m.cameraPosition.tilt != tilt) m.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.tiltTo(tilt), 600)
        }
    }

    SideEffect {
        overlay.setLayers(layers.map { TileOverlayView.LayerSpec(it.id, it.source, it.alpha, it.zIndex) })
        overlay.latest = latest?.let { MLatLng(it.latitude, it.longitude) }
        overlay.heading = heading
    }

    LaunchedEffect(refreshKey) {
        delay(1500)
        overlay.refresh()
    }

    // 3D route: while 3D buildings are on, load the routes in view whenever the camera settles (or the style
    // reloads, or new dots arrive) and draw them at their recorded heights.
    LaunchedEffect(holder, camera, look.buildings, route3d != null, refreshKey) {
        if (!look.buildings || route3d == null) {
            holder.withMap { m -> m.style?.let(VectorMaps::clearRoute3d) }
            return@LaunchedEffect
        }
        snapshotFlow { camera.cameraTick to holder.styleVersion }
            .debounce(400)
            .collectLatest {
                val m = holder.map ?: return@collectLatest
                val b = m.projection.visibleRegion.latLngBounds
                // A margin around the view, so routes don't visibly end at the screen edge while panning.
                val padLat = (b.latitudeNorth - b.latitudeSouth) / 2
                val padLon = (b.longitudeEast - b.longitudeWest) / 2
                val box = GeoBox(b.latitudeNorth + padLat, b.latitudeSouth - padLat, b.longitudeEast + padLon, b.longitudeWest - padLon)
                val load = loadRoute3d ?: return@collectLatest
                val segments = withContext(Dispatchers.IO) {
                    // One fixed baseline, so heights don't change as the map moves.
                    Route3d.build(load(box), PointStore.get(holder.mapView.context).altitudeBaseline())
                }
                m.style?.let { VectorMaps.showRoute3d(it, segments) }
            }
    }

    LaunchedEffect(camera) {
        camera.commands.collect { c ->
            holder.withMap { m ->
                val update = if (c.zoom.isNaN()) org.maplibre.android.camera.CameraUpdateFactory.newLatLng(MLatLng(c.lat, c.lon))
                else org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(MLatLng(c.lat, c.lon), c.zoom.toVectorZoom())
                when {
                    c.durationMs > 0 -> m.easeCamera(update, c.durationMs, false)
                    c.animate -> m.animateCamera(update, 600)
                    else -> m.moveCamera(update)
                }
            }
        }
    }

    LaunchedEffect(camera) {
        camera.actions.collect { a ->
            holder.withMap { m ->
                val update = when (a) {
                    MapCamera.Action.ZOOM_IN -> org.maplibre.android.camera.CameraUpdateFactory.zoomBy(1.0)
                    MapCamera.Action.ZOOM_OUT -> org.maplibre.android.camera.CameraUpdateFactory.zoomBy(-1.0)
                    MapCamera.Action.NORTH_UP -> org.maplibre.android.camera.CameraUpdateFactory.bearingTo(0.0)
                }
                m.animateCamera(update, 300)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            FrameLayout(it).apply {
                (holder.mapView.parent as? ViewGroup)?.removeView(holder.mapView)
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                addView(holder.mapView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
        },
    )
}

/** One line on a [TripPreviewMap]. */
class PreviewLine(val points: List<LatLng>, val color: Color, val widthDp: Float = 4f)

/** A small, non-interactive map showing one trip. */
@Composable
fun TripPreviewMap(path: List<LatLng>, modifier: Modifier = Modifier) {
    val lineColor = MapAccent
    TripPreviewLinesMap(remember(path, lineColor) { listOf(PreviewLine(path, lineColor)) }, modifier)
}

/**
 * A small map showing one or more lines (e.g. the candidate roads for a stretch). [interactive] lets it be
 * panned and pinch-zoomed, with zoom and fit buttons; [endpoints] marks where the first line starts (green)
 * and ends (red).
 */
@Composable
fun TripPreviewLinesMap(
    lines: List<PreviewLine>,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    endpoints: Boolean = false,
) {
    val dark = isDarkTheme()
    val all = remember(lines) { lines.flatMap { it.points } }
    val holder = rememberVectorMap()
    MapBrightness(holder, dark)
    LaunchedEffect(lines, dark) {
        holder.styleLook = null // Reload so the lines are added to a fresh style.
        holder.setLook(MapLook(dark)) { style ->
            lines.forEachIndexed { i, l ->
                val geometry = LineString.fromLngLats(l.points.map { Point.fromLngLat(it.longitude, it.latitude) })
                style.addSource(GeoJsonSource("preview-$i", Feature.fromGeometry(geometry)))
                style.addLayer(
                    LineLayer("preview-line-$i", "preview-$i").withProperties(
                        PropertyFactory.lineColor(l.color.toArgb()),
                        PropertyFactory.lineWidth(l.widthDp),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    )
                )
            }
            val first = lines.firstOrNull()?.points
            if (endpoints && first != null && first.size >= 2) {
                listOf("start" to (first.first() to 0xFF43A047.toInt()), "end" to (first.last() to 0xFFE53935.toInt())).forEach { (id, v) ->
                    val (p, c) = v
                    style.addSource(GeoJsonSource("preview-$id", Feature.fromGeometry(Point.fromLngLat(p.longitude, p.latitude))))
                    style.addLayer(
                        CircleLayer("preview-$id-dot", "preview-$id").withProperties(
                            PropertyFactory.circleRadius(6f),
                            PropertyFactory.circleColor(c),
                            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                            PropertyFactory.circleStrokeWidth(2f),
                        )
                    )
                }
            }
            holder.withMap { m ->
                m.uiSettings.setAllGesturesEnabled(interactive)
                if (interactive) {
                    m.uiSettings.isRotateGesturesEnabled = false
                    m.uiSettings.isTiltGesturesEnabled = false
                }
                m.uiSettings.isAttributionEnabled = false
                holder.mapView.post { fitPreview(m, all) }
            }
        }
    }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                (holder.mapView.parent as? ViewGroup)?.removeView(holder.mapView)
                holder.mapView
            },
        )
        if (interactive) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PreviewButton("Zoom in", { holder.withMap { it.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomBy(1.0), 250) } }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                PreviewButton("Zoom out", { holder.withMap { it.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomBy(-1.0), 250) } }) {
                    // A plain minus sign; the core icon set has no "remove" glyph.
                    Text("−", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                PreviewButton("Show the whole trip", { holder.withMap { fitPreview(it, all) } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun fitPreview(m: MapLibreMap, all: List<LatLng>) {
    if (all.size < 2) return
    val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
        .includes(all.map { MLatLng(it.latitude, it.longitude) })
        .build()
    runCatching { m.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds, 48), 300) }
}

@Composable
private fun PreviewButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Tip(label) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            shadowElevation = 2.dp,
            modifier = Modifier.size(36.dp).semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}
