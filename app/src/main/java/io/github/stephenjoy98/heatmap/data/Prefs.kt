package io.github.stephenjoy98.heatmap.data

import android.content.Context
import android.content.SharedPreferences
import io.github.stephenjoy98.heatmap.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

enum class DisplayMode { ROUTES, POINTS }

enum class MapTheme { SYSTEM, DARK, LIGHT }

enum class MapLayerType { DEFAULT, TERRAIN }

/** Where Review insights are written. */
enum class AiEngine { ON_DEVICE, CLOUD, STATS }

/** App settings, persisted in SharedPreferences and observable from the UI, the service and workers. */
object Prefs {
    const val MIN_INTERVAL_SEC = 15
    const val MAX_INTERVAL_SEC = 24 * 60 * 60
    const val DEFAULT_CLOUD_MODEL = "gemini-2.5-flash"

    private lateinit var sp: SharedPreferences

    private val _intervalSec = MutableStateFlow(60)
    val intervalSec: StateFlow<Int> = _intervalSec

    /** Let the app choose the interval from movement and activity instead of a fixed tick. */
    private val _autoInterval = MutableStateFlow(false)
    val autoInterval: StateFlow<Boolean> = _autoInterval

    /** The interval the recorder is using right now (differs from [intervalSec] in automatic mode). */
    private val _liveIntervalSec = MutableStateFlow(60)
    val liveIntervalSec: StateFlow<Int> = _liveIntervalSec

    /** Home, work and other places where recording slows down while you're there. */
    private val _savedPlaces = MutableStateFlow<List<SavedPlace>>(emptyList())
    val savedPlaces: StateFlow<List<SavedPlace>> = _savedPlaces

    /** The saved place you're at right now while recording, if any (not saved). */
    private val _atPlace = MutableStateFlow<SavedPlace?>(null)
    val atPlace: StateFlow<SavedPlace?> = _atPlace

    private val _mapLayer = MutableStateFlow(MapLayerType.DEFAULT)
    val mapLayer: StateFlow<MapLayerType> = _mapLayer

    private val _buildings3d = MutableStateFlow(false)
    val buildings3d: StateFlow<Boolean> = _buildings3d

    /** Days ("yyyy-MM-dd") whose points were edited and must be re-uploaded to the backup. */
    private val _dirtyBackupDays = MutableStateFlow<Set<String>>(emptySet())
    val dirtyBackupDays: StateFlow<Set<String>> = _dirtyBackupDays

    /** Whether the user wants recording on; used to resume after a reboot. */
    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking

    /** The travel mode picked for the trip being taken right now (tags new dots), or null to detect it. */
    private val _currentMode = MutableStateFlow<Int?>(null)
    val currentMode: StateFlow<Int?> = _currentMode

    private val _highAccuracy = MutableStateFlow(true)
    val highAccuracy: StateFlow<Boolean> = _highAccuracy

    private val _displayMode = MutableStateFlow(DisplayMode.ROUTES)
    val displayMode: StateFlow<DisplayMode> = _displayMode

    private val _mapTheme = MutableStateFlow(MapTheme.DARK)
    val mapTheme: StateFlow<MapTheme> = _mapTheme

    private val _units = MutableStateFlow(UnitSystem.METRIC)
    val units: StateFlow<UnitSystem> = _units

    /** Travel modes shown on the map. [TravelMode.UNKNOWN] covers points not yet assigned a mode. */
    private val _modeFilter = MutableStateFlow(TravelMode.entries.toSet())
    val modeFilter: StateFlow<Set<TravelMode>> = _modeFilter

    /** Null until the user has picked one during onboarding. */
    private val _aiEngine = MutableStateFlow<AiEngine?>(null)
    val aiEngine: StateFlow<AiEngine?> = _aiEngine

    private val _cloudModel = MutableStateFlow(DEFAULT_CLOUD_MODEL)
    val cloudModel: StateFlow<String> = _cloudModel

    private val _onboarded = MutableStateFlow(false)
    val onboarded: StateFlow<Boolean> = _onboarded

    private val _shareRoutes = MutableStateFlow(true)
    val shareRoutes: StateFlow<Boolean> = _shareRoutes

