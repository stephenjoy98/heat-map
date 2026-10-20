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
  and uploads `location-ticker-<version>-apk`; on pushes to main it also publishes a GitHub release `vX.Y.Z` with that
  version's CHANGELOG entry and `location-ticker-X.Y.Z.apk` + `-debug.apk`, refreshing it if the version didn't change)
  is the only compiler. Pushes are the test. Watch the PR's check
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

- Version lives only in `gradle.properties` → `appVersion=MAJOR.MINOR.PATCH` (currently **2.29.0**).
  versionCode = MAJOR*10000 + MINOR*100 + PATCH (minor/patch < 100). Build fails if CHANGELOG.md has no
  `## [x.y.z]` entry for it.
- Rules agreed with the user: MAJOR = breaks existing data/backups/sharing; MINOR = new features (also
  removals that don't break data); PATCH = fixes/tweaks.
- Every user-visible change: bump `appVersion` and add a CHANGELOG entry (Keep a Changelog format, written
  for users; it's bundled into the app and shown as "What's new" after updates and under About →
  Changelog). If the previous version hasn't been built/installed yet, folding small follow-ups into its
  entry is OK; once the user has it, make a new patch version.
- Keep README.md and THIRD_PARTY_NOTICES.md in sync with features/dependencies.
- **Keep the About page's `FEATURES` list (ui/AboutScreen.kt) in sync with every release**: the user asked
  (2026-10-20) for it to always show everything the app currently supports. Add, reword or drop lines as
  features change.

## Architecture (app/src/main/java/io/github/stephenjoy98/heatmap)

- Saved places (2.21.0): `data/SavedPlaces.kt` (`SavedPlace`, `PlaceKind`, `PlaceZones.find` with a 30 m leave
  margin; Prefs "saved_places", `Prefs.atPlace` runtime), `tracking/PlaceFences` (Play geofences, EXIT only,
  needs background location; `PlaceFenceReceiver` in manifest), service `placeSec` override: settled →
  max(base, place interval) + balanced accuracy; moving (activity or >0.7 m/s) → auto: max(base, 20 s), fixed:
  base. UI `PlacesSection` (Settings) with full-screen `PlaceEditor` (pin at map centre via `MapCamera.center`),
  suggestions from `PointStore.frequentSpots`.
- `tracking/`: `LocationTickerService` (foreground service, fused location, records altitude via
  `altitudeOf` — MSL on API 34+), `AutoInterval` (vehicle 1 s, cycle 2 s, walk 3 s, unknown 5 s, still 15 s,
  long still 60 s; stillness judged over the last minute of fixes), `ActivityTracker`, `TripDetector`,
  `BootReceiver`. Manual interval slider is 15 s … 1 day. Fixes worse than `accuracyLimitM` (100 m high
  accuracy / 500 m) are skipped.
- `data/`: `PointStore` (SQLite, **schema v9**: points incl. altitude, trips, places, friend_routes,
  trip_paths, road_questions, deleted_points (soft delete), explored_cells/explored_days/explored_state
  (km-explored cache), battery_log (v8: sample/min while recording via `LocationTickerService.sampleBattery`;
  `batteryByInterval` → `BatterySection` in Settings)), `Prefs` (StateFlows), `Units` (metric/imperial, Compose state), `Changelog` + `SemVer`,
  `TravelMode` — **a class, not an enum** since 2.18.0: built-ins as companion vals (ids 0–5), user-added
  modes from id 100 (`TravelMode.added`, Prefs "custom_modes"/"next_mode_id"; never reuse ids), deleted
  built-ins in "deleted_modes" (Walking/Cycling/Driving/Other can't be deleted); equality by id; `entries`,
  `choosable`, `vehicles` are computed from Compose state; `kind` (FOOT/BIKE/CAR/PUBLIC) drives Valhalla
  costing and vehicle checks. `color` = `ModeColors` override (built-ins, Prefs "mode_colors") or
  `defaultColor`. Deleting a mode runs `PointStore.replaceMode` (trips → Other, needs_review). Backups
  write a `backup/modes` doc; restore merges it first. UI: `TravelModesSection` + `ColorPickerDialog`.
  When checking "all modes shown" use `containsAll(TravelMode.entries)`, not sizes.
  Since 2.19.0: sub-modes via `parentId` (one level; `topLevel`, `ordered`, `children`, `withChildren`,
  `fullLabel`); built-ins renamable (Prefs "mode_labels"); defaults seeded once ("modes_seeded_v1":
  CarPool, RideShare › Bike/Car, PublicTransit › SkyTrain/Bus/SeaBus); Kind.TRACK = no road matching
  (`Valhalla.costing` returns null). Pickers: `ModeChips` / `ModeMenuItems` (ModePicker.kt). Map filter chips
  are top-level and toggle `withChildren`. Deleting a sub-mode moves its trips to the parent.
  `Prefs.currentMode` (set from `RecordingSheet`, opened by tapping the record tile) tags new dots' `mode`;
  cleared when tracking stops; TripDetector follows a majority of tagged dots.
  `ExploredDistance` (new-ground km on a ~60 m grid, per-square credit capped at the diagonal),
  `Route3d` (3D route geometry: continuous mitred ribbon, median + 120 m distance smoothing, 3D-simplified, split at
  U-turns, pieces inset 6 cm so walls never touch (flicker); `densify` + `withoutOverlaps` draw a street once; since 2.28.0
  heights are above the ground (`map/GroundHeights`: Terrarium z13 tiles cached in cacheDir/ground, ≤16 downloads per
  load; fallback `PointStore.altitudeBaseline()`), true scale like the 3D buildings;
  `drape` lays dot heights along a display line; loader `map/Route3dTracks` uses snapped polys / smoothed
  lines like the 2D map; curtain stops 0.4 m under the ribbon to avoid z-fighting). Since 2.24.0 heights come
  from `data/HeightGrid` (~55×36 m patches; per-pass medians, pass = trip or day for stays; patch height =
  median of passes). Patches whose passes split by >15 m become `height_levels` rows (schema v9; decision
  ASK/SAME/DIFFERENT) asked in the Timeline's Questions tab; DIFFERENT keeps per-pass heights, `TrackRules`, `Cells`, `GpxExporter` (writes `<ele>`).
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
  the map is framed only when the period changes, not on every reload; `DayEntry.Group` joins back-to-back
  same-mode trips (long-press expands; `flattened()` for lookups); multi-select (`picked` set,
  `SelectionBar`, `setTripModes`); add a trip (`adding` window → `RetimePanel` with trip=null →
  `PointStore.addTrip`, which splits a trip it lands inside); Fix route (`fixing` window → `RouteFixPanel`,
  vias by tapping the map, `Valhalla.routes`/`routeThrough`, `RouteLinesOverlay`; save = `addTrip` to join,
  then `saveTripPath(createdTripId, STATUS_SNAPPED, poly, poly)` if a road was picked); trip, road and
  level questions live in the Timeline's **Questions** tab since 2.28.0 (`QuestionsPane` in TripQuestions.kt: one queue,
  answer → next, Skip → back of queue; `TimelineTabs.questions` selects the tab, set by the Account page);
  Timeline nav item has the question badge), `MapEditor`,
  `MapControls` (CompassButton, ZoomButtons, LayersButton, ScaleBar), `MapLayersSheet` (Default/Terrain
  pictures + 3D buildings), `GpsAccuracy` (Fog of World-style card, satellites), `ProfileScreen`,
  `ProfileMenu` (`ProfileButton` with badge outside its clip — pass `background` instead of wrapping it in a
  clipping Surface; `AccountScreen` = `Page.ACCOUNT`, a page since 2.29.0 so back from nested pages returns to it), `SettingsScreen` (+ `PermissionsSection`),
  `AboutScreen`, `WhatsNew`, `Tooltips` (`Tip`, `BackButton`), `Theme` (Light/Dark/System + Material
  expressive = Monet dynamic colour + shapes/type/springs; light schemes get white cards on grey page).
