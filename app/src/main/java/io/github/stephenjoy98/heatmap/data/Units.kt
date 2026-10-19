package io.github.stephenjoy98.heatmap.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.roundToInt

enum class UnitSystem { METRIC, IMPERIAL }

/**
 * Formats distances, heights and speeds in the chosen unit system (Settings → Units). The choice is held in
 * Compose state, so anything on screen that used it updates as soon as it changes.
 */
object Units {
    var system by mutableStateOf(UnitSystem.METRIC)
        internal set

    val imperial: Boolean get() = system == UnitSystem.IMPERIAL

    private const val KM_PER_MILE = 1.609344
    private const val FEET_PER_METER = 3.28084

    /** Miles where people measure in miles: the US, the UK, Liberia and Myanmar. */
    fun defaultFor(locale: Locale): UnitSystem =
        if (locale.country in setOf("US", "GB", "LR", "MM")) UnitSystem.IMPERIAL else UnitSystem.METRIC

    /** A distance given in kilometres, in the long unit: kilometres or miles. */
    fun fromKm(km: Double): Double = if (imperial) km / KM_PER_MILE else km

    /** "km" or "mi". */
    val longUnit: String get() = if (imperial) "mi" else "km"

    /** "kilometres" or "miles", for sentences. */
    val longUnitName: String get() = if (imperial) "miles" else "kilometres"

    /** "12.3 km" or "7.6 mi"; whole numbers from 100 up. */
    fun distance(km: Double): String {
        val v = fromKm(km)
        return if (v >= 100) "${v.roundToInt()} $longUnit" else String.format(Locale.US, "%.1f %s", v, longUnit)
    }

    /** A short length given in metres, as a whole number of metres or feet. */
    fun lengthValue(meters: Double): Int = (if (imperial) meters * FEET_PER_METER else meters).roundToInt()

    /** "m" or "ft". */
    val shortUnit: String get() = if (imperial) "ft" else "m"

    /** "METERS" or "FEET", for labels. */
    val shortUnitLabel: String get() = if (imperial) "FEET" else "METERS"

    /** "12 m" or "39 ft". */
    fun length(meters: Double): String = "${lengthValue(meters)} $shortUnit"

    /** "40 km/h" or "25 mph". */
    fun speed(kmh: Double): String =
        if (imperial) "${(kmh / KM_PER_MILE).roundToInt()} mph" else "${kmh.roundToInt()} km/h"
}
