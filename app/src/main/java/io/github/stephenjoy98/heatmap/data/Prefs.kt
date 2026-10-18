package io.github.stephenjoy98.heatmap.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DisplayMode { ROUTES, POINTS }

enum class MapTheme { SYSTEM, DARK, LIGHT }

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

    /** Whether the user wants recording on; used to resume after a reboot. */
    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking

    private val _highAccuracy = MutableStateFlow(true)
    val highAccuracy: StateFlow<Boolean> = _highAccuracy

    private val _displayMode = MutableStateFlow(DisplayMode.ROUTES)
    val displayMode: StateFlow<DisplayMode> = _displayMode

    private val _mapTheme = MutableStateFlow(MapTheme.DARK)
    val mapTheme: StateFlow<MapTheme> = _mapTheme

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

    private val _lastBackup = MutableStateFlow(0L)
    val lastBackup: StateFlow<Long> = _lastBackup

    fun init(context: Context) {
        sp = context.getSharedPreferences("ticker", Context.MODE_PRIVATE)
        _intervalSec.value = sp.getInt("interval_sec", 60).coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
        _tracking.value = sp.getBoolean("tracking", false)
        _highAccuracy.value = sp.getBoolean("high_accuracy", true)
        _displayMode.value = DisplayMode.entries.byName(sp.getString("display_mode", null)) ?: DisplayMode.ROUTES
        _mapTheme.value = MapTheme.entries.byName(sp.getString("map_theme", null)) ?: MapTheme.DARK
        _modeFilter.value = sp.getStringSet("mode_filter", null)
            ?.mapNotNull { it.toIntOrNull()?.let(TravelMode::fromId) }?.toSet()
            ?: TravelMode.entries.toSet()
        _aiEngine.value = AiEngine.entries.byName(sp.getString("ai_engine", null))
        _cloudModel.value = sp.getString("cloud_model", null) ?: DEFAULT_CLOUD_MODEL
        _onboarded.value = sp.getBoolean("onboarded", false)
        _shareRoutes.value = sp.getBoolean("share_routes", true)
        _autoBackup.value = sp.getBoolean("auto_backup", true)
        _lastBackup.value = sp.getLong("last_backup", 0L)
    }

    fun setIntervalSec(seconds: Int) {
        val clamped = seconds.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
        _intervalSec.value = clamped
        sp.edit().putInt("interval_sec", clamped).apply()
    }

    fun setTracking(on: Boolean) {
        _tracking.value = on
        sp.edit().putBoolean("tracking", on).apply()
    }

    fun setHighAccuracy(on: Boolean) {
        _highAccuracy.value = on
        sp.edit().putBoolean("high_accuracy", on).apply()
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
        sp.edit().putString("display_mode", mode.name).apply()
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
    }

    fun setShareRoutes(on: Boolean) {
        _shareRoutes.value = on
        sp.edit().putBoolean("share_routes", on).apply()
    }

    fun setAutoBackup(on: Boolean) {
        _autoBackup.value = on
        sp.edit().putBoolean("auto_backup", on).apply()
    }

    fun setLastBackup(time: Long) {
        _lastBackup.value = time
        sp.edit().putLong("last_backup", time).apply()
    }

    private fun <T : Enum<T>> List<T>.byName(name: String?): T? = firstOrNull { it.name == name }
}
