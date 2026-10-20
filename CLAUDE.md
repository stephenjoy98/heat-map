# CLAUDE.md — Location Ticker (heat-map)

Working notes for Claude. Read this first; it captures decisions and context from earlier sessions so work
can continue after the conversation history is compacted. README.md describes features for users;
CHANGELOG.md lists every release.

## The project

**Location Ticker**: an Android app that records the phone's location at a set or automatic interval and
draws it as a Strava-style heatmap on OpenStreetMap, with travel modes, trips, a Timeline, a profile with
travel stats, Insights (AI write-ups), backup and friends.

- Owner: **@stephenjoy98** (GitHub), based in Canada. Licence: **GPL-3.0-or-later** (LICENSE + NOTICE with a
  section 7 exception for linking Google Play services, Firebase, ML Kit and Google Identity).
- Repo: `stephenjoy98/heat-map`. **`main` is the default branch and where work goes now**: the user asked
  (2026-10-19) for everything to be on `main` and for future work to use it. PR #1 (from
  `claude/location-ticker-android-wk7rgy`, everything up to 2.16.1) was merged into `main`. If a session is
  assigned a different work branch, branch from the latest `main` and open a PR into `main`. Commit messages end with the Co-Authored-By / Claude-Session trailers given in
  the session's system reminder. Don't put model names in commits or code.
- Package: `io.github.stephenjoy98.heatmap`. Single `app` module.
- Toolchain: Kotlin 2.2.21, AGP 8.13.0, Gradle 8.14.3, compileSdk 36, targetSdk 35, minSdk 26, Compose BOM
  2025.09.01, Material 3. Kotlin 2.2 needs `kotlin { compilerOptions { jvmTarget } }` (not `kotlinOptions`).
- Main libraries: MapLibre android-sdk 11.13.5 (the only map engine), play-services-location 21.3.0
  (fused location + activity recognition), WorkManager 2.10.3, Health Connect 1.1.0, Firebase BoM 34.4.0
  (auth, firestore, firebase-ai), Credential Manager + googleid, ML Kit genai-prompt (Gemini Nano), Coil,
  ZXing + Play code scanner.
- **Google Maps was removed in 2.15.0** at the user's request (OSM only). `LatLng` in UI code is
  `org.maplibre.android.geometry.LatLng`. Don't reintroduce Google Maps.

## How to work in this environment

- **No Android SDK locally.** CI (`.github/workflows/android.yml`, builds assembleDebug + assembleRelease
  and uploads `location-ticker-<version>-apk`) is the only compiler. Pushes are the test. Watch the PR's check
  events; use the GitHub MCP tools (`actions_list`, `actions_get`, `get_job_logs`) — unauthenticated
  api.github.com calls get rate-limited.
- Network: Maven Central and npm are reachable; **dl.google.com / maven.google.com, gnu.org, OSM tiles,
  jsdelivr, Valhalla are blocked**. So androidx/Compose/Play/Firebase APIs can't be inspected locally.
- To check APIs: download jars from Maven Central into the scratchpad and `javap` them. Already fetched in
  earlier sessions (scratchpad may be gone): MapLibre `classes.jar` (from the AAR), maplibre geojson jar,
  maps-compose (no longer used).
- Pure-Kotlin logic can be unit-tested locally: download `kotlin-compiler-embeddable` 2.2.21 + deps
  (kotlin-stdlib, kotlin-script-runtime, kotlin-reflect, kotlin-daemon-embeddable, trove4j, annotations,
  kotlinx-coroutines-core-jvm) and run `java -cp "$K/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler`.
  Used for `Changelog`, `ExploredDistance`, `Route3d`. Stub Android classes if needed.
- Before pushing: bracket-balance check of changed .kt files (strip strings first, then comments), grep for
  leftover references, re-read the diff. Python + Pillow + numpy are available (used for image assets).
- ffmpeg is available for reading user screen recordings (extract a contact sheet of frames).

## Versioning and releases (SemVer)

- Version lives only in `gradle.properties` → `appVersion=MAJOR.MINOR.PATCH` (currently **2.17.0**).
  versionCode = MAJOR*10000 + MINOR*100 + PATCH (minor/patch < 100). Build fails if CHANGELOG.md has no
  `## [x.y.z]` entry for it.
