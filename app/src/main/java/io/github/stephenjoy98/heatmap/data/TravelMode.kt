package io.github.stephenjoy98.heatmap.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue

/** How a stretch of the history was travelled. Ids are stored in the database; never reuse one. */
enum class TravelMode(val id: Int, val label: String, val defaultColor: Long) {
    UNKNOWN(0, "Other", 0xFF9E9E9E),
    WALK(1, "Walking", 0xFF66BB6A),
    CYCLE(2, "Cycling", 0xFF29B6F6),
    DRIVE(3, "Driving", 0xFFFF7A1A),
    RIDESHARE(4, "Car share", 0xFFAB47BC),
    TRANSIT(5, "Transit", 0xFFFFCA28);

    /** The colour chosen in Settings → Travel mode colours, or the default. */
    val color: Long get() = ModeColors.custom[this] ?: defaultColor

    companion object {
        /** The modes a user can pick for a trip or filter the map by. */
        val choosable = listOf(WALK, CYCLE, DRIVE, RIDESHARE, TRANSIT)
        val vehicles = setOf(DRIVE, RIDESHARE, TRANSIT)

        fun fromId(id: Int): TravelMode = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

/**
 * Colours the user picked for travel modes. Held in Compose state, so everything on screen that shows a
 * mode's colour updates as soon as one changes; [version] changes too, for things drawn ahead of time.
 */
object ModeColors {
    internal val custom = mutableStateMapOf<TravelMode, Long>()

    var version by mutableIntStateOf(0)
        private set

    val anyCustom: Boolean get() = custom.isNotEmpty()

    internal fun replace(colors: Map<TravelMode, Long>) {
        custom.clear()
        custom.putAll(colors)
        version++
    }

    /** "1=FF66BB6A,3=FF0000FF": mode ids to ARGB. */
    fun encode(colors: Map<TravelMode, Long>): String =
        colors.entries.joinToString(",") { (m, c) -> "${m.id}=${java.lang.Long.toHexString(c and 0xFFFFFFFFL)}" }

    fun decode(s: String?): Map<TravelMode, Long> = s.orEmpty().split(',').mapNotNull { part ->
        val (id, hex) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        val mode = id.toIntOrNull()?.let { i -> TravelMode.entries.firstOrNull { it.id == i } } ?: return@mapNotNull null
        val color = hex.toLongOrNull(16) ?: return@mapNotNull null
        mode to (color or 0xFF000000L)
    }.toMap()
}
