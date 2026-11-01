package io.github.stephenjoy98.heatmap.ui

import androidx.annotation.DrawableRes
import io.github.stephenjoy98.heatmap.R
import io.github.stephenjoy98.heatmap.data.PlaceKind
import io.github.stephenjoy98.heatmap.data.SavedPlace
import io.github.stephenjoy98.heatmap.data.TrackRules

/** The icons a saved place can have (Material filled, so they all look alike), by the key saved with the place. */
object PlaceIcons {
    val all: List<Pair<String, Int>> = listOf(
        "home" to R.drawable.ic_place_home,
        "work" to R.drawable.ic_place_work,
        "school" to R.drawable.ic_place_school,
        "gym" to R.drawable.ic_place_gym,
        "shop" to R.drawable.ic_place_shop,
        "restaurant" to R.drawable.ic_place_restaurant,
        "cafe" to R.drawable.ic_place_cafe,
        "park" to R.drawable.ic_place_park,
        "hospital" to R.drawable.ic_place_hospital,
        "church" to R.drawable.ic_place_church,
        "family" to R.drawable.ic_place_family,
        "heart" to R.drawable.ic_place_heart,
        "star" to R.drawable.ic_place_star,
        "flag" to R.drawable.ic_place_flag,
        "city" to R.drawable.ic_place_city,
        "building" to R.drawable.ic_place_building,
        "pets" to R.drawable.ic_place_pets,
        "sports" to R.drawable.ic_place_sports,
        "library" to R.drawable.ic_place_library,
        "pin" to R.drawable.ic_place_pin,
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
    fun res(key: String): Int = byKey[key] ?: R.drawable.ic_place_pin

    /** The saved place a stay was at: by position, or else by its name. */
    fun placeFor(places: List<SavedPlace>, lat: Double, lon: Double, name: String?): SavedPlace? =
        places.filter { TrackRules.distanceMeters(it.lat, it.lon, lat, lon) <= it.radiusM + 50 }
            .minByOrNull { TrackRules.distanceMeters(it.lat, it.lon, lat, lon) }
            ?: places.firstOrNull { name != null && it.name == name }
}
