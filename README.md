# heat-map

Your most travelled routes on a map.

**Location Ticker** is an Android app that records your position on a fixed tick, anywhere from every
15 seconds to once a day, and draws everything as a Strava-style heatmap on OpenStreetMap. It works out
how you travelled, gives you a Insights page with AI insights, reads your steps from Health Connect, backs
up to your Google account, and lets you compare explored areas with friends.

## Features

### Design
- **Material Design 3** throughout: large collapsing top app bars, segmented buttons, M3 navigation bar,
  cards and sheets. **Light**, **Dark** or **System** theme, plus a separate **Material expressive**
  switch (Android 12+): colours from your wallpaper through Android's Monet engine, rounder shapes,
  emphasised headings and springier motion. Otherwise a neutral dark theme (near-white accent, like Google's own
  dark apps) or an orange light one. Text is set a size up from Material's defaults, like Gmail. Icons are
  Google's Material icons throughout. The heat colours themselves never change.
- **Motion and navigation** follow Android's guidance: predictive back (the page shrinks and follows
  your swipe, revealing the one underneath), menus and popups that grow out of what you tapped, and
  large page titles that collapse into the top bar as you scroll. Back walks back through the pages
  you opened, then returns to the map, and asks before leaving the app from the map.
- Two tabs, **Map** and **Timeline**. Profile, Friends, Backup, Insights, Settings and About open from
  the account button, which shows a badge when trips or roads are waiting for your answer.

