package io.github.stephenjoy98.heatmap.data

/**
 * Rough CO₂ per person per kilometre for each travel mode, from the kind of mode it is (so sub-modes and modes you
 * add count too) and a few words in its name. Figures are the UK government's greenhouse gas conversion factors
 * (DESNZ 2024), rounded: average car 170 g/km, taxi or ride-hail 150 g/km, local bus 100 g/km, train 35 g/km,
 * light rail or metro 30 g/km, foot-passenger ferry 20 g/km. A car pool is shared with one other person on
 * average. Walking, cycling and Other count as nothing.
 */
object Carbon {
    const val CAR_KG_PER_KM = 0.17

    fun kgPerKm(mode: TravelMode): Double {
        val name = mode.fullLabel.lowercase()
        fun has(vararg words: String) = words.any { it in name }
        return when (mode.kind) {
            TravelMode.Kind.FOOT, TravelMode.Kind.BIKE, TravelMode.Kind.OTHER -> 0.0
            TravelMode.Kind.CAR -> when {
                has("pool") -> CAR_KG_PER_KM / 2
                has("taxi", "uber", "lyft", "ride") -> 0.15
                else -> CAR_KG_PER_KM
            }
            TravelMode.Kind.PUBLIC -> 0.10
            TravelMode.Kind.TRACK -> when {
                has("sea", "ferry", "boat", "ship", "water") -> 0.02
                has("sky", "metro", "subway", "light", "tram", "line") -> 0.03
                else -> 0.035
            }
        }
    }

    /** CO₂ from [kmByMode], in kg. */
    fun kg(kmByMode: Map<TravelMode, Double>): Double = kmByMode.entries.sumOf { (mode, km) -> km * kgPerKm(mode) }

    /** CO₂ walking and cycling avoided compared with driving the same distance, in kg. */
    fun savedKg(kmByMode: Map<TravelMode, Double>): Double = kmByMode.entries.sumOf { (mode, km) ->
        if (mode.kind == TravelMode.Kind.FOOT || mode.kind == TravelMode.Kind.BIKE) km * CAR_KG_PER_KM else 0.0
    }
}