    private val _autoBackup = MutableStateFlow(true)
    val autoBackup: StateFlow<Boolean> = _autoBackup

    /** How often automatic backups run, in hours: 6, 12, 24 or 168 (weekly). */
    private val _backupEveryHours = MutableStateFlow(24)
    val backupEveryHours: StateFlow<Int> = _backupEveryHours


    /** Material You: take the app's colours from the wallpaper (Android 12+). */
    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    /** Draw unsnapped routes as smooth curves rather than straight hops between fixes. */
    private val _smoothRoutes = MutableStateFlow(true)
    val smoothRoutes: StateFlow<Boolean> = _smoothRoutes

    /** Match finished trips to OpenStreetMap roads (sends trip coordinates, without times, to Valhalla). */
    private val _snapToRoads = MutableStateFlow(true)
    val snapToRoads: StateFlow<Boolean> = _snapToRoads

    private val _lastBackup = MutableStateFlow(0L)
    val lastBackup: StateFlow<Long> = _lastBackup

    fun init(context: Context) {
        sp = context.getSharedPreferences("ticker", Context.MODE_PRIVATE)
        _intervalSec.value = sp.getInt("interval_sec", 60).coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
        _tracking.value = sp.getBoolean("tracking", false)
        _highAccuracy.value = sp.getBoolean("high_accuracy", true)
        _displayMode.value = DisplayMode.entries.byName(sp.getString("display_mode", null)) ?: DisplayMode.ROUTES
        _mapTheme.value = MapTheme.entries.byName(sp.getString("map_theme", null)) ?: MapTheme.DARK
        _units.value = UnitSystem.entries.byName(sp.getString("units", null)) ?: Units.defaultFor(Locale.getDefault())
        Units.system = _units.value
        TravelMode.loadAdded(
            sp.getString("custom_modes", null),
            sp.getString("deleted_modes", null).orEmpty().split(',').mapNotNull { it.toIntOrNull() },
            sp.getInt("next_mode_id", TravelMode.FIRST_CUSTOM_ID),
        )
        TravelMode.loadLabels(sp.getString("mode_labels", null))
        val modesBefore = TravelMode.entries
        if (!sp.getBoolean("modes_seeded_v1", false)) {
            TravelMode.seedDefaults()
            sp.edit().putBoolean("modes_seeded_v1", true).apply()
            saveModes()
        }
        ModeColors.replace(ModeColors.decode(sp.getString("mode_colors", null)))
        _modeFilter.value = sp.getStringSet("mode_filter", null)
            ?.mapNotNull { it.toIntOrNull()?.let(TravelMode::fromId) }?.toSet()
            // Modes added just now are shown if every mode was.
            ?.let { f -> if (f.containsAll(modesBefore)) f + TravelMode.entries else f }
            ?: TravelMode.entries.toSet()
        _aiEngine.value = AiEngine.entries.byName(sp.getString("ai_engine", null))
        _cloudModel.value = sp.getString("cloud_model", null) ?: DEFAULT_CLOUD_MODEL
        _onboarded.value = sp.getBoolean("onboarded", false)
        _currentMode.value = if (sp.contains("current_mode")) sp.getInt("current_mode", 0).takeIf { id -> TravelMode.fromId(id).id == id } else null
        _shareRoutes.value = sp.getBoolean("share_routes", true)
        _autoBackup.value = sp.getBoolean("auto_backup", true)
        _backupEveryHours.value = sp.getInt("backup_every_hours", 24)
        _lastBackup.value = sp.getLong("last_backup", 0L)
        _dynamicColor.value = sp.getBoolean("dynamic_color", true)
        _smoothRoutes.value = sp.getBoolean("smooth_routes", true)
        _autoInterval.value = sp.getBoolean("auto_interval", false)
        _liveIntervalSec.value = _intervalSec.value
        _savedPlaces.value = SavedPlace.decode(sp.getString("saved_places", null))
        _mapLayer.value = MapLayerType.entries.byName(sp.getString("map_layer", null)) ?: MapLayerType.DEFAULT
        _buildings3d.value = sp.getBoolean("buildings_3d", false)
        _dirtyBackupDays.value = sp.getStringSet("dirty_backup_days", null)?.toSet() ?: emptySet()
        _snapToRoads.value = sp.getBoolean("snap_roads", true)
    }

