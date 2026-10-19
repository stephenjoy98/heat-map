# Changelog

All notable changes to Location Ticker are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html):

- **MAJOR** for changes that break existing data, backups or sharing with older versions.
- **MINOR** for new features that keep working with what you already have.
- **PATCH** for bug fixes.

The app shows the entries since your last version as "What's new" after an update.

## [Unreleased]

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

