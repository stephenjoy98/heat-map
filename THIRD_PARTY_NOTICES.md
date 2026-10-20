# Third-party notices

Location Ticker's own source code is licensed under the [GNU General Public License v3.0 or later](LICENSE),
with an additional permission for linking Google's proprietary libraries (see [NOTICE](NOTICE)). It builds on the
libraries, services and data below, each under its own terms. Libraries are fetched by Gradle at build time
and map data and elevation are downloaded by the app at runtime. The only artwork copied into this repository
is a set of Google's Material icons (`app/src/main/res/drawable/ic_*.xml`, Apache License 2.0).

The app shows the same list under **Account → About**.

## Libraries

| Library | Licence |
| --- | --- |
| Jetpack Compose, Material 3, AndroidX (Core, Activity, Lifecycle, WorkManager, Credentials) | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Health Connect client | Apache License 2.0 |
| Kotlin standard library, kotlinx.coroutines | Apache License 2.0 |
| [Firebase Android SDK](https://github.com/firebase/firebase-android-sdk) (Auth, Firestore, AI Logic) | Apache License 2.0 |
| [MapLibre Native](https://github.com/maplibre/maplibre-native) | [BSD 2-Clause](https://github.com/maplibre/maplibre-native/blob/main/LICENSE.md) |
| [Coil](https://github.com/coil-kt/coil) | Apache License 2.0 |
| [Material icons](https://github.com/google/material-design-icons) (vector drawables in `res/drawable/ic_*.xml`) | Apache License 2.0 |
| [ZXing](https://github.com/zxing/zxing) | Apache License 2.0 |
| Gradle wrapper | Apache License 2.0 |
| Google Play services (Location, Code Scanner), Google ID, ML Kit GenAI Prompt API | Proprietary: [Android SDK License](https://developer.android.com/studio/terms) and [Google APIs Terms](https://developers.google.com/terms). Free to use, but **not** open source. Combining them with this GPL program is allowed by the section 7 permission in [NOTICE](NOTICE) |

Apache 2.0 libraries ship their own `NOTICE` files inside their artifacts, and Android's build keeps
them in the APK.

## Services and data

| Source | Terms and required attribution |
| --- | --- |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | © OpenStreetMap contributors, [ODbL 1.0](https://opendatacommons.org/licenses/odbl/). Credit shown on the map. |
| [OpenFreeMap](https://openfreemap.org/) tiles, [OpenMapTiles](https://openmaptiles.org/) schema | "OpenFreeMap © OpenMapTiles". Credit shown on the map. |
| [Terrain Tiles](https://registry.opendata.aws/terrain-tiles/) (AWS Open Data) | Elevation from Mapzen, USGS, NASA SRTM, EU-DEM and others; see the [full attribution](https://github.com/tilezen/joerd/blob/master/docs/attribution.md). |
| [JoyPixels](https://www.joypixels.com/) emoji artwork (flags and globes in the profile's place lists) | [JoyPixels Free License](https://www.joypixels.com/licenses), which requires attribution (shown under About). Downloaded at runtime; **not** covered by this project's GPL licence. |
| [Valhalla](https://github.com/valhalla/valhalla) map matching on the FOSSGIS server | Valhalla is MIT-licensed; the public server has a [fair-use policy](https://valhalla.openstreetmap.de/). Only coordinates are sent. |

## Trademarks

Claude and Anthropic are trademarks of Anthropic, PBC. Google, Android, Firebase and
Health Connect are trademarks of Google LLC. Strava and Tesla are mentioned only for comparison. This
project isn't affiliated with or endorsed by any of these companies.
