# heat-map

Your most travelled routes on a map.

**Location Ticker** is an Android app that records your position on a fixed tick, anywhere from
every 15 seconds to once a day, and draws everything it has recorded as a Strava-style heatmap: roads
you've used once glow dark red, and your most travelled routes burn orange, yellow, and then white.

## Features

- **Configurable interval**: a slider with presets (15 s, 30 s, 1 min … 12 h, 1 day) plus an exact
  value in seconds, minutes, hours or days. Limited to between 15 seconds and 1 day.
- **Background recording**: a foreground service with a persistent notification (with a Stop
  button) uses the fused location provider. Changing the interval or accuracy applies right away.
- **Heatmap**: every continuous route is drawn into a density buffer, so overlapping trips stack up,
  and the result is coloured through a heat ramp on top of a dark map (CARTO / OpenStreetMap, no API
  key needed).
  - *Routes* mode joins fixes that are at most three ticks apart (capped at 15 minutes) into lines.
    Slower ticks, like hourly ones, appear as dots.
  - *Points* mode shows a density of the raw fixes.
  - Very large histories (over 400k points in view) fall back to a density grid automatically.
- **Stats**: total points and points today.
- **Export**: all points as a GPX file, split into a track segment per route.
- **Resume after reboot** (needs "Allow all the time" location access) and a shortcut to turn off
  battery optimization, so long intervals aren't paused by Doze.
- Everything stays on the device, in a local SQLite database.

## Install

Every push builds the APKs in GitHub Actions (**Actions → Android build → artifact
`location-ticker-apk`**). Download `app-release.apk`, copy it to your phone and open it. You'll need
to allow installing from unknown sources. Android 8.0 (API 26) or newer is required.

## Build locally

Requires JDK 17 and the Android SDK (API 35).

```sh
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

## How it works

| Piece | File |
| --- | --- |
| Recording service (fused location, one fix per tick) | `tracking/LocationTickerService.kt` |
| Storage (SQLite, bounding-box and grid queries) | `data/PointStore.kt` |
| When two fixes count as one route | `data/TrackRules.kt` |
| Heatmap rendering (density buffer + colour ramp) | `map/HeatRenderer.kt` |
| Map overlay and re-rendering on pan/zoom | `map/HeatmapOverlay.kt`, `map/HeatmapController.kt` |
| UI (Jetpack Compose) | `ui/MainActivity.kt`, `ui/SettingsSheet.kt` |

The renderer strokes each route with a low alpha (12%) onto an offscreen bitmap. Overlaps combine as
`1 - (1 - a)^n`, so the alpha channel becomes a measure of how often you've been there, saturating
after about 20 trips. That value is then looked up in a dark red → orange → yellow → white ramp.
Rendering happens off the main thread once the map stops moving, at up to about 1.6 megapixels.