### Map
- **Works with no setup**: sharp **vector maps** from [OpenFreeMap](https://openfreemap.org/)
  (OpenStreetMap data, no key, no limits), drawn by MapLibre at your screen's full resolution.
- Two looks: a **dark** map in the near-black greys of Google's dark theme, so routes glow
  like Strava's heatmap, and a clean **light** white map in the spirit of Tesla's in-car map. Choose
  Light, Dark or System; the whole app follows.
- **Heatmap tiles**: every continuous route is drawn into a density buffer so overlapping trips stack
  up. On the dark map roads used once are dark red and your most travelled ones burn orange, yellow and
  white. The light map uses light orange through deep crimson.
- **Travel-mode filter**: the filter button (top left) slides out chips for All, Walking, Cycling,
  Driving, Car share, Transit and Other; a dot on the button shows when the map is filtered.
- Routes or raw-points view, and very large histories automatically fall back to a density grid.
- **Map layers** (layers button on the right, like Google Maps): **Default** or **Terrain** map type
  (hill shading from the keyless [AWS Terrain Tiles](https://registry.opendata.aws/terrain-tiles/)), and **3D buildings** under Map details, which tilts
  the map so buildings stand up and shows your routes in **3D**: every dot records its elevation, and
  routes are drawn as an orange ribbon at their recorded height (above a fixed low point of everything
  recorded, exaggerated 1.5×, smoothed over neighbouring dots) over a translucent curtain.
  Tilting the map with two fingers switches 3D on, and tilting it back flat switches it off.
- A **scale bar** shows while you pan or zoom, in round distances.
- **Units**: metric (km, m) or imperial (miles, feet) in Settings, used for every distance, height, speed
  and the map scale; it defaults to your region (imperial in the US, UK, Liberia and Myanmar).
- **GPS accuracy** (signal-bars button, like Fog of World): live accuracy in metres with a Good / Fair /
  Poor / Too weak badge, satellites in use and in view (by constellation: GPS, Galileo, GLONASS,
  BeiDou…), heading and speed, and the time of the fix. The **?** explains what accuracy
  means; fixes less precise than 100 m (500 m in battery-saver mode) are never recorded.
- **Edit dots** (pencil on the Timeline tab): fix mapping errors. Pick a day, narrow it down with a **start–end
  slider** (the dots in range are highlighted and joined in time order), or tap a single dot and step
  through its neighbours, then delete. Trips touching deleted dots are rebuilt from what's left, and
  backups and shared routes are updated on the next sync. Deleting is a **soft delete**: deleted dots
  move to a Deleted list for that day (with Undo right after), and you can restore them at any time.

### Recording
- Interval slider (15 s … 1 day) plus an exact value in seconds, minutes, hours or days.
- **Automatic interval**: picks how often to record from how you're moving (activity recognition,
  speed between recent fixes, and how long you've stayed put): every 1 s in a vehicle, 2 s cycling,
  3 s on foot, 5 s when unsure, 15 s when still and 1 min after 20 minutes in one place. It reacts straight away
  when activity recognition is allowed; otherwise it adapts from GPS fixes alone.
- Foreground service with a Stop action in its notification; resumes after reboot when allowed.
- **Permissions** in Settings: each permission the app uses, whether it's allowed, and a tap to change it
  (Android's own dialog where there is one, otherwise the app's page in Android settings).

### How you travelled
- Each fix is tagged with Android's **activity recognition** (in vehicle, on bicycle, walking,
  running, still). Finished stretches are split into **trips** using that plus speed.
- **When it's unsure, it asks.** Any vehicle trip could be driving, a car share or transit, and slow
  "cycling" could be traffic, so these show up as questions on the Insights page, with a mini map of the
  trip and a notification. Your answer is remembered: the next trip between the same two places is
  classified automatically.

### Smooth, road-snapped routes
- **Smoothing**: routes that aren't snapped are drawn as curves (a 3-point moving average plus Chaikin
  corner cutting) instead of straight hops between fixes.
- **Snapping to roads**: finished trips are map-matched to OpenStreetMap with the free public
  [Valhalla](https://valhalla1.openstreetmap.de) service run by FOSSGIS, using the network for the trip's
  mode (footpaths, cycle routes, roads or bus routes). Only coordinates are sent, never times.
- **Asking which road**: where the recording strays more than ~40 m from the matched road for a few fixes,
  the Insights page asks "Which road did you take?" with a mini map of the options: the best match,
  alternative roads between the same points (labelled by street name), and the line as recorded.
- Trips that stray almost everywhere (trail walks, trains) keep their smoothed recording. Changing a trip's
  travel mode re-matches it. Both features can be switched off in Settings.

### Timeline tab
- Browse your history by **Day, Week, Month or Year**, like Google Maps' Timeline: ‹ › to step through
  periods, or tap the date to pick one.
- A map of the period's routes, drawn as solid lines in each travel mode's colour, framed to fit.
- **Day**: a vertical timeline of trips (mode, distance, times) and the stays between them, named by
  street and town. Tap a trip or stay to zoom the map to it.
- **Questions tab**: "How did you travel?", "Which road did you take?" and "Different heights here?" wait in
  their own tab on the Timeline, queued one after another, on a map you can pinch-zoom (green start, red
  end). Answer one and the next comes up; Skip sends it to the back. A badge on the Timeline tab counts them.
- **Grouped trips**: back-to-back trips in the same mode show as one entry; long-press it (or tap the arrow)
  to see and edit each part, or change the mode of all parts from its menu.
- **Fix route** (long-press › Fix route on the map): pick a stretch of dots across gaps and trips; it's
  joined into one trip and snapped to roads, or to a road you pick from "Find roads". Tap the map to make
  the road pass through a spot.
- **Select several trips** (long-press › Select) and change their mode together.
- **Add a trip** (+ on a day, or long-press a stay › Add a trip here): pick its first and last dot with a
  slider and its mode; overlapping trips are trimmed or split around it.
- **Long-press a trip** to change how you travelled (walking, cycling, driving, car share, transit) or
  **Change times**: a start–end slider over the day's dots, like Edit dots, picks the trip's first and last
  dot. The trips or stays before and after it grow or shrink to match.
- **Right now**: tap the recording tile on the map to say what kind of trip you're on (new dots are filed
  under it until you stop recording) and switch between automatic and a set interval.
- **Sub-modes**: CarPool, RideShare › Bike and Car, PublicTransit › SkyTrain, Bus and SeaBus come
  suggested; add your own with "Part of". Train and boat modes aren't snapped to roads.
- **Places** in Settings: home, work, school and others, each a circle with its own slower interval while
  you're settled there (1–30 min, Wi-Fi/cell positioning). Moving speeds it to ~20 s until you're out of the
  circle, then the usual pace; Android geofencing also reports leaving. Suggested from where you spend the
  most time; stays at them are named on the Timeline.
- **Settings** as a page per section, or one long list (choose at the top).
- **Battery** in Settings: battery used per hour at each recording interval (last 7 or 30 days, screen off
  or all), measured on battery power while recording.
- **Travel modes** in Settings: pick each mode's colour with a colour picker or a hex code (reset one or all
  to the defaults), **add your own modes** (a name, a colour, and whether it travels on foot, by bike, by car
  or by public transport, which decides how its trips are matched to roads), and **delete** modes you don't
  use. Walking, Cycling, Driving and Other always stay; deleted Car share and Transit can be added back.
  Trips of a deleted mode become Other and are asked about again in Insights. Added modes are included in
  backups.
- **Week / Month**: a row per day; **Year**: a row per month, each with distance, trips and a bar split by
  travel mode. Tap a row to drill in.

