package io.github.stephenjoy98.heatmap.data

/** How often one satellite helped fix your position: [fixes] recorded dots it was used for. */
class SatelliteUse(val constellation: Int, val svid: Int, val fixes: Long)

/** Satellites used in a period, and how many recorded dots had satellite information at all. */
class SatelliteSummary(val satellites: List<SatelliteUse>, val fixes: Long) {
    /** Satellite count and fixes per system, busiest first. */
    val bySystem: List<Triple<Int, Int, Long>>
        get() = satellites.groupBy { it.constellation }
            .map { (c, list) -> Triple(c, list.size, list.sumOf { it.fixes }) }
            .sortedByDescending { it.third }
}

/**
 * Names for satellite navigation systems and their satellites. Constellation numbers are Android's
 * GnssStatus.CONSTELLATION_* values; svid is the satellite's ID within its system.
 */
object Satellites {
    const val GPS = 1
    const val SBAS = 2
    const val GLONASS = 3
    const val QZSS = 4
    const val BEIDOU = 5
    const val GALILEO = 6
    const val NAVIC = 7

    fun system(constellation: Int): String = when (constellation) {
        GPS -> "GPS"
        SBAS -> "SBAS"
        GLONASS -> "GLONASS"
        QZSS -> "QZSS"
        BEIDOU -> "BeiDou"
        GALILEO -> "Galileo"
        NAVIC -> "NavIC"
        else -> "Other"
    }

    /** Who runs each system, for a line under its name. */
    fun operator(constellation: Int): String = when (constellation) {
        GPS -> "United States"
        SBAS -> "Correction satellites (WAAS and others)"
        GLONASS -> "Russia"
        QZSS -> "Japan"
        BEIDOU -> "China"
        GALILEO -> "European Union"
        NAVIC -> "India"
        else -> "Unknown system"
    }

    /** The usual short name for a satellite: G05 (GPS), R12 (GLONASS), E24 (Galileo), C30 (BeiDou)… */
    fun name(constellation: Int, svid: Int): String = when (constellation) {
        GPS -> "G" + two(svid)
        GLONASS -> if (svid in 93..106) "R FCN ${svid - 100}" else "R" + two(svid)
        GALILEO -> "E" + two(svid)
        BEIDOU -> "C" + two(svid)
        QZSS -> if (svid >= 193) "J" + two(svid - 192) else "J" + two(svid)
        NAVIC -> "I" + two(svid)
        SBAS -> "S$svid"
        else -> "#$svid"
    }

    private fun two(n: Int) = n.toString().padStart(2, '0')

    /** Packs a satellite into one number (system × 1000 + ID). */
    fun key(constellation: Int, svid: Int): Int = constellation * 1000 + svid
}
