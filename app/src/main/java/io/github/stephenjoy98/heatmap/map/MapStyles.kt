package io.github.stephenjoy98.heatmap.map

/**
 * Google Maps styles. The light one is a clean, white, low-contrast look in the spirit of in-car
 * navigation maps; the dark one is a near-black canvas like Strava's heatmap, so the routes glow.
 * Both hide points of interest and transit icons so the heat stays the focus.
 */
object MapStyles {
    val light = """
    [
      {"elementType":"geometry","stylers":[{"color":"#f5f5f5"}]},
      {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
      {"elementType":"labels.text.fill","stylers":[{"color":"#8a8a8a"}]},
      {"elementType":"labels.text.stroke","stylers":[{"color":"#f5f5f5"}]},
      {"featureType":"administrative","elementType":"geometry","stylers":[{"visibility":"off"}]},
      {"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#6b6b6b"}]},
      {"featureType":"poi","stylers":[{"visibility":"off"}]},
      {"featureType":"poi.park","elementType":"geometry","stylers":[{"visibility":"on"},{"color":"#e9eee8"}]},
      {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#ffffff"}]},
      {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#e2e2e2"}]},
      {"featureType":"road.arterial","elementType":"labels","stylers":[{"visibility":"simplified"}]},
      {"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#ffffff"}]},
      {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#d6d6d6"}]},
      {"featureType":"road.local","elementType":"labels","stylers":[{"visibility":"off"}]},
      {"featureType":"transit","stylers":[{"visibility":"off"}]},
      {"featureType":"water","elementType":"geometry","stylers":[{"color":"#dde3ea"}]},
      {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#a3adb8"}]}
    ]
    """.trimIndent()

    val dark = """
    [
      {"elementType":"geometry","stylers":[{"color":"#1b1b1b"}]},
      {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
      {"elementType":"labels.text.fill","stylers":[{"color":"#6e6e6e"}]},
      {"elementType":"labels.text.stroke","stylers":[{"color":"#1b1b1b"}]},
      {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#3a3a3a"}]},
      {"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#9a9a9a"}]},
      {"featureType":"poi","stylers":[{"visibility":"off"}]},
      {"featureType":"poi.park","elementType":"geometry","stylers":[{"visibility":"on"},{"color":"#1f221f"}]},
      {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#2b2b2b"}]},
      {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#212121"}]},
      {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#5c5c5c"}]},
      {"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#363636"}]},
      {"featureType":"road.local","elementType":"labels","stylers":[{"visibility":"off"}]},
      {"featureType":"transit","stylers":[{"visibility":"off"}]},
      {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0e0f10"}]},
      {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#3d3d3d"}]}
    ]
    """.trimIndent()
}