    fun setIntervalSec(seconds: Int) {
        val clamped = seconds.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
        _intervalSec.value = clamped
        sp.edit().putInt("interval_sec", clamped).apply()
    }

    fun setTracking(on: Boolean) {
        _tracking.value = on
        sp.edit().putBoolean("tracking", on).apply()
        // A trip picked for "right now" ends when recording stops.
        if (!on) setCurrentMode(null)
    }

    fun setCurrentMode(mode: TravelMode?) {
        _currentMode.value = mode?.id
        sp.edit().apply { if (mode == null) remove("current_mode") else putInt("current_mode", mode.id) }.apply()
    }

    fun setHighAccuracy(on: Boolean) {
        _highAccuracy.value = on
        sp.edit().putBoolean("high_accuracy", on).apply()
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
        sp.edit().putString("display_mode", mode.name).apply()
    }

    /** Sets a travel mode's colour; null goes back to its default. */
    fun setModeColor(mode: TravelMode, color: Long?) {
        val colors = ModeColors.custom.toMutableMap()
        if (color == null || color == mode.defaultColor) colors.remove(mode) else colors[mode] = color
        ModeColors.replace(colors)
        sp.edit().putString("mode_colors", ModeColors.encode(colors)).apply()
    }

    private fun saveModes() {
        sp.edit()
            .putString("custom_modes", TravelMode.encodeAdded())
            .putString("deleted_modes", TravelMode.deletedBuiltIns.joinToString(","))
            .putInt("next_mode_id", TravelMode.nextId)
            .putString("mode_labels", TravelMode.encodeLabels())
            .apply()
        ModeColors.bump()
    }

    /** Adds a travel mode. If the map shows every mode, it shows the new one too. */
    fun addMode(label: String, color: Long, kind: TravelMode.Kind, parent: TravelMode? = null): TravelMode {
        val showingAll = _modeFilter.value.containsAll(TravelMode.entries)
        // A sub-mode shows on the map whenever its parent does.
        val parentShown = parent != null && parent in _modeFilter.value
        val mode = TravelMode.create(label, color, kind, parent?.id)
        saveModes()
        if (showingAll || parentShown) setModeFilter(_modeFilter.value + mode)
        return mode
    }

    fun editMode(mode: TravelMode, label: String, color: Long, kind: TravelMode.Kind, parent: TravelMode?) {
        TravelMode.edit(mode, label, color, kind, parent?.id)
        saveModes()
    }

    /**
     * Removes a travel mode from the lists. Its trips should be moved to another mode first
     * ([PointStore.replaceMode]). Built-in modes can be added back with [restoreMode].
     */
    fun deleteMode(mode: TravelMode) {
        if (!mode.deletable) return
        if (mode.builtIn) TravelMode.deletedBuiltIns += mode.id else TravelMode.added.remove(mode)
        if (mode in ModeColors.custom) setModeColor(mode, null)
        saveModes()
        val filter = _modeFilter.value - mode
        setModeFilter(filter.ifEmpty { TravelMode.entries.toSet() })
    }

    /** Adds the travel modes saved in a backup. */
    fun mergeModes(added: String?, next: Int) {
        val showingAll = _modeFilter.value.containsAll(TravelMode.entries)
        TravelMode.merge(added, next)
        saveModes()
        setModeFilter(if (showingAll) TravelMode.entries.toSet() else _modeFilter.value)
    }

    /** Adds a deleted built-in mode back. */
    fun restoreMode(mode: TravelMode) {
        val showingAll = _modeFilter.value.containsAll(TravelMode.entries)
        TravelMode.deletedBuiltIns.remove(mode.id)
        saveModes()
        if (showingAll) setModeFilter(_modeFilter.value + mode)
    }

    fun resetModeColors() {
        ModeColors.replace(emptyMap())
        sp.edit().remove("mode_colors").apply()
    }

    fun setUnits(units: UnitSystem) {
        _units.value = units
        Units.system = units
        sp.edit().putString("units", units.name).apply()
    }

