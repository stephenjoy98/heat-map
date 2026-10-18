package io.github.stephenjoy98.heatmap.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DisplayMode { ROUTES, POINTS }

/** App settings, persisted in SharedPreferences and observable from the UI and the service. */
object Prefs {
    const val MIN_INTERVAL_SEC = 15
    const val MAX_INTERVAL_SEC = 24 * 60 * 60

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

    fun init(context: Context) {
        sp = context.getSharedPreferences("ticker", Context.MODE_PRIVATE)
        _intervalSec.value = sp.getInt("interval_sec", 60).coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
        _tracking.value = sp.getBoolean("tracking", false)
        _highAccuracy.value = sp.getBoolean("high_accuracy", true)
        _displayMode.value = runCatching { DisplayMode.valueOf(sp.getString("display_mode", null)!!) }
            .getOrDefault(DisplayMode.ROUTES)
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
}
