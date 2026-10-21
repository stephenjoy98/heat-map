package io.github.stephenjoy98.heatmap.review

import io.github.stephenjoy98.heatmap.data.SatelliteSummary

import android.content.Context
import io.github.stephenjoy98.heatmap.data.CoarseArea
import io.github.stephenjoy98.heatmap.data.Place
import io.github.stephenjoy98.heatmap.data.PointStore
import io.github.stephenjoy98.heatmap.data.TravelMode
import io.github.stephenjoy98.heatmap.data.Trip
import java.time.LocalDate
import java.time.ZoneId

enum class ReviewPeriod(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All time") }

class CityVisit(val city: String, val country: String?, val points: Int, val km: Double = 0.0)

class ReviewData(
    val period: ReviewPeriod,
    val kmByMode: Map<TravelMode, Double>,
    val trips: Int,
    val points: Long,
    val areasVisited: Long,
    val newAreas: Long,
    val cities: List<CityVisit>,
    val countries: List<String>,
    val steps: StepsSummary?,
    val busiestDay: Pair<LocalDate, Double>?,
    val questions: List<Trip>,
    /** New map squares explored in the period, by city, most first. */
    val newAreasByCity: List<Pair<String, Int>> = emptyList(),
    /** Satellites behind the dots recorded in the period (noted since 2.31.0). */
    val satellites: SatelliteSummary = SatelliteSummary(emptyList(), 0),
) {
    val totalKm: Double get() = kmByMode.values.sum()
}

/** Gathers the numbers behind the Insights page. Call off the main thread. */
object ReviewRepository {
    private const val MAX_GEOCODES_PER_LOAD = 40

    fun range(period: ReviewPeriod): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = when (period) {
            ReviewPeriod.WEEK -> today.minusDays(6)
            ReviewPeriod.MONTH -> today.minusDays(29)
            ReviewPeriod.YEAR -> today.minusDays(364)
            ReviewPeriod.ALL -> return 0L to Long.MAX_VALUE
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    suspend fun load(context: Context, period: ReviewPeriod): ReviewData {
        val store = PointStore.get(context)
        val (from, to) = range(period)
        val areaPlaces = Places.resolve(context, store, store.coarseAreas(from, to), MAX_GEOCODES_PER_LOAD)
        val cities = cities(areaPlaces, store.kmByArea(from, to))
        val stepsFrom = if (period == ReviewPeriod.ALL) LocalDate.now().minusDays(364)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() else from
        return ReviewData(
            period = period,
            kmByMode = store.distanceByMode(from, to).mapValues { it.value / 1000.0 },
            trips = store.tripCount(from, to),
            points = store.pointCount(from, to),
            areasVisited = store.cellsVisited(from, to),
            newAreas = store.newCells(from, to),
            cities = cities,
            countries = cities.mapNotNull { it.country }.distinct(),
            steps = runCatching { HealthSteps.summary(context, stepsFrom, to) }.getOrNull(),
            busiestDay = store.distanceByDay(from, to).maxByOrNull { it.value }
                ?.let { LocalDate.parse(it.key) to it.value / 1000.0 },
            questions = store.tripsNeedingReview(),
            newAreasByCity = byCity(areaPlaces, store.cellsByArea(from, to, onlyNew = true)),
            satellites = store.satelliteUse(from, to),
        )
    }

    /** Cities visited, busiest first, with the distance travelled in each (~5 km areas, geocoded once and cached). */
    private fun cities(areas: List<Pair<CoarseArea, Place>>, kmByArea: Map<String, Double>): List<CityVisit> {
        val byCity = LinkedHashMap<String, CityVisit>()
        for ((area, place) in areas) {
            val city = place.city ?: continue
            val prev = byCity[city]
            byCity[city] = CityVisit(
                city,
                place.country ?: prev?.country,
                (prev?.points ?: 0) + area.points,
                (prev?.km ?: 0.0) + (kmByArea[area.key] ?: 0.0),
            )
        }
        return byCity.values.sortedByDescending { it.points }
    }

    /** Sums per-area counts into cities (areas without a known city go under "Elsewhere"), largest first. */
    fun byCity(areas: List<Pair<CoarseArea, Place>>, perArea: Map<String, Int>): List<Pair<String, Int>> {
        val cityOf = areas.associate { (area, place) -> area.key to (place.city ?: "Elsewhere") }
        val out = HashMap<String, Int>()
        perArea.forEach { (key, n) -> val city = cityOf[key] ?: "Elsewhere"; out[city] = (out[city] ?: 0) + n }
        return out.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
