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
  and uploads `location-ticker-<version>-apk`; on main it publishes a release with `location-ticker-X.Y.Z.apk` +
  `-debug.apk`. **Since 2.33.0 every push is a beta** (pre-release `vX.Y.Z-beta.<run>`, newest 3 kept, notes = changes
  since the last stable); a **stable** release (`vX.Y.Z`, latest, notes from `tools/release_notes.py` = every entry since
  the previous stable) happens only via workflow_dispatch with `stable: true` (MCP `actions_run_trigger`) or a commit
  message containing `[stable]`. The user asked (2026-10-21) for most changes to go out as betas and stable releases
  only once things work: don't put `[stable]` on routine pushes; release stable when the user says so or a batch is verified.
  BuildConfig.CHANNEL/BUILD_NUMBER come from the env)
  is the only compiler. Pushes are the test. A second job (`emulator`, API 34 google_apis via
  reactivecircus/android-emulator-runner) runs `tools/emulator-smoke.sh`: installs the debug APK, presets
  shared_prefs/ticker.xml with run-as (onboarded, last_seen_version, auto_update off), feeds `adb emu geo fix` points,
  taps through screens by text/content-desc from `uiautomator dump`, saves PNGs + run.mp4 + logcat/crash + steps.txt;
  fails only on a crash. Results are force-pushed to the **`ci-screenshots`** branch: `git fetch origin ci-screenshots`
  and look at the PNGs/frames before telling the user a change works. Keep tap labels in the script in sync with UI text. Watch the PR's check
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

- Version lives only in `gradle.properties` → `appVersion=MAJOR.MINOR.PATCH` (currently **2.46.3**).
  versionCode = MAJOR*10000 + MINOR*100 + PATCH (minor/patch < 100). Build fails if CHANGELOG.md has no
  `## [x.y.z]` entry for it.