### Profile
- The account button in the top-right corner of every tab shows your Google profile picture and opens
  the **Account** page: **Profile**, **Friends**, **Backup**, **Insights**, **Settings**, **About**, and sign in or out.
  Going back from any of them returns to the Account page.
- **Profile** is a "travel passport": name, joining date and friend ID; your **home base** (the city
  where you spend the most days); **dots captured** with kilometres, trips and areas; **time captured**
  as a total (e.g. 1 day 15 hours, each dot counting for its interval); your most visited city;
  **locations explored** (**km explored**, which counts only ground you're covering for the first time
  on a ~60 m grid, unlike km travelled; and countries, cities and continents, each tappable for the full list, with JoyPixels flags and globes);
  and an estimated **carbon footprint**.
  Filter by all time or by year.

### Insights page
- Week / Month / Year / All time: distance (total and per mode), **new areas explored** (~220 m
  squares you'd never visited before), **cities and countries** visited (reverse-geocoded on the
  phone and cached), longest travel day, and **steps from Health Connect**.
- **Satellites**: which navigation satellites fixed your dots in the period, per system (GPS, Galileo,
  GLONASS, BeiDou, QZSS, NavIC) with how many satellites and how often each was used, and the full list
  (G05, E24, R12…) with counts. Recorded from version 2.31.0 on; dots from Wi-Fi or mobile networks use none.
- **Insights on demand**: tap *Get insights* or ask a question. On first launch (and any time in
  Settings) you choose who writes them:
  - **On-device AI (Gemini Nano)**: runs on the phone through ML Kit's Prompt API / AICore, with
    nothing leaving the device. Only on recent flagships (e.g. Pixel 9 series and newer, Galaxy S24
    series and newer). The app checks this and offers the one-time model download.
  - **Cloud AI (Gemini via Firebase AI Logic)**: any phone. Sends only a short summary of the period
    (distances, counts, city names, steps), never raw points or routes.
  - **Statistics only**: no AI. Highlights and simple Q&A computed from your numbers.

  If the chosen engine isn't available when you ask, the app falls back to statistics and says why.

### Friends and backup (needs Firebase)
- **Sign in with Google** (Credential Manager + Firebase Auth).
- **Storage** on the Backup page: space used and free on the phone (exact) and in the cloud (estimated from
  what's uploaded, against the free Firebase plan's 1 GiB).
- **Backup** of the full history to your private Firestore area, automatically (whenever you open the app, plus every 6 or 12 hours, daily or weekly) and on demand, with **Restore**
  that merges into the current phone's history.
- **Friends** via an 8-character **ID**, a **QR code**, or a shared invite link
  (`locationticker://friend/ABCD2345`). Scan with the built-in Google code scanner.
- **Compare** with a friend: both heatmaps on one map (yours orange, theirs blue), plus **Shared
  areas**, **New to me** (where they've been and you haven't) and **New to them**, and a table of
  distance per mode, areas explored, shared and unexplored areas.
- Friends see your **full routes and travel modes, never dates or times**. Sharing can be switched off.

## Install

Download the latest APK from the repository's **[Releases](https://github.com/stephenjoy98/heat-map/releases)**
page: each version has its own release (tag `vX.Y.Z`) with its changelog and `location-ticker-X.Y.Z.apk`.
Install it on Android 8.0+; you'll need to allow installing from unknown sources.

Once installed, the app keeps itself up to date: **Settings › Updates** checks the releases (on opening and
every 6 hours), downloads a new version (on Wi-Fi only by default) and installs it once you've left the app.
Android asks you to confirm the first update (and to allow Location Ticker to install apps); on Android 12
and later, later updates install without asking. Automatic updates can be turned off there.

Every push to `main` builds the APKs in GitHub Actions and publishes (or refreshes) the release for the
version in `gradle.properties`. Builds of other branches are under **Actions → Android build** as the
artifact `location-ticker-<version>-apk`.

Without any setup the app works fully on OpenStreetMap: recording, heatmap, travel modes, Insights,
steps, on-device AI and statistics. Sign-in, backup, friends and cloud AI need Firebase (below).

## Setup

