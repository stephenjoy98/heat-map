# heat-map

Your most travelled routes on a map.

**Location Ticker** is an Android app that records your position on a fixed tick, anywhere from every
15 seconds to once a day, and draws everything as a Strava-style heatmap on Google Maps. It works out
how you travelled, gives you a Review tab with AI insights, reads your steps from Health Connect, backs
up to your Google account, and lets you compare explored areas with friends.

## Features

### Map
- **Google Maps** with two custom styles: a **dark** near-black canvas like Strava's heatmap, and a
  clean **light** white style in the spirit of in-car navigation maps. Choose Dark, Light or Match
  system in Settings; the whole app follows the map theme.
- **Heatmap tiles**: every continuous route is drawn into a density buffer so overlapping trips stack
  up. On the dark map roads used once are dark red and your most travelled ones burn orange, yellow and
  white. The light map uses light orange through deep crimson.
- **Travel-mode filter**: chips for Walking, Cycling, Driving, Car share, Transit and Other.
- Routes or raw-points view, and very large histories automatically fall back to a density grid.

### Recording
- Interval slider (15 s … 1 day) plus an exact value in seconds, minutes, hours or days.
- Foreground service with a Stop action in its notification; resumes after reboot when allowed.

### How you travelled
- Each fix is tagged with Android's **activity recognition** (in vehicle, on bicycle, walking,
  running, still). Finished stretches are split into **trips** using that plus speed.
- **When it's unsure, it asks.** Any vehicle trip could be driving, a car share or transit, and slow
  "cycling" could be traffic, so these show up as questions on the Review tab, with a mini map of the
  trip and a notification. Your answer is remembered: the next trip between the same two places is
  classified automatically.

### Review tab
- Week / Month / Year / All time: distance (total and per mode), **new areas explored** (~220 m
  squares you'd never visited before), **cities and countries** visited (reverse-geocoded on the
  phone and cached), longest travel day, and **steps from Health Connect**.
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
- **Backup** of the full history to your private Firestore area, daily and on demand, with **Restore**
  that merges into the current phone's history.
- **Friends** via an 8-character **ID**, a **QR code**, or a shared invite link
  (`locationticker://friend/ABCD2345`). Scan with the built-in Google code scanner.
- **Compare** with a friend: both heatmaps on one map (yours orange, theirs blue), plus **Shared
  areas**, **New to me** (where they've been and you haven't) and **New to them**, and a table of
  distance per mode, areas explored, shared and unexplored areas.
- Friends see your **full routes and travel modes, never dates or times**. Sharing can be switched off.

## Install

Every push builds the APKs in GitHub Actions (**Actions → Android build → artifact
`location-ticker-apk`**). Install `app-release.apk` on Android 8.0+. You'll need to allow installing
from unknown sources.

Without the setup below the app still works: recording, heatmap (on a blank map), travel modes,
Review, steps, on-device AI and statistics. Map tiles need the Maps key; sign-in, backup, friends and
cloud AI need Firebase.

## Setup

### 1. Google Maps API key
1. In [Google Cloud console](https://console.cloud.google.com/), create a project (or reuse your
   Firebase one), enable **Maps SDK for Android**, and create an API key.
2. Restrict it to Android apps with package `io.github.stephenjoy98.heatmap` and the SHA-1 below.
3. Add it as a GitHub Actions secret named **`MAPS_API_KEY`** (repo → Settings → Secrets and
   variables → Actions). For local builds, put `MAPS_API_KEY=...` in `local.properties`.

### 2. Firebase (sign-in, backup, friends, cloud AI)
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

### 3. On the phone
- Allow **location** (and *Allow all the time* in Settings if you want recording to resume after a
  reboot), **notifications**, and **physical activity** (much better travel-mode detection).
- Connect **Health Connect** from the Review tab or Settings to see steps.
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
| Storage (SQLite: points, trips, places cache, friends' routes) | `data/PointStore.kt` |
| Heatmap rendering and Google Maps tiles | `map/HeatRenderer.kt`, `map/TileProviders.kt`, `map/TrackSources.kt` |
| Map styles (dark / light) | `map/MapStyles.kt` |
| Review stats, steps, insights (Nano / cloud / statistics) | `review/` |
| Sign-in, backup, friends, daily sync | `cloud/` |
| UI (Jetpack Compose) | `ui/` |

The renderer strokes each route at 12% alpha onto an offscreen 512 px tile. Overlaps combine as
`1 - (1 - a)^n`, so the alpha channel measures how often you've been somewhere, saturating after about
20 trips. That value is then looked up in the theme's colour ramp.