- Rules agreed with the user: MAJOR = breaks existing data/backups/sharing; MINOR = new features (also
  removals that don't break data); PATCH = fixes/tweaks.
- Every user-visible change: bump `appVersion` and add a CHANGELOG entry (Keep a Changelog format, written
  for users; it's bundled into the app and shown as "What's new" after updates and under About →
  Changelog). If the previous version hasn't been built/installed yet, folding small follow-ups into its
  entry is OK; once the user has it, make a new patch version.
- Keep README.md and THIRD_PARTY_NOTICES.md in sync with features/dependencies.
- **Bump `Attention.NEW_IN` (ui/Attention.kt) with each release** for the sections that gained or changed something
  (keys: profile, friends, backup, insights, timeline, about, `settings/<page>`); their "New" marker shows until opened.
  Red dots (`AttentionDot`) mark notification subjects: update waiting (Prefs `update_seen`), changelog unread
  (Prefs `changelog_read`); baseline Prefs `attention_base` = version seen when the feature first ran.
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
- Battery profiles (2.32.0): `data/PowerProfile` (USER/PERFORMANCE/DEFAULT/SAVER: per-activity seconds + highAccuracy),
  `Prefs.powerProfile`, `applyPowerProfile` (saves the user's interval/auto/accuracy as JSON "user_profile" when leaving
  USER, restores it on return; built-ins force auto + their accuracy); manual `setIntervalSec`/`setAutoInterval`/
  `setHighAccuracy` switch back to USER; `AutoInterval.choose(..., profile)`. UI `PowerProfilesSection` on the Battery page.
- `tracking/`: `LocationTickerService` (foreground service, fused location, records altitude via
  `altitudeOf` — MSL on API 34+), `AutoInterval` (vehicle 1 s, cycle 2 s, walk 3 s, unknown 5 s, still 15 s,
  long still 60 s; stillness judged over the last minute of fixes), `ActivityTracker`, `TripDetector`,
  `BootReceiver`. Manual interval slider is 15 s … 1 day. Fixes worse than `accuracyLimitM` (100 m high
  accuracy / 500 m) are skipped, and (2.33.2) GPS jumps (>150 m at >55 m/s within 60 s of the last saved fix, max 5 in a
  row) too; (2.41.1) `TripDetector.removeSpikes` soft-deletes out-and-back blips (`PointStore.findSpikes`: 1–3 dots >300 m
  from both neighbours that are close to each other, within 30 min, coarse >20 m or >70 m/s) over the last day, and the whole
  history once (Prefs `spikes_cleaned_v1`). `TrackRules.isBreak`: jumps <1 km never break; gaps ≤10 min at 3–45 m/s (tunnels) don't break.
- `data/`: `PointStore` (SQLite, **schema v10**: v10 = satellite_use (day, constellation, svid, fixes) + satellite_fixes
  (day, fixes), filled by `LocationTickerService` from a GnssStatus callback (satellites usedInFix within 15 s of a saved
  fix, `logSatellites`), read by `satelliteUse(from, to)` → Insights `SatellitesCard`; names in `data/Satellites.kt`;
  backed up as `sats_yyyy-MM-dd` (c, s, n lists + f fixes; restore keeps the max per satellite/day); points incl. altitude, trips, places, friend_routes,
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
  `Route3d` (3D route geometry: continuous mitred ribbon, median + 200 m distance smoothing, sloping hops cut into
  pieces ≤0.15 m apart in height (2.30.3, cap 18k pieces) so slopes don't show as stairs, heights above a
  fixed `PointStore.altitudeBaseline()` (5th percentile, cached 10 min), exaggerated 1.5×. 2.28.0 tried heights above
  the ground (Terrarium DEM) at true scale plus overlap removal; the user found it "barely looks 3D" and it was
  reverted in 2.29.1 — don't redo that without asking;
  `drape` lays dot heights along a display line; loader `map/Route3dTracks` uses snapped polys / smoothed
  lines like the 2D map; curtain stops 0.4 m under the ribbon to avoid z-fighting). Since 2.24.0 heights come
  from `data/HeightGrid` (~55×36 m patches; per-pass medians, pass = trip or day for stays; patch height =
  median of passes). Patches whose passes split by >15 m become `height_levels` rows (schema v9; decision
  ASK/SAME/DIFFERENT) asked in the Timeline's Questions tab; DIFFERENT keeps per-pass heights, `TrackRules`, `Cells`, `GpxExporter` (writes `<ele>`).
- `map/`: `VectorMapSupport.kt` (`VectorMaps`: OpenFreeMap Positron style, dark recolour, terrain hillshade
  from AWS Terrarium DEM declared at tileSize 128 for sharpness, 3D buildings fill-extrusion, 3D route
  layers, `applyColours(style, dark, vivid)` (2.35.0: blends muted→vivid `Palette`s per theme; light is left
  untouched at 0; Prefs `mapColourDark/Light`; roads only with Prefs `map_colour_roads`, else muted; 2.46.2 darker dark-map roads at the user's request: muted minor #1F1F1F / major #2B2B2B, vivid #30363E / #5C6570), `setTint` (2.34.0 world-polygon tint for Prefs `mapBrightnessDark/Light`; the slider was removed in 2.40.0 at the
  user's request (sheet too cluttered) and AppMap now clears the tint; Map colours is a collapsible row in `MapLayersSheet`); `TileLayerRenderer`/`TileOverlayView`: heat tiles drawn over MapLibre with a 4-point matrix,
  blue location dot that glides linearly between positions over the time between them (2.37.1; MapScreen feeds
  the newer of the last recorded dot and the live fix ≤50 m; `heading` arrow from the live fix's bearing when >1 m/s)),
  heat rendering (`HeatRenderer.PASS_ALPHA` 0.085 ≈ 30 passes to saturate; `HeatRamps.own/friend(palette, dark)`,
  Prefs `heat_palette` FIRE/ICE chosen in the layers sheet; friends get the other palette), smoothing, tile sources.
- `ui/`: `MainActivity` (AppScaffold: tabs **Map** and **Timeline**; `PageHost` page stack for Profile,
  Friends, Backup, Settings, About, Changelog, **Insights** (file still `ReviewScreen.kt`, enum `Page.REVIEW`),
  Compare; back = pop page → Map tab → exit confirm), `Navigation.kt` (PageStack, predictive back,
  `GrowFromSource` popups, `trackBounds`), `AppMap.kt` (MapLibre only; `MapCamera` with commands/actions,
  bearing, cameraTick, metersPerPixel, moving, projector; per-activity `VectorMapCache` keeps each tab's map
  alive via `cacheKey`; tilt >8° by hand turns 3D on, <2° turns it off; 3D route loader), `MapScreen`
  (filter button + chips, stats, profile button, scale bar + compass under the top row, accuracy button
  bottom-left, location/zoom/layers bottom-right, record panel; long-press location = follow me (`following`:
  `MapCamera.glideTo` per dot update with `easeCamera` linear, same duration as the dot's glide; 2.45.0: panning
  (`MapCamera.panTick`) no longer stops it, it pauses `FOLLOW_PAUSE_MS` 5 s then glides back; tap = come back now;
  long-press again stops it (user's request); 2.46.0: following with the app STARTED sets runtime `Prefs.followBoost` → service records every `FOLLOW_SEC` 1 s with high accuracy (overrides auto/place), restored on stop/background/dispose via `LocationTickerService.start`; 2.44.0: `TileOverlayView.navMarker` draws an Uber-style red marker squashed
  by the tilt; 2.45.4: "trace cursor" from the user's mockup (after red/3D puck and Uber
  black-and-white tries in 2.45.0–2.45.3): dark disc, white arrow, ring + glow (4 translucent circles, no blur/gradient)
  in `navGlow` = the heat palette's colour (FIRE red-pink / ICE blue, set in AppMap; 2.45.6 at the user's request matches the route line: dark map FIRE rgb(128,10,44), ICE rgb(18,55,165); r 16 dp; glow = 16 circles 0.8 dp apart, alpha 16; arrow light grey rgb(200,200,205)); (2.45.3: no outline; tilted >2° = thin disc with a
  dark edge from stacked ovals; `navHeadingUp` (follow + 3D) draws the arrow straight up; follow starts with
  `MapCamera.snapTo` (350 ms, zoom 17 + bearing); bearing glides keep the target tilt so they don't stop the 3D tilt); the glide also sets bearing = last heading, heading-up (since 2.46.1 flat too, at the user's request; `navHeadingUp` = following; stopping calls `northUp()`))), `TimelineScreen` (also hosts **Edit dots**
  with range slider, soft delete/restore, move/add dots (2.45.0: `DotEditState.placing` MOVE/ADD, next map tap →
  `PointStore.movePoint` / `addPointAfter` (halfway in time to the next dot, same trip, accuracy 5 m) then `refreshTrip`;
  undo = move back / `removeAddedPoint` + `restore(snapshot)`; for ferry routes, which are TRACK so never road-snapped), undo snackbar over the map, layers button; long-press a trip row
  for a mode menu (`setTripMode`) and **Change times** (`RetimePanel` → `PointStore.retimeTrip`, which moves
  dots between the trip, adjacent trips (<5 min apart) and stays (trip_id -1), then `refreshTrip`s them);
  the map is framed only when the period changes, not on every reload; `DayEntry.Group` joins back-to-back
  same-mode trips (long-press expands; `flattened()` for lookups); multi-select (`picked` set,
  `SelectionBar`, `setTripModes`); add a trip (`adding` window → `RetimePanel` with trip=null →
  `PointStore.addTrip`, which splits a trip it lands inside); Fix route (`fixing` window → `RouteFixPanel`,
  vias by tapping the map, `Valhalla.routes`/`routeThrough`, `RouteLinesOverlay`; save = `addTrip` to join,
  then `saveTripPath(createdTripId, STATUS_SNAPPED, poly, poly)` if a road was picked; on part of a trip addTrip splits it,
  which is how "Snap to a road (all or part)" isolates a stretch); Split (2.42.0: `splitting` → `SplitPanel`, `retime.range.last` =
  last dot of the first part → `PointStore.splitTrip(tripId, atTime)`, undoable); Routes/Dots segmented toggle on the map
  (Dots = Edit dots mode); trip, road and
  level questions live in the Timeline's **Questions** tab since 2.28.0 (`QuestionsPane` in TripQuestions.kt; 2.46.3: trip question = pick chip (starts on the best guess) then Save; Timeline undo snackbar at TopCenter + statusBarsPadding so it never covers Skip/Save; one queue,
  answer → next, Skip → back of queue; `TimelineTabs.questions` selects the tab, set by the Account page);
  Timeline nav item has the question badge), `MapEditor`,
  `MapControls` (CompassButton (2.39.1: dial with N/E/S/W + NE/SE/SW/NW ticks fixed, needle turns by -bearing;
  the user tried a fixed needle + turning dial and asked for this way round): north up, then toggles 3D via Prefs.buildings3d; `Altimeter` (live fix, `LocationTickerService.altitudeOf`) above
  `Speedometer` (live fix speed; shrinks to a 10 dp dot below 1 km/h|mph, in a fixed 48 dp slot) on the left of MapScreen; ZoomButtons, LayersButton, ScaleBar), `MapLayersSheet` (Default/Terrain
  pictures + 3D buildings, My location, 3D routes (Prefs `routes_3d`, gates the route-3d loader in AppMap)), `GpsAccuracy` (Fog of World-style card, satellites), `ProfileScreen`,
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
  arrays, ≤4000 per doc: Firestore's 40k index entries per doc, one per list item); `trips_yyyy-MM-dd` (since 2.26.0: trips starting that day as maps s, e, m, im, c, r, ps,
  bp, p, q (road questions JSON), id (remote route id); null fields omitted); `deleted_yyyy-MM-dd` (soft-deleted dots); `sats_yyyy-MM-dd` (satellite counts); `levels` (height
  answers); `modes`; `settings` (JSON of the user's prefs, whitelist `Prefs.BACKED_UP_KEYS`; add new user-facing
  pref keys there). Restore: `Backup.preview` downloads once and summarises (dialog `RestorePreviewDialog`), then
  `restore(context, preview)` reuses that download; order: settings (`Prefs.importSettings` → re-`init`), modes, levels, then deleted dots, dots
  and `restoreTrips` inside `synchronized(TripDetector)`, then the detector for leftovers. `createTrip`/
  `saveTripPath` mark the trip's day dirty; Prefs "backup_complete_v2" makes the first 2.26 backup re-upload every
  day (bump the key when per-day backup content grows). 3D ribbons and heatmaps are always drawn on the phone. Shared routes (`users/{uid}/routes`) hold mode + polyline + distance, no times or heights. Friend profile page
  `FriendProfileScreen` (`Page.FRIEND`): stats + heatmap of `friend_routes` framed by `PointStore.friendBounds`.
- `ui/Breakdowns.kt`: `ModeBreakdownRows` (top-level modes with children summed, tap to expand; used on Insights
  and Profile), `ListDialog`, `AREA_EXPLANATION`. `PointStore.kmByArea` / `cellsByArea` + `ReviewRepository.byCity`
  give km and areas per city. Settings: `SettingsPage` enum with a `group` (Recording / Map and trips / App /
  System), page-per-section by default (Prefs "settings_pages"), the "One list" choice and Units live on the
  General page; sections switch with an AnimatedContent shared-axis slide. `PageHost` keeps the page under the top one
  composed so popping never flashes the tabs.
- `update/` (2.30.0): `Updater` (GitHub releases/latest → `Release`; newer = higher SemVer, or same version with a
  different `<!-- build: sha -->` marker in the notes vs `BuildConfig.BUILD_COMMIT` from CI's GITHUB_SHA; downloads to
  cacheDir/updates (re-fetches release details first; 404/size change → retry 4× 30 s apart, because CI's --clobber
  re-upload briefly removes the asset), installs via PackageInstaller session with USER_ACTION_NOT_REQUIRED on 31+; `inForeground` set by
  MainActivity so auto installs wait until onStop), `InstallReceiver` (pending user action → start confirm intent or
  notify), `UpdateWorker` (6 h periodic + on app open; Prefs `auto_update` (off by default since 2.37.0, forced off once via
  `auto_update_off_v1`: the user doesn't want auto downloads; the sheet offers Later / Download and install now), `update_wifi_only`), UI `UpdatesSection`
  (Settings › Updates; STABLE_FEATURES ignores patch bumps and rebuilds unless `check(manual = true)`), channel by
  `UpdateFrequency` BETA (newest of /releases incl. pre-releases) / STABLE / STABLE_FEATURES;
  `UpdateSheet` (ui/UpdateSheet.kt, 2.35.0: `SlideUpSheet` ModalBottomSheet with notes, Update now / Later, only when
  on the Map tab with no page open, once per session via `UpdatePrompt.offeredThisSession`, reset after 30 min away);
  `WhatsNewDialog` uses the same `SlideUpSheet` and also waits for the map screen. BootReceiver notifies "updated to X" on MY_PACKAGE_REPLACED. **Every notification's tap
  intent comes from `MainActivity.openIntent(context, page)`** (own request code + action per page: updates, changelog,
  questions, none) — PendingIntents that differ only in extras are the same one, which once made every notification
  open the changelog.
- Undo (2.36.0): `PointStore.snapshot(start, end)` / `restore(snap)` (points' trip/mode, trips, trip_paths, road_questions
  around an edit, widened to whole trips ±1 h); Timeline's `undoable(label, from, to) { change }` shows an Undo snackbar
  (`undoSnackbar`); road/level answers undo via `RoadSnapper.answer(q, -1)` / `answerLevel(area, ASK)`; `questionsReset`
  brings answered questions back. Map full screen: `MapChrome.fullscreen` (MapScreen.kt) also hides the nav bar;
  Prefs `show_location_dot`. Snackbars use `SwipeSnackbarHost` (ui/SwipeSnackbar.kt, swipe to dismiss).
- RoadSnapper (2.41.1): `strays()` rejects matched shapes >250 m from the recorded line anywhere or >1.5× its length
  (+300 m) → STATUS_RECORDED; `recheckSnapped` ran once over existing paths with stricter limits (Prefs `snaps_rechecked_v1`).
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
  chips hidden behind the filter button, Review named **Insights**, **Google Material icons, not emoji** (since 2.29.0; the one exception the user asked for: JoyPixels
  flags/globes in the profile's country/city/continent lists and the 🇨🇦 on About, `JoyEmoji` in ui/JoyEmoji.kt;
  `MaterialIcon(R.drawable.ic_*)` in ui/MaterialIcon.kt; add icons as vector drawables generated from the npm package
  `@material-design-icons/svg` outlined SVGs — material-icons-extended isn't used, it would bloat the unminified APK),
  neutral near-white accent in the default dark theme (no orange in the UI) but **map elements are never
  Material-themed**: 3D ribbon `#FF7A1A`, preview lines/rings/edit dots use `MapAccent` (Theme.kt), graphs use `ChartColors` (Theme.kt, bright like the mode bars), text a size up
  from Material defaults with no letter spacing (`baseTypography` in Theme.kt, user compares with Gmail/Claude apps), units setting respected everywhere (`Units`), long-press tooltips on icon buttons.
- **Every AlertDialog is wide**: `properties = DialogProperties(usePlatformDefaultWidth = false)` +
  `modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)` (the user asked for all pop-ups wider, 2026-10-22);
  bigger choices use `SlideUpSheet` instead.
- When something can't be done exactly (e.g. no system dialog for revoking permissions), do the closest
  sensible thing and say so plainly in the reply.