- `cloud/`: Firebase optional (only if `app/google-services.json` exists; CI writes it from the
  `GOOGLE_SERVICES_JSON` secret). `Backup` (per-day docs incl. `al` altitude list with NaN for none),
  `SyncWorker` (periodic at Prefs.backupEveryHours 6/12/24/168, UPDATE policy; backup when due, and on every app open via `runNow(appOpen = true)` unless one ran <5 min ago),
  `Friends` (list cached in Prefs "friends_cache" → `Friends.saved()`), `Account`, `StorageUsage` (phone: file
  sizes + StatFs; cloud: estimated from Firestore's document size rules, since the client can't query usage;
  shown in `StorageCard` on the Backup page). Backup docs: `yyyy-MM-dd[_n]` dots (t, la, lo, ac, iv, at, md, al
  arrays, ≤7000 per doc); `trips_yyyy-MM-dd` (since 2.26.0: trips starting that day as maps s, e, m, im, c, r, ps,
  bp, p, q (road questions JSON), id (remote route id); null fields omitted); `deleted_yyyy-MM-dd` (soft-deleted dots); `levels` (height
  answers); `modes`; `settings` (JSON of the user's prefs, whitelist `Prefs.BACKED_UP_KEYS`; add new user-facing
  pref keys there). Restore: `Backup.preview` downloads once and summarises (dialog `RestorePreviewDialog`), then
  `restore(context, preview)` reuses that download; order: settings (`Prefs.importSettings` → re-`init`), modes, levels, then deleted dots, dots
  and `restoreTrips` inside `synchronized(TripDetector)`, then the detector for leftovers. `createTrip`/
  `saveTripPath` mark the trip's day dirty; Prefs "backup_complete_v2" makes the first 2.26 backup re-upload every
  day (bump the key when per-day backup content grows). 3D ribbons and heatmaps are always drawn on the phone. Shared routes (`users/{uid}/routes`) hold mode + polyline + distance, no times or heights. Friend profile page
  `FriendProfileScreen` (`Page.FRIEND`): stats + heatmap of `friend_routes` framed by `PointStore.friendBounds`.
- `ui/Breakdowns.kt`: `ModeBreakdownRows` (top-level modes with children summed, tap to expand; used on Insights
  and Profile), `ListDialog`, `AREA_EXPLANATION`. `PointStore.kmByArea` / `cellsByArea` + `ReviewRepository.byCity`
  give km and areas per city. Settings: `SettingsPage` enum, page-per-section by default (Prefs
  "settings_pages"), "One list" option at the top.
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
  chips hidden behind the filter button, Review named **Insights**, **Google Material icons, never emoji** (since 2.29.0;
  `MaterialIcon(R.drawable.ic_*)` in ui/MaterialIcon.kt; add icons as vector drawables generated from the npm package
  `@material-design-icons/svg` outlined SVGs — material-icons-extended isn't used, it would bloat the unminified APK),
  neutral near-white accent in the default dark theme (no orange; 3D ribbon uses `colorScheme.primary`), text a size up
  from Material defaults with no letter spacing (`baseTypography` in Theme.kt, user compares with Gmail/Claude apps), units setting respected everywhere (`Units`), long-press tooltips on icon buttons.
- When something can't be done exactly (e.g. no system dialog for revoking permissions), do the closest
  sensible thing and say so plainly in the reply.