- Rules agreed with the user: MAJOR = breaks existing data/backups/sharing; MINOR = new features (also
  removals that don't break data); PATCH = fixes/tweaks.
- Every user-visible change: bump `appVersion` and add a CHANGELOG entry (Keep a Changelog format, written
  for users; it's bundled into the app and shown as "What's new" after updates and under About →
  Changelog). If the previous version hasn't been built/installed yet, folding small follow-ups into its
  entry is OK; once the user has it, make a new patch version.
- Keep README.md and THIRD_PARTY_NOTICES.md in sync with features/dependencies.

## Architecture (app/src/main/java/io/github/stephenjoy98/heatmap)

- `tracking/`: `LocationTickerService` (foreground service, fused location, records altitude via
  `altitudeOf` — MSL on API 34+), `AutoInterval` (vehicle 1 s, cycle 2 s, walk 3 s, unknown 5 s, still 15 s,
  long still 60 s; stillness judged over the last minute of fixes), `ActivityTracker`, `TripDetector`,
  `BootReceiver`. Manual interval slider is 15 s … 1 day. Fixes worse than `accuracyLimitM` (100 m high
  accuracy / 500 m) are skipped.
- `data/`: `PointStore` (SQLite, **schema v7**: points incl. altitude, trips, places, friend_routes,
  trip_paths, road_questions, deleted_points (soft delete), explored_cells/explored_days/explored_state
  (km-explored cache)), `Prefs` (StateFlows), `Units` (metric/imperial, Compose state), `Changelog` + `SemVer`,
  `TravelMode` (`defaultColor`; `color` reads `ModeColors`, Compose-state overrides saved as Prefs
  "mode_colors"; Settings → Travel mode colours via `ModeColorsSection` + `ColorPickerDialog`),
  `ExploredDistance` (new-ground km on a ~60 m grid, per-square credit capped at the diagonal),
  `Route3d` (3D route geometry), `TrackRules`, `Cells`, `GpxExporter` (writes `<ele>`).
- `map/`: `VectorMapSupport.kt` (`VectorMaps`: OpenFreeMap Positron style, dark recolour, terrain hillshade
  from AWS Terrarium DEM declared at tileSize 128 for sharpness, 3D buildings fill-extrusion, 3D route
  layers; `TileLayerRenderer`/`TileOverlayView`: heat tiles drawn over MapLibre with a 4-point matrix,
  blue location dot), heat rendering, smoothing, tile sources.
- `ui/`: `MainActivity` (AppScaffold: tabs **Map** and **Timeline**; `PageHost` page stack for Profile,
  Friends, Backup, Settings, About, Changelog, **Insights** (file still `ReviewScreen.kt`, enum `Page.REVIEW`),
  Compare; back = pop page → Map tab → exit confirm), `Navigation.kt` (PageStack, predictive back,
  `GrowFromSource` popups, `trackBounds`), `AppMap.kt` (MapLibre only; `MapCamera` with commands/actions,
  bearing, cameraTick, metersPerPixel, moving, projector; per-activity `VectorMapCache` keeps each tab's map
  alive via `cacheKey`; tilt >8° by hand turns 3D on, <2° turns it off; 3D route loader), `MapScreen`
  (filter button + chips, stats, profile button, scale bar + compass under the top row, accuracy button
  bottom-left, location/zoom/layers bottom-right, record panel), `TimelineScreen` (also hosts **Edit dots**
  with range slider, soft delete/restore, undo snackbar over the map, layers button; long-press a trip row
  for a mode menu (`setTripMode`) and **Change times** (`RetimePanel` → `PointStore.retimeTrip`, which moves
  dots between the trip, adjacent trips (<5 min apart) and stays (trip_id -1), then `refreshTrip`s them);
  the map is framed only when the period changes, not on every reload), `MapEditor`,
  `MapControls` (CompassButton, ZoomButtons, LayersButton, ScaleBar), `MapLayersSheet` (Default/Terrain
  pictures + 3D buildings), `GpsAccuracy` (Fog of World-style card, satellites), `ProfileScreen`,
  `ProfileMenu` (account menu; badge for pending questions), `SettingsScreen` (+ `PermissionsSection`),
  `AboutScreen`, `WhatsNew`, `Tooltips` (`Tip`, `BackButton`), `Theme` (Light/Dark/System + Material
  expressive = Monet dynamic colour + shapes/type/springs; light schemes get white cards on grey page).
- `cloud/`: Firebase optional (only if `app/google-services.json` exists; CI writes it from the
  `GOOGLE_SERVICES_JSON` secret). `Backup` (per-day docs incl. `al` altitude list with NaN for none),
  `SyncWorker` (periodic at Prefs.backupEveryHours 6/12/24/168, UPDATE policy; backup when due, and on every app open via `runNow(appOpen = true)` unless one ran <5 min ago),
  `Friends`, `Account`.
- `review/` (Insights data, Places, Health Connect steps), `profile/`, `timeline/`, `roads/` (Valhalla map
  matching + "which road?" questions).
- Assets: `res/drawable-nodpi/layer_{default,terrain}[_dark].webp` generated by
  `tools/make_layer_previews.py` (run `python3 -I tools/make_layer_previews.py app/src/main/res/drawable-nodpi`).
- Signing: dev keystore `app/signing/dev.keystore` (pkcs12, alias dev, password locationticker), used for
  debug and release; SHA-1 C0:4A:B5:37:63:46:61:4D:C9:2B:79:37:0B:0A:7F:E2:90:52:38:A2.

## Gotchas learned the hard way

- `TileSet.encoding` is a Kotlin property (`encoding = "terrarium"`), not `setEncoding(...)`.
- `AnimatedVisibility` inside a `Box` that sits inside a `Column` resolves to the ColumnScope overload and
  fails to compile; keep it a direct child of the Column/Row or outside such scopes.
- `Tip` (tooltip wrapper) must put layout modifiers (align/padding) on an outer `Box`; otherwise Box
  alignment is lost (this sent the compass to the left once).
- Map views (SurfaceView) can't be faded: no Crossfade between tabs with maps.
- Material3 Expressive APIs weren't verifiable offline; the "Material expressive" toggle is built from
  stable APIs.
- Early `return@Column` in composable lambdas: avoid; use if/else.
- Firestore lists can't contain null (use NaN sentinels).

## User preferences and style

- The user tests on Pixel 9 Pro XL, S22 Ultra, Pixel 7, sends screenshots/videos, and iterates quickly on
  UI polish. Follow Google/Material guidance (Material 3, predictive back, source-origin popups, collapsing
  large titles). Prefers OSM, low friction, no API keys.
- Keep: blue location marker, compass always visible (under the profile button, below open chips), filter
  chips hidden behind the filter button, Review named **Insights**, JoyPixels emoji everywhere (with
  attribution), units setting respected everywhere (`Units`), long-press tooltips on icon buttons.
- When something can't be done exactly (e.g. no system dialog for revoking permissions), do the closest
  sensible thing and say so plainly in the reply.