    fun setMapTheme(theme: MapTheme) {
        _mapTheme.value = theme
        sp.edit().putString("map_theme", theme.name).apply()
    }

    fun setModeFilter(modes: Set<TravelMode>) {
        _modeFilter.value = modes
        sp.edit().putStringSet("mode_filter", modes.map { it.id.toString() }.toSet()).apply()
    }

    fun setAiEngine(engine: AiEngine) {
        _aiEngine.value = engine
        sp.edit().putString("ai_engine", engine.name).apply()
    }

    fun setCloudModel(model: String) {
        val m = model.trim().ifEmpty { DEFAULT_CLOUD_MODEL }
        _cloudModel.value = m
        sp.edit().putString("cloud_model", m).apply()
    }

    fun setOnboarded() {
        _onboarded.value = true
        sp.edit().putBoolean("onboarded", true).apply()
        // A fresh install has nothing new to show yet.
        if (lastSeenVersion == null) setLastSeenVersion(BuildConfig.VERSION_NAME)
    }

    /** The app version whose "What's new" the user last saw; null on a fresh install or when updating from before 2.11.0. */
    val lastSeenVersion: String? get() = sp.getString("last_seen_version", null)

    fun setLastSeenVersion(version: String) {
        sp.edit().putString("last_seen_version", version).apply()
    }

    fun setShareRoutes(on: Boolean) {
        _shareRoutes.value = on
        sp.edit().putBoolean("share_routes", on).apply()
    }

    fun setBackupEveryHours(hours: Int) {
        _backupEveryHours.value = hours
        sp.edit().putInt("backup_every_hours", hours).apply()
    }

    fun setAutoBackup(on: Boolean) {
        _autoBackup.value = on
        sp.edit().putBoolean("auto_backup", on).apply()
    }

    fun setDynamicColor(on: Boolean) {
        _dynamicColor.value = on
        sp.edit().putBoolean("dynamic_color", on).apply()
    }

    fun setAutoInterval(on: Boolean) {
        _autoInterval.value = on
        sp.edit().putBoolean("auto_interval", on).apply()
    }

    fun setSavedPlaces(places: List<SavedPlace>) {
        _savedPlaces.value = places
        sp.edit().putString("saved_places", SavedPlace.encode(places)).apply()
    }

    fun setAtPlace(place: SavedPlace?) {
        _atPlace.value = place
    }

    fun setLiveIntervalSec(seconds: Int) {
        _liveIntervalSec.value = seconds
    }

    fun setMapLayer(layer: MapLayerType) {
        _mapLayer.value = layer
        sp.edit().putString("map_layer", layer.name).apply()
    }

    fun setBuildings3d(on: Boolean) {
        _buildings3d.value = on
        sp.edit().putBoolean("buildings_3d", on).apply()
    }

    fun addDirtyBackupDays(days: Collection<String>) {
        val next = _dirtyBackupDays.value + days
        _dirtyBackupDays.value = next
        sp.edit().putStringSet("dirty_backup_days", next).apply()
    }

    fun clearDirtyBackupDays(days: Collection<String>) {
        val next = _dirtyBackupDays.value - days.toSet()
        _dirtyBackupDays.value = next
        sp.edit().putStringSet("dirty_backup_days", next).apply()
    }

    /** Shared routes (Firestore ids) whose trips were deleted, to remove on the next sync. */
    val pendingRouteDeletes: Set<String> get() = sp.getStringSet("pending_route_deletes", null)?.toSet() ?: emptySet()

    fun setPendingRouteDeletes(ids: Set<String>) {
        sp.edit().putStringSet("pending_route_deletes", ids).apply()
    }

    fun setSmoothRoutes(on: Boolean) {
        _smoothRoutes.value = on
        sp.edit().putBoolean("smooth_routes", on).apply()
    }

    fun setSnapToRoads(on: Boolean) {
        _snapToRoads.value = on
        sp.edit().putBoolean("snap_roads", on).apply()
    }

    fun setLastBackup(time: Long) {
        _lastBackup.value = time
        sp.edit().putLong("last_backup", time).apply()
    }

    private fun <T : Enum<T>> List<T>.byName(name: String?): T? = firstOrNull { it.name == name }
}
