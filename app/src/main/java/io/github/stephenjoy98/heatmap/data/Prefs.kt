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

/** The heatmap's colours: Fire (dark red to white) or Ice (darkest blue to white). */
enum class HeatPalette { FIRE, ICE, WHITE }

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

    /** Settings shown as a page per section (true) or as one long list. */
    private val _settingsAsPages = MutableStateFlow(true)
    val settingsAsPages: StateFlow<Boolean> = _settingsAsPages

    /** Home, work and other places where recording slows down while you're there. */
    private val _savedPlaces = MutableStateFlow<List<SavedPlace>>(emptyList())
    val savedPlaces: StateFlow<List<SavedPlace>> = _savedPlaces

    /** Follow me is on with the app in front: record every second meanwhile (not saved). */
    private val _followBoost = MutableStateFlow(false)
    val followBoost: StateFlow<Boolean> = _followBoost

    fun setFollowBoost(on: Boolean) {
        _followBoost.value = on
    }

    /** The saved place you're at right now while recording, if any (not saved). */
    private val _atPlace = MutableStateFlow<SavedPlace?>(null)
    val atPlace: StateFlow<SavedPlace?> = _atPlace

    private val _mapLayer = MutableStateFlow(MapLayerType.DEFAULT)
    val mapLayer: StateFlow<MapLayerType> = _mapLayer

    private val _buildings3d = MutableStateFlow(false)
    val buildings3d: StateFlow<Boolean> = _buildings3d

    /** The map is tilted into 3D (compass or two fingers); separate from 3D buildings, which also show flat. */
    private val _mapTilted = MutableStateFlow(false)
    val mapTilted: StateFlow<Boolean> = _mapTilted

    /** Whether routes are raised to their recorded heights when the map is in 3D (Map layers › 3D routes). */
    private val _routes3d = MutableStateFlow(true)
    val routes3d: StateFlow<Boolean> = _routes3d

    fun setRoutes3d(on: Boolean) {
        _routes3d.value = on
        sp.edit().putBoolean("routes_3d", on).apply()
    }

    /** Whether the blue dot for where you are shows on the map (Map layers › My location). */
    private val _showLocationDot = MutableStateFlow(true)
    val showLocationDot: StateFlow<Boolean> = _showLocationDot

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

    /** Download new versions from GitHub releases and install them, on Wi-Fi only if [updateWifiOnly]. */
    private val _autoUpdate = MutableStateFlow(true)
    val autoUpdate: StateFlow<Boolean> = _autoUpdate
    private val _updateWifiOnly = MutableStateFlow(true)
    val updateWifiOnly: StateFlow<Boolean> = _updateWifiOnly
    private val _updateFrequency = MutableStateFlow(UpdateFrequency.STABLE)
    val updateFrequency: StateFlow<UpdateFrequency> = _updateFrequency

    /** How much lighter (positive) or darker (negative) the base map is drawn, for the dark and light themes. */
    private val _mapBrightnessDark = MutableStateFlow(0f)
    val mapBrightnessDark: StateFlow<Float> = _mapBrightnessDark
    private val _mapBrightnessLight = MutableStateFlow(0f)
    val mapBrightnessLight: StateFlow<Float> = _mapBrightnessLight

    /** How colourful the map is, 0 (muted standard colours) to 1 (vivid), for the dark and light themes. */
    private val _mapColourDark = MutableStateFlow(0f)
    val mapColourDark: StateFlow<Float> = _mapColourDark
    private val _mapColourLight = MutableStateFlow(0f)
    val mapColourLight: StateFlow<Float> = _mapColourLight

    private val _heatPalette = MutableStateFlow(HeatPalette.FIRE)
    val heatPalette: StateFlow<HeatPalette> = _heatPalette

    fun setHeatPalette(p: HeatPalette) {
        _heatPalette.value = p
        sp.edit().putString("heat_palette", p.name).apply()
    }

    /** Whether Map colours also recolours roads; off keeps roads in the standard look. */
    private val _mapColourRoads = MutableStateFlow(false)
    val mapColourRoads: StateFlow<Boolean> = _mapColourRoads

    fun setMapColourRoads(on: Boolean) {
        _mapColourRoads.value = on
        sp.edit().putBoolean("map_colour_roads", on).apply()
    }

    fun setMapColour(dark: Boolean, amount: Float) {
        val v = amount.coerceIn(0f, 1f)
        if (dark) _mapColourDark.value = v else _mapColourLight.value = v
        sp.edit().putFloat(if (dark) "map_colour_dark" else "map_colour_light", v).apply()
    }

    fun setMapBrightness(dark: Boolean, amount: Float) {
        val v = amount.coerceIn(-MAX_MAP_BRIGHTNESS, MAX_MAP_BRIGHTNESS)
        if (dark) _mapBrightnessDark.value = v else _mapBrightnessLight.value = v
        sp.edit().putFloat(if (dark) "map_brightness_dark" else "map_brightness_light", v).apply()
    }

    const val MAX_MAP_BRIGHTNESS = 0.6f

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
        // Before the first "New" markers and changelog dots: count from the version the user last saw (a fresh
        // install starts with nothing marked).
        if (sp.getString("attention_base", null) == null) {
            val from = sp.getString("last_seen_version", null) ?: BuildConfig.VERSION_NAME
            sp.edit().putString("attention_base", from).putString("changelog_read", from).apply()
        }
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
        _mapBrightnessDark.value = sp.getFloat("map_brightness_dark", 0f)
        _mapBrightnessLight.value = sp.getFloat("map_brightness_light", 0f)
        _mapColourDark.value = sp.getFloat("map_colour_dark", 0f)
        _mapColourLight.value = sp.getFloat("map_colour_light", 0f)
        _mapColourRoads.value = sp.getBoolean("map_colour_roads", false)
        _heatPalette.value = HeatPalette.entries.byName(sp.getString("heat_palette", null)) ?: HeatPalette.FIRE
        // Updates aren't downloaded on their own any more: the app offers them and you choose (asked 2026-10-21).
        // Turn automatic updates off once for everyone who had them on by default.
        if (!sp.getBoolean("auto_update_off_v1", false)) {
            sp.edit().putBoolean("auto_update", false).putBoolean("auto_update_off_v1", true).apply()
        }
        _autoUpdate.value = sp.getBoolean("auto_update", false)
        _updateWifiOnly.value = sp.getBoolean("update_wifi_only", true)
        _updateFrequency.value = runCatching { UpdateFrequency.valueOf(sp.getString("update_frequency", null)!!) }.getOrDefault(UpdateFrequency.STABLE)
        _autoInterval.value = sp.getBoolean("auto_interval", false)
        _powerProfile.value = runCatching { PowerProfile.valueOf(sp.getString("power_profile", null)!!) }.getOrDefault(PowerProfile.USER)
        _liveIntervalSec.value = _intervalSec.value
        _savedPlaces.value = SavedPlace.decode(sp.getString("saved_places", null))
        _settingsAsPages.value = sp.getBoolean("settings_pages", true)
        _mapLayer.value = MapLayerType.entries.byName(sp.getString("map_layer", null)) ?: MapLayerType.DEFAULT
        _buildings3d.value = sp.getBoolean("buildings_3d", false)
        // Before 2.47.0 one switch did both: a map with 3D buildings was a tilted one.
        _mapTilted.value = sp.getBoolean("map_tilted", _buildings3d.value)
        _showLocationDot.value = sp.getBoolean("show_location_dot", true)
        _routes3d.value = sp.getBoolean("routes_3d", true)
        _dirtyBackupDays.value = sp.getStringSet("dirty_backup_days", null)?.toSet() ?: emptySet()
        _snapToRoads.value = sp.getBoolean("snap_roads", true)
    }

    fun setIntervalSec(seconds: Int) {
        val clamped = seconds.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
        _intervalSec.value = clamped
        sp.edit().putInt("interval_sec", clamped).apply()
        becameUserProfile()
    }

    /** The battery profile in use; [PowerProfile.USER] means your own Recording settings. */
    private val _powerProfile = MutableStateFlow(PowerProfile.USER)
    val powerProfile: StateFlow<PowerProfile> = _powerProfile

    /** Your own interval, automatic choice and accuracy, kept while another profile is on. */
    data class UserRecording(val intervalSec: Int, val auto: Boolean, val highAccuracy: Boolean)

    val userRecording: UserRecording
        get() = if (_powerProfile.value == PowerProfile.USER) {
            UserRecording(_intervalSec.value, _autoInterval.value, _highAccuracy.value)
        } else {
            runCatching {
                val o = org.json.JSONObject(sp.getString("user_profile", null)!!)
                UserRecording(o.getInt("i"), o.getBoolean("a"), o.getBoolean("h"))
            }.getOrElse { UserRecording(_intervalSec.value, false, true) }
        }

    /**
     * Switches battery profile. Leaving User keeps your own settings so choosing User again brings them back;
     * the others turn the automatic interval on with their own pace and accuracy. Restart the recorder after.
     */
    fun applyPowerProfile(profile: PowerProfile) {
        if (profile == _powerProfile.value) return
        val mine = userRecording
        if (_powerProfile.value == PowerProfile.USER) {
            sp.edit().putString(
                "user_profile",
                org.json.JSONObject().put("i", mine.intervalSec).put("a", mine.auto).put("h", mine.highAccuracy).toString(),
            ).apply()
        }
        _powerProfile.value = profile
        val edit = sp.edit().putString("power_profile", profile.name)
        if (profile == PowerProfile.USER) {
            _intervalSec.value = mine.intervalSec
            _autoInterval.value = mine.auto
            _highAccuracy.value = mine.highAccuracy
            edit.putInt("interval_sec", mine.intervalSec).putBoolean("auto_interval", mine.auto).putBoolean("high_accuracy", mine.highAccuracy)
        } else {
            _autoInterval.value = true
            _highAccuracy.value = profile.highAccuracy
            edit.putBoolean("auto_interval", true).putBoolean("high_accuracy", profile.highAccuracy)
        }
        edit.apply()
    }

    /** Changing a Recording setting by hand makes it your own: the profile becomes User. */
    private fun becameUserProfile() {
        if (_powerProfile.value == PowerProfile.USER) return
        _powerProfile.value = PowerProfile.USER
        sp.edit().putString("power_profile", PowerProfile.USER.name).apply()
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
        becameUserProfile()
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

    // ---- What's waiting to be looked at (see ui/Attention) ----

    /** The version a section was last opened in, so its "New" marker shows only for changes since then. */
    fun sectionSeen(key: String): String? = sp.getString("seen_$key", null)

    fun setSectionSeen(key: String, version: String) {
        sp.edit().putString("seen_$key", version).apply()
    }

    /** The version from which "New" markers count: the one installed when they first appeared, for sections never opened. */
    val attentionBase: String? get() = sp.getString("attention_base", null)

    /** The version whose changelog was last read, in the "What's new" sheet or on the Changelog page. */
    var changelogRead: String?
        get() = sp.getString("changelog_read", null)
        set(value) = sp.edit().putString("changelog_read", value).apply()

    /** The new version last looked at on Settings › Updates (its label and build), so its dot goes away. */
    var updateSeen: String?
        get() = sp.getString("update_seen", null)
        set(value) = sp.edit().putString("update_seen", value).apply()

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
        becameUserProfile()
    }

    fun setSettingsAsPages(on: Boolean) {
        _settingsAsPages.value = on
        sp.edit().putBoolean("settings_pages", on).apply()
    }

    fun friendsCache(): String? = sp.getString("friends_cache", null)

    fun setFriendsCache(json: String) {
        sp.edit().putString("friends_cache", json).apply()
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

    fun setMapTilted(on: Boolean) {
        _mapTilted.value = on
        sp.edit().putBoolean("map_tilted", on).apply()
    }

    fun setShowLocationDot(on: Boolean) {
        _showLocationDot.value = on
        sp.edit().putBoolean("show_location_dot", on).apply()
    }

    // Everything the user chooses or sets up, kept in the backup so a new phone is set up the same way.
    // (Not state like "recording now", sync bookkeeping or caches.)
    private val BACKED_UP_KEYS = listOf(
        "interval_sec", "auto_interval", "high_accuracy", "display_mode", "map_theme", "units", "dynamic_color",
        "smooth_routes", "snap_roads", "map_layer", "buildings_3d", "map_tilted", "mode_filter", "ai_engine", "cloud_model",
        "share_routes", "auto_backup", "backup_every_hours", "settings_pages", "saved_places", "custom_modes",
        "deleted_modes", "next_mode_id", "mode_labels", "mode_colors", "modes_seeded_v1", "auto_update", "update_wifi_only", "update_frequency",
        "power_profile", "user_profile", "map_brightness_dark", "map_brightness_light", "map_colour_dark", "map_colour_light",
        "show_location_dot", "map_colour_roads", "heat_palette", "routes_3d",
    )

    /** The user's settings as JSON, each value tagged with its type: {"units": {"s": "METRIC"}, ...}. */
    fun exportSettings(): String {
        val out = org.json.JSONObject()
        val all = sp.all
        for (key in BACKED_UP_KEYS) {
            val v = all[key] ?: continue
            val tagged = org.json.JSONObject()
            when (v) {
                is Boolean -> tagged.put("b", v)
                is Int -> tagged.put("i", v)
                is Long -> tagged.put("l", v)
                is Float -> tagged.put("f", v.toDouble())
                is String -> tagged.put("s", v)
                is Set<*> -> tagged.put("set", org.json.JSONArray(v.filterIsInstance<String>()))
                else -> continue
            }
            out.put(key, tagged)
        }
        return out.toString()
    }

    /** Applies settings from a backup and reloads them, so every screen picks them up. */
    fun importSettings(context: Context, json: String) {
        val o = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return
        val e = sp.edit()
        for (key in BACKED_UP_KEYS) {
            val t = o.optJSONObject(key) ?: continue
            when {
                t.has("b") -> e.putBoolean(key, t.getBoolean("b"))
                t.has("i") -> e.putInt(key, t.getInt("i"))
                t.has("l") -> e.putLong(key, t.getLong("l"))
                t.has("f") -> e.putFloat(key, t.getDouble("f").toFloat())
                t.has("s") -> e.putString(key, t.getString("s"))
                t.has("set") -> t.getJSONArray("set").let { a -> e.putStringSet(key, (0 until a.length()).map { a.getString(it) }.toSet()) }
            }
        }
        e.commit()
        init(context)
    }

    /**
     * Whether every day has been backed up with everything the backup now holds (trips, road answers and deleted dots,
     * added in 2.26.0). Bump the key when the backup gains something per day.
     */
    val backupComplete: Boolean get() = sp.getBoolean("backup_complete_v2", false)

    fun setBackupComplete() {
        sp.edit().putBoolean("backup_complete_v2", true).apply()
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

    /** Whether road-matched trips have been checked once for detours the recording doesn't show (RoadSnapper). */
    var snapsRechecked: Boolean
        get() = sp.getBoolean("snaps_rechecked_v1", false)
        set(value) = sp.edit().putBoolean("snaps_rechecked_v1", value).apply()

    /** Whether train and boat trips kept as recorded have been queued once for matching to their tracks. */
    var tracksSnapped: Boolean
        get() = sp.getBoolean("tracks_snapped_v1", false)
        set(value) = sp.edit().putBoolean("tracks_snapped_v1", value).apply()

    /** Whether the whole history has been checked once for GPS blips (see PointStore.findSpikes). */
    var spikesCleaned: Boolean
        get() = sp.getBoolean("spikes_cleaned_v1", false)
        set(value) = sp.edit().putBoolean("spikes_cleaned_v1", value).apply()

    /** The release last announced in a notification, so each one is announced once. */
    var lastUpdateNotified: String?
        get() = sp.getString("last_update_notified", null)
        set(value) = sp.edit().putString("last_update_notified", value).apply()

    fun setAutoUpdate(on: Boolean) {
        _autoUpdate.value = on
        sp.edit().putBoolean("auto_update", on).apply()
    }

    fun setUpdateFrequency(f: UpdateFrequency) {
        _updateFrequency.value = f
        sp.edit().putString("update_frequency", f.name).apply()
    }

    fun setUpdateWifiOnly(on: Boolean) {
        _updateWifiOnly.value = on
        sp.edit().putBoolean("update_wifi_only", on).apply()
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

/**
 * Which updates to offer: [BETA], every build as soon as it's made (newest changes, may have rough edges);
 * [STABLE], releases that have been tried out first; [STABLE_FEATURES], only stable releases with new
 * features (minor or major versions).
 */
enum class UpdateFrequency { BETA, STABLE, STABLE_FEATURES }
