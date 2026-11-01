package io.github.stephenjoy98.heatmap.ui

import androidx.annotation.DrawableRes
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.PlaceKind
import io.github.stephenjoy98.heatmap.data.SavedPlace
import io.github.stephenjoy98.heatmap.data.TrackRules

/** The icons a saved place can have (Material outlined), by the key saved with the place. */
object PlaceIcons {
    val all: List<Pair<String, Int>> = listOf(
        "home" to R.drawable.ic_home,
        "work" to R.drawable.ic_work,
        "school" to R.drawable.ic_school,
        "gym" to R.drawable.ic_fitness_center,
        "shop" to R.drawable.ic_local_grocery_store,
        "restaurant" to R.drawable.ic_restaurant,
        "cafe" to R.drawable.ic_local_cafe,
        "park" to R.drawable.ic_park,
        "hospital" to R.drawable.ic_local_hospital,
        "church" to R.drawable.ic_church,
        "family" to R.drawable.ic_family_restroom,
        "heart" to R.drawable.ic_favorite,
        "star" to R.drawable.ic_star,
        "flag" to R.drawable.ic_flag,
        "city" to R.drawable.ic_location_city,
        "building" to R.drawable.ic_apartment,
        "pets" to R.drawable.ic_pets,
        "sports" to R.drawable.ic_sports_soccer,
        "library" to R.drawable.ic_local_library,
        "pin" to R.drawable.ic_place,
    )
    private val byKey = all.toMap()

    /** The key a kind of place starts with. */
    fun defaultKey(kind: PlaceKind): String = when (kind) {
        PlaceKind.HOME -> "home"
        PlaceKind.WORK -> "work"
        PlaceKind.SCHOOL -> "school"
        PlaceKind.OTHER -> "pin"
    }

    fun key(place: SavedPlace): String = place.icon?.takeIf { it in byKey } ?: defaultKey(place.kind)

    @DrawableRes
    fun res(place: SavedPlace): Int = byKey.getValue(key(place))

    @DrawableRes
    fun res(key: String): Int = byKey[key] ?: R.drawable.ic_place

    /** The saved place a stay was at: by position, or else by its name. */
    fun placeFor(places: List<SavedPlace>, lat: Double, lon: Double, name: String?): SavedPlace? =
        places.filter { TrackRules.distanceMeters(it.lat, it.lon, lat, lon) <= it.radiusM + 50 }
            .minByOrNull { TrackRules.distanceMeters(it.lat, it.lon, lat, lon) }
            ?: places.firstOrNull { name != null && it.name == name }
}
