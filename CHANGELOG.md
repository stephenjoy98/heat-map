# Changelog

All notable changes to Location Ticker are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html):

- **MAJOR** for changes that break existing data, backups or sharing with older versions.
- **MINOR** for new features that keep working with what you already have.
- **PATCH** for bug fixes.

The app shows the entries since your last version as "What's new" after an update.

## [Unreleased]

## [2.18.0] - 2026-10-20

### Added
- Add your own travel modes in Settings → Travel modes: give each a name, a colour, and whether it travels
  on foot, by bike, by car or by public transport (used to match its trips to roads). They show up
  everywhere the built-in modes do: the Timeline, Insights questions and the map filters.
- Delete travel modes you don't use. Their trips become Other and Insights asks how you travelled them.
  Walking, Cycling, Driving and Other always stay; Car share and Transit can be added back.
- Backups include the travel modes you added, so restoring on another phone brings them back.

### Changed
- Settings → Travel mode colours is now Travel modes.

## [2.17.0] - 2026-10-20

### Added
- Long-press a trip on the Timeline to change how you travelled.
- Change a trip's times from the same menu: a start and end slider over the day's dots, as in Edit dots,
  or tap a dot to move the nearer end. The trips or stays before and after it adjust to match.
- Travel mode colours in Settings: choose each mode's colour with a colour picker or a hex code, and go back
  to the default for one mode or all of them.

### Fixed
- Tapping a trip or stay on the Timeline zooms the map to it and stays there, instead of jumping back to
  the whole day a moment later while recording.

## [2.16.1] - 2026-10-19

### Changed
- Opening the app starts a backup straight away (when automatic backup is on). The backup frequency sets
  how often it also backs up in the background.

## [2.16.0] - 2026-10-19

### Added
- Permissions in Settings: location, location all the time, physical activity, notifications,
  unrestricted battery and Health Connect, each showing whether it's allowed. Tap one to change it in
  Android's own dialog, or on the app's page in Android settings where there's no dialog.
- Backup frequency: every 6 hours, every 12 hours, daily or weekly.
- The About page shows the app icon, what the app does and its main features.

### Changed
- Review is now called Insights.
- The location marker is blue again.
- The dialog for choosing who writes your insights is wider.

### Removed
- The source code link on the About page (the licence link still points to it).

## [2.15.1] - 2026-10-19

### Changed
- Sharper terrain: hill shading uses elevation data two zoom levels more detailed, so it no longer looks
  blurry on high-resolution screens.
- Crisper Terrain picture in the layers sheet.

## [2.15.0] - 2026-10-19

### Removed
- Google Maps. The app uses OpenStreetMap maps only, which need no API key, and is smaller without the
  Google Maps libraries. The map provider choice in Settings is gone.

## [2.14.4] - 2026-10-19

### Changed
- Your location on the map is a red marker (it was blue), on both OpenStreetMap and Google Maps.
- The Default and Terrain pictures in the layers sheet show the same red marker.

## [2.14.3] - 2026-10-19

### Changed
- The Default and Terrain pictures in the layers sheet have dark versions for the dark theme.

### Fixed
- The compass moves down below the travel-mode chips when they're open, instead of covering them.

## [2.14.2] - 2026-10-19

### Changed
- The Default and Terrain tiles in the layers sheet show clear pictures of a street map and a terrain map.

### Fixed
- The compass sits under the profile button on the right again (it had slipped to the left).
- Buttons with long-press tips keep their places on the map.

## [2.14.1] - 2026-10-19

### Changed
- The compass stays in the top right corner, just under the profile button, even when the filters are
  open.
- Tilting the map back flat with two fingers switches 3D buildings off again.

## [2.14.0] - 2026-10-19

### Added
- Elevation is recorded with every dot (height above sea level where your phone provides it).
- 3D routes: with 3D buildings on, your routes are drawn at the heights they were recorded at, as a
  ribbon over a translucent curtain, so climbs and descents stand out among the buildings.
- Map layers button while editing dots on the Timeline.
- GPX exports include elevation.
- Long-press any button to see what it does.
- Tilt the map with two fingers to switch on 3D buildings and 3D routes.
- A scale bar appears while you move or zoom the map.
- Units setting: metric (km, m) or imperial (miles, feet) for every distance, height, speed and the map
  scale. It starts from your region's usual units.

### Fixed
- Tapping the map outside the GPS accuracy card closes it.

### Changed
- The compass is always shown again.
- Automatic recording is much finer: every second in a vehicle, 2 s cycling, 3 s walking, 5 s when
  unsure, 15 s when still and every minute after staying put. Standing still is judged over the last
  minute of fixes.
- Backups now include elevation. Older backups and dots recorded before this version have none, so
  they stay flat in 3D.

## [2.13.1] - 2026-10-19

### Changed
- Map type tiles in the layers sheet show real previews of the OpenStreetMap styles around you.
- Light theme: white cards and buttons on a light grey page, with stronger secondary text, for better
  contrast.

### Fixed
- Switching between Map and Timeline no longer flashes or reloads the maps; each tab keeps its map and
  its place.

