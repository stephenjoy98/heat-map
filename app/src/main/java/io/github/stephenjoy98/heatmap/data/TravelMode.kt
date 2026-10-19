package io.github.stephenjoy98.heatmap.data

/** How a stretch of the history was travelled. Ids are stored in the database; never reuse one. */
enum class TravelMode(val id: Int, val label: String, val color: Long) {
    UNKNOWN(0, "Other", 0xFF9E9E9E),
    WALK(1, "Walking", 0xFF66BB6A),
    CYCLE(2, "Cycling", 0xFF29B6F6),
    DRIVE(3, "Driving", 0xFFFF7A1A),
    RIDESHARE(4, "Car share", 0xFFAB47BC),
    TRANSIT(5, "Transit", 0xFFFFCA28);

    companion object {
        /** The modes a user can pick for a trip or filter the map by. */
        val choosable = listOf(WALK, CYCLE, DRIVE, RIDESHARE, TRANSIT)
        val vehicles = setOf(DRIVE, RIDESHARE, TRANSIT)

        fun fromId(id: Int): TravelMode = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}
