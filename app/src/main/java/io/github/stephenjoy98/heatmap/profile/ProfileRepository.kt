package io.github.stephenjoy98.heatmap.profile

import android.content.Context
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.data.Trip
import io.github.stephenjoy98.heatmap.review.Places
import java.time.LocalDate
import java.time.ZoneId

/** An achievement, earned when [value] reaches [target]. */
class Stamp(val name: String, val description: String, val icon: StampIcon, val value: Double, val target: Double) {
    val progress: Float get() = (value / target).toFloat().coerceIn(0f, 1f)
    val earned: Boolean get() = value >= target
}

enum class StampIcon { DOTS, DISTANCE, WALK, CYCLE, CITY, GLOBE, CONTINENT, AREA, CALENDAR }

class ProfileStats(
    val year: Int?,
    val years: List<Int>,
    val joined: Long?,
    val dots: Long,
    val hours: Int,
    val days: Int,
    val months: Int,
    val yearsCaptured: Int,
    val kmByMode: Map<TravelMode, Double>,
    val trips: Int,
    val areas: Long,
    val cities: Int,
    val states: Int,
    val countries: List<String>,
    val continents: List<String>,
    val homeCity: String?,
    val homeCountry: String?,
    val topCity: String?,
    val topCityDays: Int,
    val longestTrip: Trip?,
    val stamps: List<Stamp>,
) {
    val totalKm: Double get() = kmByMode.values.sum()

    /** Rough CO₂ from vehicle trips: ~170 g/km by car (driving or car share), ~50 g/km by bus or train. */
    val co2Kg: Double
        get() = ((kmByMode[TravelMode.DRIVE] ?: 0.0) + (kmByMode[TravelMode.RIDESHARE] ?: 0.0)) * 0.17 +
            (kmByMode[TravelMode.TRANSIT] ?: 0.0) * 0.05

    /** CO₂ walking and cycling avoided compared with driving the same distance. */
    val savedKg: Double
        get() = ((kmByMode[TravelMode.WALK] ?: 0.0) + (kmByMode[TravelMode.CYCLE] ?: 0.0)) * 0.17
}

/** Gathers the profile's statistics, for all time or one calendar year. Call off the main thread. */
object ProfileRepository {
    fun load(context: Context, year: Int?, accountCreated: Long?): ProfileStats {
        val store = PointStore.get(context)
        val zone = ZoneId.systemDefault()
        val (from, to) = if (year == null) 0L to Long.MAX_VALUE else {
            LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() to
                LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val period = summarize(context, store, from, to)
        val allTime = if (year == null) period else summarize(context, store, 0L, Long.MAX_VALUE)
        val coverage = store.coverage(from, to)
        val allCoverage = if (year == null) coverage else store.coverage(0L, Long.MAX_VALUE)
        val firstPoint = store.firstPointTime()

        return ProfileStats(
            year = year,
            years = store.yearsWithData(),
            joined = listOfNotNull(firstPoint, accountCreated).minOrNull(),
            dots = store.pointCount(from, to),
            hours = coverage[0],
            days = coverage[1],
            months = coverage[2],
            yearsCaptured = coverage[3],
            kmByMode = period.kmByMode,
            trips = store.tripCount(from, to),
            areas = store.cellsVisited(from, to),
            cities = period.cities.size,
            states = period.states.size,
            countries = period.countries,
            continents = period.continents,
            homeCity = allTime.topCity,
            homeCountry = allTime.topCountry,
            topCity = period.topCity,
            topCityDays = period.topCityDays,
            longestTrip = store.longestTrip(from, to),
            stamps = stamps(store, allTime, allCoverage[1]),
        )
    }

    private class Summary(
        val kmByMode: Map<TravelMode, Double>,
        val cities: Set<String>,
        val states: Set<String>,
        val countries: List<String>,
        val continents: List<String>,
        val topCity: String?,
        val topCountry: String?,
        val topCityDays: Int,
    )

    private fun summarize(context: Context, store: PointStore, from: Long, to: Long): Summary {
        val places = Places.resolve(context, store, store.coarseAreas(from, to, 500))
        val cityDays = HashMap<String, Int>()
        val cityCountry = HashMap<String, String?>()
        val states = HashSet<String>()
        val countries = LinkedHashMap<String, String>() // code -> name
        for ((area, place) in places) {
            place.city?.let {
                // Days in a city: the most days seen in any one of its areas (areas overlap the same days).
                cityDays[it] = maxOf(cityDays[it] ?: 0, area.days)
                cityCountry[it] = place.country
            }
            if (place.state != null) states += "${place.countryCode}/${place.state}"
            val code = place.countryCode
            if (code != null) countries.getOrPut(code) { place.country ?: code }
        }
        val top = cityDays.maxByOrNull { it.value }
        return Summary(
            kmByMode = store.distanceByMode(from, to).mapValues { it.value / 1000.0 },
            cities = cityDays.keys,
            states = states,
            countries = countries.values.toList(),
            continents = countries.keys.mapNotNull(Places::continentOf).distinct(),
            topCity = top?.key,
            topCountry = top?.key?.let { cityCountry[it] },
            topCityDays = top?.value ?: 0,
        )
    }

    private fun stamps(store: PointStore, all: Summary, daysCaptured: Int): List<Stamp> {
        val dots = store.pointCount(0L, Long.MAX_VALUE).toDouble()
        val km = all.kmByMode.values.sum()
        val areas = store.cellsVisited(0L, Long.MAX_VALUE).toDouble()
        return listOf(
            Stamp("First thousand", "Capture 1,000 dots", StampIcon.DOTS, dots, 1_000.0),
            Stamp("Century", "Travel 100 km", StampIcon.DISTANCE, km, 100.0),
            Stamp("Every day", "Capture 30 different days", StampIcon.CALENDAR, daysCaptured.toDouble(), 30.0),
            Stamp("Walker", "Walk 100 km", StampIcon.WALK, all.kmByMode[TravelMode.WALK] ?: 0.0, 100.0),
            Stamp("Cyclist", "Cycle 500 km", StampIcon.CYCLE, all.kmByMode[TravelMode.CYCLE] ?: 0.0, 500.0),
            Stamp("City hopper", "Visit 10 cities", StampIcon.CITY, all.cities.size.toDouble(), 10.0),
            Stamp("Explorer", "Explore 1,000 map squares", StampIcon.AREA, areas, 1_000.0),
            Stamp("Thousand km", "Travel 1,000 km", StampIcon.DISTANCE, km, 1_000.0),
            Stamp("Globetrotter", "Visit 5 countries", StampIcon.GLOBE, all.countries.size.toDouble(), 5.0),
            Stamp("Continental", "Visit 3 continents", StampIcon.CONTINENT, all.continents.size.toDouble(), 3.0),
            Stamp("Dot collector", "Capture 100,000 dots", StampIcon.DOTS, dots, 100_000.0),
            Stamp("Year round", "Capture 365 different days", StampIcon.CALENDAR, daysCaptured.toDouble(), 365.0),
        ).sortedWith(compareByDescending<Stamp> { it.earned }.thenByDescending { it.progress })
    }
}