## [2.13.0] - 2026-10-19

### Added
- Material expressive theme switch: colours from your wallpaper through Android's Monet engine, with
  rounder shapes, bolder headings and springier motion.
- Predictive back: the page shrinks and follows your swipe, showing where you'll land.
- Back now walks back through the pages you opened, then returns to the map, and asks before you leave
  the app.
- The account menu, place lists and the GPS accuracy card grow out of the button you tapped.
- A badge on the account button when trips or roads are waiting for your answer.
- Filter button at the top left of the map: travel-mode chips slide out when you need them, and a dot
  shows when the map is filtered.

### Changed
- Edit dots has moved to the Timeline tab.
- Map buttons rearranged: the compass sits under your profile button; layers, zoom and your position
  stack in the bottom right corner; GPS accuracy is in the bottom left.
- Review has moved from the bottom bar to the account menu.
- Theme options are now Light, Dark and System.
- Locations explored on your profile matches the other cards and follows your theme.
- Page titles collapse into the top bar as you scroll, on every page including your profile.
- Your profile opens faster: km explored is kept up to date in the background.

### Fixed
- The Undo message no longer covers the edit buttons.

## [2.12.0] - 2026-10-19

### Added
- Km explored on your profile, under Locations explored: the distance over ground you've covered for the
  first time. Travelling the same streets again doesn't add to it, and GPS drift while standing still
  can't inflate it. Km travelled still counts every trip.

## [2.11.0] - 2026-10-19

### Added
- Version numbers now follow Semantic Versioning (MAJOR.MINOR.PATCH).
- "What's new" appears after an update and lists everything that changed since your last version.
- The full changelog is available any time from About.

### Changed
- Location Ticker is now licensed under the GNU GPL v3.0 or later, so it and any versions based on it
  stay open source.

## [2.10.0] - 2026-10-19

### Added
- GPS accuracy button on the map, with signal bars that show how good your fix is.
- Accuracy card with live accuracy in metres and a Perfect, Good, Fair, Poor or Too weak rating.
- Coordinates, heading and the time of the latest fix on the accuracy card.
- Satellites in use and in view, broken down by GPS, Galileo, GLONASS, BeiDou and others.
- An explanation of what accuracy means and which fixes get recorded.

## [2.9.0] - 2026-10-19

### Added
- Deleted dots can be restored: each day has a Deleted list, and a delete can be undone straight away.
- About page with credits, a link to the source code and the licences of everything the app uses.

### Changed
- Location Ticker is now open source under the MIT License.
- Deleting dots is now a soft delete, so nothing is lost by mistake.

## [2.8.0] - 2026-10-19

### Added
- Automatic recording interval that adapts to how you're moving: faster in a vehicle, slower when still.
- Map layers: Default or Terrain map type, and 3D buildings that tilt the map.
- Edit dots: pick a day, narrow it down with a start and end slider or tap a dot, and delete mistakes.

## [2.7.0] - 2026-10-19

### Added
- Tap cities, countries or continents on your profile to see the full list.

### Changed
- Emoji throughout the app use JoyPixels artwork.

## [2.6.0] - 2026-10-19

### Added
- Profile page with your joining date, dots captured, time captured, distance, and the cities, states,
  countries and continents you've visited.
- Your Google profile picture on the account button.

### Changed
- Time captured is shown as a running total, such as "1 day 12 hours".
- Clearer, higher-contrast text on the profile page.

## [2.5.0] - 2026-10-19

### Added
- Timeline tab to browse your history by day, week, month or year.
- Account menu at the top right with Profile, Friends, Backup and Settings.

### Changed
- Friends and Backup have their own pages.

## [2.4.0] - 2026-10-19

### Added
- Zoom buttons and a compass that turns the map back to north.

### Changed
- Settings are organised into clear sections.

## [2.3.0] - 2026-10-19

### Added
- Smoother routes, snapped to roads where possible.
- When it isn't sure which road you took, the app asks.

## [2.2.0] - 2026-10-18

### Changed
- Sharper vector maps drawn at your screen's full resolution.
- Settings moved to the top right.
- A darker dark map, in the style of Google's apps.

### Fixed
- Map area calculations on OpenStreetMap maps.

## [2.1.1] - 2026-10-18

### Fixed
- The map showed "API key required" instead of a map; it now uses keyless OpenStreetMap tiles.

## [2.1.0] - 2026-10-18

### Added
- OpenStreetMap maps when there's no Google Maps key, so the app works with no setup.
- Material Design 3, with Material You colours from your wallpaper.

## [2.0.0] - 2026-10-18

### Added
- Google Maps, with a light Tesla-style map and a dark Strava-style map.
- Travel modes: walking, cycling, driving, car share and transit, and the app asks when it's unsure.
- Review tab with on-device AI insights about places, cities and distance.
- Step counts from Health Connect.
- Google sign-in, cloud backup, and friends you can add by ID or QR code to compare routes.

## [1.0.0] - 2026-10-18

### Added
- Records your location every 15 seconds to once a day.
- Strava-style heatmap of everywhere you've been.