### 1. Firebase (sign-in, backup, friends, cloud AI)
1. Create a project at [console.firebase.google.com](https://console.firebase.google.com/) and add an
   Android app with package **`io.github.stephenjoy98.heatmap`**.
2. Add the signing key's SHA-1 (and SHA-256) fingerprints:
   ```
   SHA-1:   C0:4A:B5:37:63:46:61:4D:C9:2B:79:37:0B:0A:7F:E2:90:52:38:A2
   SHA-256: 64:B4:38:7A:75:67:D1:77:3A:AC:EE:7E:2C:AA:9F:E2:E2:CB:8D:9F:7B:04:3C:9D:7F:30:CA:27:D6:C5:1E:72
   ```
   Both APKs are signed with the development key in `app/signing/dev.keystore`, so these stay the
   same on every build. (It's a dev key committed for convenience; use your own for a Play Store
   release.)
3. **Authentication** → Sign-in method → enable **Google**.
4. **Firestore Database** → create a database, then paste [`firestore.rules`](firestore.rules) into
   the Rules tab and publish.
5. **AI Logic** → get started with the **Gemini Developer API** (for the cloud AI option).
6. Download `google-services.json` and add its full contents as a GitHub Actions secret named
   **`GOOGLE_SERVICES_JSON`**. For local builds, put the file at `app/google-services.json`
   (it's git-ignored).

The next push (or *Run workflow* on the Actions tab) builds an APK with everything turned on.

### 2. On the phone
- Allow **location** (and *Allow all the time* in Settings if you want recording to resume after a
  reboot), **notifications**, and **physical activity** (much better travel-mode detection).
- Connect **Health Connect** from the Insights page or Settings to see steps.
- For long intervals, turn off battery optimization for the app (button in Settings).

## Build locally

Requires JDK 17 and the Android SDK (API 36).

```sh
./gradlew assembleDebug
```

## How it works

| Piece | File |
| --- | --- |
| Recording service, activity tagging | `tracking/LocationTickerService.kt`, `tracking/ActivityTracker.kt` |
| Trip splitting and travel-mode decisions | `tracking/TripDetector.kt` |
| Road snapping, doubtful-stretch questions | `roads/RoadSnapper.kt`, `roads/Valhalla.kt` |
| Route smoothing | `map/Smoothing.kt` |
| Storage (SQLite: points, trips, places cache, friends' routes) | `data/PointStore.kt` |
| Heatmap rendering and map tiles | `map/HeatRenderer.kt`, `map/TileProviders.kt`, `map/TrackSources.kt` |
| The map (MapLibre vector maps with heat tiles, 3D routes and a location marker) | `ui/AppMap.kt`, `map/VectorMapSupport.kt` |
| Map styles (dark / light) | `map/MapStyles.kt`, `map/VectorMapSupport.kt` |
| Insights: stats, steps, AI write-ups (Nano / cloud / statistics) | `review/` |
| Sign-in, backup, friends, scheduled sync | `cloud/` |
| UI (Jetpack Compose) | `ui/` |

The renderer strokes each route at 12% alpha onto an offscreen 512 px tile. Overlaps combine as
`1 - (1 - a)^n`, so the alpha channel measures how often you've been somewhere, saturating after about
20 trips. That value is then looked up in the theme's colour ramp.

## Versioning and changelog

Versions follow [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`.

- **MAJOR**: changes that break existing data, backups or sharing with older versions of the app.
- **MINOR**: new features that keep working with existing data.
- **PATCH**: bug fixes only.

The version is set in one place, `appVersion` in [`gradle.properties`](gradle.properties). The build
derives Android's `versionCode` from it (`2.11.3` → `21103`, so it always goes up) and uses it as the
`versionName`. [`CHANGELOG.md`](CHANGELOG.md) follows [Keep a Changelog](https://keepachangelog.com/)
and is bundled into the app. After an update, the app shows **What's new** with every release since the
version the user last saw, and the full changelog is under **About → Changelog**.

To release:

1. Note changes under `## [Unreleased]` in `CHANGELOG.md` as you go, written for users.
2. Pick the next version by the rules above, and rename that heading to `## [x.y.z] - YYYY-MM-DD`.
3. Set `appVersion=x.y.z` in `gradle.properties`. The build fails if the changelog has no entry for it.
4. Optionally tag the commit `vx.y.z`.

## Licence

Location Ticker is free software under the [GNU General Public License v3.0 or later](LICENSE)
(GPL-3.0-or-later): you can use, study, share and change it, and anything you distribute that's based on
it must be shared under the same licence, with its source code. [NOTICE](NOTICE) has the copyright notice
and an additional permission (GPL section 7) for linking Google's proprietary libraries (Play services,
Maps, Firebase, ML Kit), which the app needs. Third-party libraries, icons and map data stay under
their own licences; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) or **About** in the app.

Made with ❤️ by [@stephenjoy98](https://github.com/stephenjoy98) with the help of Claude by Anthropic,
in Canada.
