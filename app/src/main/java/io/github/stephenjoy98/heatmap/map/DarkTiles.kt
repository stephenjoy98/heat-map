package io.github.stephenjoy98.heatmap.map

import org.osmdroid.tileprovider.tilesource.XYTileSource

/** CARTO's dark basemap (OpenStreetMap data): free, no API key, and makes the heat glow stand out. */
val DarkTiles = XYTileSource(
    "CartoDarkMatter",
    0,
    20,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/",
    ),
    "© OpenStreetMap contributors © CARTO",
)
