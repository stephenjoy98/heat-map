package io.github.stephenjoy98.heatmap.review

import android.content.Context
import android.location.Geocoder
import io.github.stephenjoy98.heatmap.data.CoarseArea
import io.github.stephenjoy98.heatmap.data.Place
import io.github.stephenjoy98.heatmap.data.PointStore
import java.util.Locale

/** Reverse-geocodes ~5 km areas into city, state and country, caching each answer on the phone. */
object Places {
    /**
     * Resolves [areas] in order, looking up at most [maxLookups] new ones per call (the system geocoder
     * is slow and may be rate limited). Entries cached before states were stored are refreshed too.
     */
    fun resolve(context: Context, store: PointStore, areas: List<CoarseArea>, maxLookups: Int = 40): List<Pair<CoarseArea, Place>> {
        val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null
        var lookups = 0
        val out = ArrayList<Pair<CoarseArea, Place>>(areas.size)
        for (area in areas) {
            val cached = store.place(area.key)
            val stale = cached != null && cached.city != null && cached.countryCode == null
            val place = if ((cached == null || stale) && geocoder != null && lookups < maxLookups) {
                lookups++
                lookup(geocoder, area.lat, area.lon)?.also { store.putPlace(area.key, it) } ?: cached
            } else {
                cached
            }
            if (place != null) out += area to place
        }
        return out
    }

    @Suppress("DEPRECATION") // The callback variant needs API 33; callers run on a background thread.
    private fun lookup(geocoder: Geocoder, lat: Double, lon: Double): Place? = runCatching {
        val a = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return@runCatching Place(null, null)
        Place(a.locality ?: a.subAdminArea ?: a.adminArea, a.countryName, a.adminArea, a.countryCode)
    }.getOrNull() // Offline or rate limited: leave uncached and try again next time.

    fun continentOf(countryCode: String?): String? {
        val code = countryCode?.uppercase() ?: return null
        return continents.entries.firstOrNull { code in it.value }?.key
    }

    // ISO 3166-1 alpha-2 codes by continent.
    private val continents: Map<String, Set<String>> = mapOf(
        "Africa" to "DZ AO BJ BW BF BI CV CM CF TD KM CG CD CI DJ EG GQ ER SZ ET GA GM GH GN GW KE LS LR LY MG MW ML MR MU YT MA MZ NA NE NG RE RW SH ST SN SC SL SO ZA SS SD TZ TG TN UG EH ZM ZW",
        "Asia" to "AF AM AZ BH BD BT BN KH CN CY GE HK IN ID IR IQ IL JP JO KZ KW KG LA LB MO MY MV MN MM NP KP OM PK PS PH QA SA SG KR LK SY TW TJ TH TL TR TM AE UZ VN YE",
        "Europe" to "AX AL AD AT BY BE BA BG HR CZ DK EE FO FI FR DE GI GR GG HU IS IE IM IT JE XK LV LI LT LU MT MD MC ME NL MK NO PL PT RO RU SM RS SK SI ES SJ SE CH UA GB VA",
        "North America" to "AI AG AW BS BB BZ BM BQ VG CA KY CR CU CW DM DO SV GL GD GP GT HT HN JM MQ MX MS NI PA PR BL KN LC MF PM VC SX TT TC US VI",
        "South America" to "AR BO BR CL CO EC FK GF GY PY PE SR UY VE",
        "Oceania" to "AS AU CK FJ PF GU KI MH FM NR NC NZ NU NF MP PW PG PN WS SB TK TO TV UM VU WF",
        "Antarctica" to "AQ BV GS HM TF",
    ).mapValues { it.value.split(" ").toSet() }
}
