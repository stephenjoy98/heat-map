# Changelog

All notable changes to Location Ticker are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html):

- **MAJOR** for changes that break existing data, backups or sharing with older versions.
- **MINOR** for new features that keep working with what you already have.
- **PATCH** for bug fixes.

The app shows the entries since your last version as "What's new" after an update.

## [Unreleased]

## [2.55.0] - 2026-10-27

### Added
- **Mini heatmap widget** for the home screen: your heatmap in your heat colours around where you are, today's km,
  and a follow me button that opens the map already following you. Tap the map to open the app. It refreshes
  about every 30 minutes, and as your trips are worked out (at most every 10 minutes). Long-press the home screen
  › Widgets › Location Ticker to add it; it can be resized.

## [2.54.0] - 2026-10-27

### Added
- A button at the top right of the "By travel mode" cards (profile and Insights), a friend's "How they get around"
  and Insights' Satellites switches the bars to a pie chart with each share as a percentage, and back. The choice
  is remembered for all of them.

## [2.53.0] - 2026-10-27

### Added
- Tapping **Areas** on the profile opens a map, three quarters of the screen tall, with every square you've been to
  filled red, and the squares counted by city below it.
- Tap the carbon footprint card to see how it's worked out: each travel mode's km, its rate and the CO₂.

### Fixed
- The carbon footprint only counted trips tagged exactly Driving, RideShare or PublicTransit, so car pools,
  shuttles, SkyTrain, buses and other sub-modes or modes you added counted as nothing. Every mode now counts by its
  kind (car, bus, train or boat), with rates from the UK government's conversion factors: car 170 g/km (a car pool
  half that), bus 100, train 35, SkyTrain or metro 30, ferry 20.

## [2.52.0] - 2026-10-26

### Added
- Snapping a route (Show snapped route, Find roads) shows its progress step by step in a dialog: which service it
  asks, how many lines it got back, matching the dots, and when something fails, the step and the reason.

### Changed
- The editing panels (Edit dots, Change times, Add a trip, Fix route, Split) take at most about half the screen
  and scroll inside, so the map stays big. The travel mode is a compact drop-down instead of a block of chips
  there and in the long-press sheet.

## [2.51.0] - 2026-10-26

### Changed
- Fixing a route ("Snap to a road") shows the route as it's drawn now in white while you edit, and nothing is saved
  before you've seen the result: **Show snapped route** draws the snapped line on the map first (or pick one of
  the roads Find roads offers), then **Save this route**. Changing the range clears the preview.

## [2.50.0] - 2026-10-26

### Added
- Tapping a trip on the Timeline (or a part of a group) highlights it on the map: a white line with a green start
  and red end, so you can tell which section it is. A route you long-press is highlighted while its sheet is open.
  Tap the map to let go.
- Train and boat trips (SkyTrain, SeaBus and other "Train or boat" modes) now snap to OpenStreetMap's railways, or
  its ferry routes for boats, instead of keeping the straight lines between recorded dots. Trips already recorded
  are matched once in the background. Fix route on such a trip follows the tracks or ferry route as well.

## [2.49.1] - 2026-10-26

### Fixed
- "Add a trip here" on a stay with no dots in between (only one at each end) opened a different part of the day
  (the middle of all the day's dots). It now starts on the dots either side of that stay.

## [2.49.0] - 2026-10-26

### Added
- Long-pressing a route on the Timeline map now also finds road-matched lines that wander away from your dots,
  like a long straight line across the map. Its sheet has two new options: **Draw the recorded route instead of
  the road** (undoes a bad road match) and **Edit its dots** (opens Edit dots on that day to delete or move stray
  dots).

## [2.48.1] - 2026-10-26

### Changed
- The plain white routes option is called **Snow** now, and its routes have a soft icy glow around them.

## [2.48.0] - 2026-10-26

### Added
- **White** in the map layers sheet's Heatmap colours: your explored routes drawn plain white, without the heat
  colours (dark grey on the light map, where white wouldn't show). Friends' routes stay in Fire colours.

## [2.47.0] - 2026-10-26

### Changed
- 3D buildings (in the map layers sheet) now show on the flat map too, not only when it's tilted. Tilting the
  map into 3D (compass or two fingers) is separate and no longer turns buildings on or off.

## [2.46.3] - 2026-10-26

### Changed
- "How did you travel?" takes two steps now: tap a travel mode (the best guess is picked to start with), then
  Save, so a stray tap doesn't answer it.
- The Undo banner on the Timeline shows at the top of the screen, so it no longer covers Skip and Save.

## [2.46.2] - 2026-10-26

### Changed
- Roads on the dark map are a bit darker, so your routes stand out more.

## [2.46.1] - 2026-10-25

### Changed
- Follow me turns the map with you on the flat map too: the marker's arrow points straight up and the map rotates
  as you change direction. Stopping follow me turns the map back north up.

## [2.46.0] - 2026-10-25

### Added
- While follow me is on (and the app is on screen), recording switches to every second, so the route is traced in
  full detail. It goes back to your usual interval when you stop following or leave the app.

## [2.45.6] - 2026-10-24

### Changed
- The follow me marker is a little smaller, and its ring and glow match the dark red of your routes (dark blue with
  Ice).

## [2.45.5] - 2026-10-24

### Changed
- The follow me marker's ring and glow are a bit darker, and its arrow is light grey instead of white.

## [2.45.4] - 2026-10-24

### Changed
- New follow me marker, the trace cursor: a dark disc with a white arrow, ringed and glowing in the colour of your
  routes (red with Fire, blue with Ice). It's still a thin disc on the 3D map.

## [2.45.3] - 2026-10-24

### Changed
- The follow me marker has no white outline any more, and on the 3D map it's a thin disc with a dark edge.

### Fixed
- Follow me starts straight away: the map jumps to you (turned your way on the 3D map) instead of waiting for the
  next position, and on the 3D map the arrow stays pointing straight up while the map turns under it. Tilting into
  or out of 3D while following turns the map at once, and the map turning no longer stops the tilt halfway.

## [2.45.2] - 2026-10-24

### Changed
- The follow me marker is black and white like Uber's: a flat white disc with a thick black ring and a black arrow
  (with a thin white outline in dark theme so the ring shows on the dark map).

## [2.45.1] - 2026-10-24

### Changed
- The follow me marker is flatter and styled like Uber's, in red: a red disc with a black border and a black arrow
  (with a thin light outline in dark theme so the border shows on the dark map).

## [2.45.0] - 2026-10-24

### Added
- Move dots in Edit dots on the Timeline: tap a dot, tap **Move**, then tap where it should be. **Add dots** puts
  new dots after the picked one, one per tap on the map, so you can trace the way a ferry actually went across the
  water instead of the line GPS drew. The trip is worked out again from the moved dots, and Undo puts them back.

### Changed
- Follow me keeps following when you drag the map: look around and the map comes back to you a few seconds after
  you let go (or straight away if you tap the location button). Long-press the location button again to stop.
- The follow me marker looks solid now: a puck with a red side standing on the map and a raised two-tone arrow. It
  has a dark face in dark theme.

## [2.44.0] - 2026-10-24

### Changed
- Follow me shows you as a navigation arrow (a white disc with a red ring and arrow) instead of the blue dot. On the
  flat map it stays north up and the arrow turns the way you're heading; on the 3D map the arrow lies flat in the
  middle pointing straight ahead and the map turns and moves around it as you travel.

## [2.43.0] - 2026-10-23

### Added
- 3D routes in the map layers sheet: turn off to keep your routes flat on the map when it's tilted into 3D (3D
  buildings stay). On by default, raising routes to the heights they were recorded at.

## [2.42.0] - 2026-10-23

### Added
- Routes or Dots: a switch on the Timeline's map shows every recorded dot of the day; tap a dot to pick it and
  delete it (or pick a range). The map's layers sheet has the same switch for the main map (Dots draws where your
  dots are, denser where there are more).
- Split a trip in two: "Split into two trips" when you long-press a trip (in the list or on the map). A slider, the
  minus and plus buttons or a tap on a dot picks where; both parts keep the trip's travel mode.
- Snap only part of a trip to a road: "Snap to a road (all or part)" (was "Fix route on the map"). Narrow the range
  to the stretch you want and only that part is matched to roads, as a trip of its own; the rest stays as it was.

## [2.41.1] - 2026-10-23

### Fixed
- Long straight lines to nowhere on the map: a coarse GPS fix (or two or three) that jumped far away and came
  straight back is now removed automatically, on new recordings and once over your whole history. Removed dots
  go to deleted dots, so you can bring them back from Edit dots.
- Roads matched to a trip that took a detour you never made (a straight line off to the side and back) are
  caught: such trips keep their recorded line instead, and trips matched before this are checked once too.

## [2.41.0] - 2026-10-23

### Added
- Heatmap colours in the map layers sheet: Fire (a cool dark red where you've been once, through red, orange and
  yellow, to white where you go most) or Ice (the darkest blue through blues and cyan to white). Friends' heatmaps
  use the other palette so theirs and yours stay apart.

### Fixed
- Tapping a tile's name in the map layers sheet (Default, Terrain, 3D buildings…) picks it, not just the picture.

### Changed
- The heatmap shows a much wider range of colours: it takes about 30 visits to reach the hottest colour instead
  of 20, so roads you use a few times, often and every day all look different.

## [2.40.0] - 2026-10-22

### Changed
- A tidier map layers sheet: Map colours folds away under its own row (tap it to show the slider and "Colour roads
  too"), and the long 3D buildings explanation is gone.
- Settings › Updates has a Changelog row that opens every version's changes (also still under About).

### Removed
- Map brightness. Any brightness you'd set is cleared, so the map shows at its normal brightness.

## [2.39.1] - 2026-10-22

### Changed
- The compass dial (N, E, S, W, with small ticks for NE, SE, SW and NW) stays put, and the needle turns to point
  north as you rotate the map.
- Every pop-up (About accuracy, confirmations, delete and restore questions, and the rest) now spans nearly the
  full width of the screen, like the wider ones already did.

## [2.39.0] - 2026-10-22

### Added
- Follow me: long-press the location button and the map keeps your blue dot in the middle, gliding along under
  it as you travel. The button turns into an arrow while it's on; drag the map or tap the button to stop.
- While you're moving, a small arrow by the blue dot points the way you're heading.
- An altimeter on the map: your height above sea level from GPS (m or ft), on the left with the speedometer now
  below it.

### Changed
- The speedometer shrinks to a small dot when you're not moving (under 1 km/h or mph) and grows back into the
  badge when you are.

## [2.38.0] - 2026-10-22

### Added
- A speedometer on the map, on the left opposite the compass: your speed from GPS in km/h or mph (as set in
  Settings), a dash when there's no recent fix.
- The compass does more: once the map points north, tapping it tilts the map into 3D, and tapping again lays it
  flat.

## [2.37.1] - 2026-10-21

### Changed
- The blue dot glides smoothly across the map as you travel instead of jumping from point to point. It also
  follows the live GPS fix while the map is open, so it keeps up between recorded dots.
- Long-pressing a route on the Timeline map opens a sheet that slides up from the bottom (full width) instead of
  a narrow pop-up.

## [2.37.0] - 2026-10-21

### Added
- The compass shows N, E, S and W around its needle; the whole dial turns as you rotate the map, so the
  letters and the red needle always point the real way.
- Colour roads too: a switch under Map colours in the layers sheet. Off (the default) keeps roads in the standard
  colours while water, parks and buildings get the vivid ones.

### Changed
- The location button keeps its own icon; tapping it again once you're there still turns the map north up.
- The map's OpenStreetMap info button sits a little higher, clear of the GPS signal button.
- New versions are no longer downloaded on their own. When one is out, a sheet tells you on the map screen, with
  Later and Download and install now. "Update automatically" in Settings › Updates is now off for everyone; turn it
  back on there if you want it.
- Switching the map to full screen and back: the hidden controls fly off the edges (easing out) and back in
  (easing in) instead of fading, the tab bar slides away with them, and the map no longer shakes.

## [2.36.1] - 2026-10-21

### Changed
- Undo bars and other messages at the bottom of the screen can be swiped away sideways.

### Fixed
- Backup failing with "too many index entries" on days with lots of dots (fast recording on a long drive).
  Each day is now stored in smaller parts.

## [2.36.0] - 2026-10-21

### Added
- Minus and plus buttons under the time sliders when changing a trip's times, adding a trip, fixing a route or
  editing dots: each tap moves the start or the end by one dot (times shown to the second), and holding a
  button keeps going.
- Join trips into one: "Join into one trip" in a group's menu on the Timeline, or Select several trips and tap
  Join. The joined trip runs from the first one's start to the last one's end, takes in the loose dots between
  them (the grey bits on the map) and keeps the mode of the longest one.
- Long-press a route on the Timeline's map to edit it: a dialog shows which trip you pressed and offers its travel
  mode, Change times and Fix route. Grey dots outside any trip can be made a trip, or join the trips on either side.
- Red dots show where something a notification was about is waiting: on the account button, then on Settings and
  Settings › Updates for a new version, or on About and its Changelog after an update. They go once you've looked.
- "New" markers on the sections that gained or changed something since you last opened them (on the Account page,
  Settings sections and the Timeline tab), with a red dot on the account button until you've looked.
- Undo for every change on the Timeline: changing a trip's mode (one, a group or a selection), its times, adding,
  joining or fixing trips, and answers to questions. A bar with Undo shows for a few seconds after each one and
  puts things back as they were, including the question.
- Hide your location's blue dot: My location in the map layers sheet.
- Full screen map: the new button above the location button hides the tabs, stats, filters, recording panel and
  GPS button, leaving the compass, location, zoom and layers buttons. Tap it again, or go back, to bring them back.

### Fixed
- Tilting the map into 3D with two fingers works even when the second finger lands a moment after the first.
  Before, that only panned the map unless both fingers touched down at the same time.
- Highway route numbers show on the dark map again; their badges had turned into blank grey boxes.

### Changed
- A new version is offered in a sheet that slides up from the bottom, only on the map screen and once each time
  you open the app, instead of a pop-up that could appear anywhere. "What's new" after an update slides up the
  same way. Both slide back down when you're done.
- The location button does double duty: once the map is on your latest position, tapping it again turns the map
  north up, like the compass. It shows a compass needle while it does that.

## [2.35.0] - 2026-10-21

### Added
- Map colours in the layers sheet: from the standard muted look to vivid, with blue water, green parks,
  brighter roads (yellow main roads on the light map), coloured buildings and clearer labels, for more
  contrast. Set separately for the dark and the light theme.

### Fixed
- Each notification opens its own place again: the recording notification just opens the app, "new version"
  opens Settings › Updates, "updated" opens the changelog, and trip and road questions open the Timeline's
  Questions tab. Before, after one update notification every notification opened the changelog.
- Switching Settings between a page per section and one list fades smoothly from one to the other instead of
  jumping.
- Going back from a Settings section returns to where you were in the list of sections, not the top.

## [2.34.0] - 2026-10-21

### Added
- Map brightness in the layers sheet (the layers button on the map): make the map lighter or darker, separately
  for the dark and the light theme. It changes roads, land, water and buildings; your heatmap, routes and
  place names stay as they are.

## [2.33.3] - 2026-10-21

### Changed
- On the About page, the heart, the Claude mark and a new Canadian flag sit level with the words around them.

## [2.33.2] - 2026-10-21

### Fixed
- Routes at speed no longer break up or sprout spikes when GPS jumps: a fix that leaps further than anything
  could travel since the last one (common on highways, near tall buildings and under bridges) is skipped,
  jumps under 1 km don't split a trip, and a lost signal of up to 10 minutes while driving (a tunnel) carries
  the same trip on. The whole drive is then snapped to the road in one go. Trips recorded before this
  version keep their old pieces.

## [2.33.1] - 2026-10-21

### Fixed
- Tapping the "new version available" notification opens Settings › Updates, and the "updated" notification
  opens the full changelog, instead of just opening the app.

## [2.33.0] - 2026-10-21

### Added
- Beta and stable updates. New changes now come out first as beta builds; once they've been tried out they're
  released as a stable version with everything since the last one. Under Settings › Updates › Which updates:
  - **Stable** (recommended): tried-out releases, small fixes included.
  - **Stable, new features only**: only stable releases with new features.
  - **Beta**: every build as soon as it's made, for trying the newest changes early.

  The Updates page shows when you're running a beta build.

### Changed
- The release page for each stable version lists every change since the previous stable release.

## [2.32.0] - 2026-10-21

### Added
- Battery profiles under Settings › Battery, each with what it records and how precisely:
  - **User**: your own Recording settings (interval or automatic, GPS accuracy). They're kept while another
    profile is on, so choosing User brings them back.
  - **Performance**: every second in a vehicle or on a bike, 2 s walking, 10 s when still; precise GPS. The
    most detailed routes and the most battery.
  - **Default**: every 3 s in a vehicle, 5 s cycling, 8 s walking, a minute when still and 5 minutes after a
    while; precise GPS.
  - **Battery saver**: every 10 s in a vehicle, 15 s cycling, 30 s walking, 5 minutes when still and
    15 minutes after a while, from Wi-Fi and mobile networks. The least battery; routes are rougher.

  Changing a Recording setting by hand switches back to User. Saved places still slow recording down while
  you're settled, whichever profile is on.

- Insights › Satellites updates live while you look at it, and shows how many satellites are fixing your
  position right now while you're recording. It doesn't use extra battery: it only listens to what the GPS
  chip already reports while recording has it on.

- When the app opens and a new version is out, it's offered with its changelog: Update now downloads and
  installs it there and then (with progress), Later waits until you next open the app (or, with automatic
  updates on, installs it once you've left the app).
- Settings › Updates › Which updates: every update, or new features only (2.31 → 2.32, with fixes coming
  along in the next one). Check now still finds every version.

### Fixed
- "1 sats" now reads "1 sat" in Insights › Satellites.

## [2.31.1] - 2026-10-21

### Fixed
- Downloading an update no longer fails with "404" when the release is being refreshed on GitHub at that
  moment: the app fetches the release's latest details first and tries again a few times, half a minute apart.
- "What's new" on the Updates page joins each line of the changelog back up instead of showing ragged breaks.
- The account button has a name for screen readers such as TalkBack.

## [2.31.0] - 2026-10-21

### Added
- Insights › Satellites: which navigation satellites fixed your location in the chosen period, grouped by
  system (GPS, Galileo, GLONASS, BeiDou, QZSS, NavIC) with how many of each were used and how often, plus a
  full list of every satellite (G05, E24, R12…) and how many dots it helped with. The app starts noting
  satellites with this version, so earlier history has none; dots fixed from Wi-Fi or mobile networks (for
  example while settled at a saved place) don't use satellites.
- Satellite counts are backed up with the rest of your history and come back with a restore (the restore
  preview lists them).

## [2.30.3] - 2026-10-21

### Changed
- 3D routes climb and descend in a smooth slope instead of visible steps: sloping stretches are drawn as many
  short pieces (each about 15 cm higher than the last), and heights are smoothed over about 200 m of route.

## [2.30.2] - 2026-10-20

### Changed
- The country, city and continent lists on your profile show JoyPixels flags and globes again.
- Graphs use bright colours like the travel-mode bars: storage is split into coloured parts (your history,
  map cache, other app files, other apps) with a key, and battery bars go from green for light use to red for
  the heaviest.

## [2.30.1] - 2026-10-20

### Changed
- Settings are grouped under Recording, Map and trips, App and System. Units moved out of Appearance into a
  new General page, along with the choice between a page per section and one list.
- The colour picker has many more presets: twelve hues round the rainbow in a vivid, a light and a deep tone,
  plus greys and browns, in a wider dialog.

### Fixed
- Going back from a page no longer flashes the map before the previous page appears.
- Opening a Settings section, and going back from it, slides smoothly instead of jumping.

## [2.30.0] - 2026-10-20

### Added
- Settings › Updates: see your version, check for a new one, read what's new in it and install it with one
  tap. Location Ticker gets its updates from its GitHub releases.
- Automatic updates (on by default): the app checks when it opens and every 6 hours, downloads a new version
  (on Wi-Fi only, unless you turn that off) and installs it once you've left the app, then lets you know it
  updated. Recording carries on afterwards. Android asks you to confirm the first update; after that,
  Android 12 and later install updates without asking. The first time, Android may also ask you to allow
  Location Ticker to install apps.

## [2.29.1] - 2026-10-20

### Changed
- 3D routes are back to how they were in 2.27: taller and easier to see, measured from the lowest place
  you've recorded and stretched 1.5×, and orange again. Lines, dots and rings drawn on maps keep the map's
  own orange whatever the app's theme.

## [2.29.0] - 2026-10-20

### Added
- Tapping your account picture opens an Account page instead of a popup. Going back from Profile, Friends,
  Backup, Insights, Settings or About returns to it rather than jumping straight to the map.

### Changed
- The dark theme's accent is a soft near-white, like Google's own dark apps, instead of orange. 3D routes
  use the accent colour too (or your wallpaper's colour with Material expressive).
- Google's Material icons replace the emoji everywhere: Settings, About, Profile, stamps, the restore preview
  and What's new.
- Text is a size larger throughout, without the extra letter spacing, closer to Gmail.

### Fixed
- The question count on the account button is no longer cut off on the map.

### Removed
- JoyPixels emoji artwork (and its attribution), now that the app uses Material icons.

## [2.28.0] - 2026-10-20

### Added
- Questions have their own tab on the Timeline. They come one after another in a queue: answer one and the
  next appears, or Skip it to the back of the queue. The question's map fills the screen, and no longer
  shows through the Timeline's map when you scroll. "Questions" in the account menu opens this tab.

### Changed
- 3D routes are drawn at their real height above the ground, at the same scale as the 3D buildings, rather
  than above the lowest place you've ever recorded (and stretched 1.5×). An elevated SkyTrain line now sits
  a few storeys up beside the buildings, and a 15th-floor home about halfway up its tower. The ground's
  height comes from the same free terrain tiles as hill shading and is saved on the phone; offline, routes
  fall back to the old way.

### Fixed
- 3D routes no longer flicker where pieces meet or where several trips cover the same street: a street
  travelled many times at the same height is drawn once.
- No more spikes and jagged teeth on 3D routes where GPS wobbles back and forth (at home, say).

## [2.27.1] - 2026-10-20

### Changed
- "What's new", the restore preview and the areas and cities lists are as wide as the app's other cards.

## [2.27.0] - 2026-10-20

### Added
- Restore shows a preview first: how many dots (and how many this phone doesn't have yet) over which dates,
  trips, road and height answers, deleted dots, travel modes, saved places and settings. Nothing changes
  until you tap Restore.

## [2.26.0] - 2026-10-20

### Added
- Backups now include your trips (with their travel modes and whether they still need an answer), their
  road-snapped shapes and your "which road?" answers, and your "different heights here?" answers. Restoring
  on another phone brings them back as they were instead of working them out again. The first backup after
  updating uploads every day once more to add them.
- Backups also include everything you've set up: recording interval and accuracy, theme, units, map type and
  3D, route display, saved places, travel mode names, colours and deletions, map filters, who writes your
  insights, sharing and backup choices, and how Settings is laid out. Restoring sets the new phone up the same.
- Dots you deleted stay deleted after a restore, and are still in the Deleted list to bring back.
- Restored trips keep their shared route, so friends don't see a second copy.

## [2.25.0] - 2026-10-20

### Added
- Storage on the Backup page: how much Location Ticker uses on the phone (your history, the map and picture
  cache, other app files) and how much free space the phone has; and, when signed in, about how much the
  backup and shared routes take in the cloud and how much of the free Firebase plan's 1 GiB is left.

## [2.24.0] - 2026-10-20

### Changed
- 3D routes along a way you've travelled many times are drawn at one agreed height, taken from all your trips
  there, instead of a slightly different height each time (GPS heights wander by 10 m or more between trips).

### Added
- "Different heights here?" on the Timeline: where your trips were recorded at two clearly different heights
  (more than 15 m apart), such as a bridge over a road or different floors of a building at home, the app
  asks whether they're really different levels. "Different levels" keeps each trip at its own height in 3D
  there; "Same level" draws one.

## [2.23.0] - 2026-10-20

### Added
- Tap "new areas explored" on Insights to see what an area is (a map square about 220 m across), how many
  you were in, how many were new, and which cities you found them in.
- Tap "cities" on Insights for every city with the distance you travelled in it and your share of time.
- The same on your profile: tap Areas for the squares you've been to by city; the cities list shows the
  distance in each; and a travel mode breakdown.
- Settings can show a page per section (the new default) or everything in one list, chosen at the top of
  Settings.

### Changed
- Travel mode breakdowns group sub-modes under their mode: PublicTransit shows SkyTrain, Bus and SeaBus
  added together; tap it to see each.

### Fixed
- 3D routes keep the same height however you move the map; heights are now measured from one fixed low
  point of everything you've recorded, not from what's on screen.
- 3D routes follow the same line as the flat route (smoothed, or snapped to the road), as one continuous
  ribbon without gaps at the bends, with heights smoothed and GPS height spikes removed.
- The 3D route no longer flickers where its curtain met the ribbon.

## [2.22.0] - 2026-10-20

### Added
- Fix route on the Timeline: long-press a trip and choose "Fix route on the map" (or "Join parts and fix
  route" on grouped trips). Pick the stretch with the slider, across gaps and several trips, and it's
  joined into one trip and snapped to roads. Or tap "Find roads" to see the ways between its ends and pick
  one, and tap the map where you went to make the road pass through there.
- Friend profiles: tap a friend to see their totals, how they get around, their cities and a heatmap of
  everywhere they've shared, zoomed out to fit. The map pans, zooms, rotates and tilts. "Compare with
  you" opens the side-by-side comparison.
- Your friends list is saved on the phone, so it shows straight away and without a connection.

### Changed
- The About page lists everything the app can do now.

## [2.21.0] - 2026-10-20

### Added
- Places in Settings: save home, work, school and other places you spend time, with a size (50–500 m) and
  how often to record while you're there (every 1, 5, 10, 15 or 30 minutes). Set one by moving a map under
  a pin, from where you are now, or from suggestions of where you've spent the most time.
- While you're settled at a place, recording slows to its interval and uses Wi-Fi and cell positioning,
  saving battery. As soon as activity recognition sees you walking or driving, it checks about every
  20 seconds, and once you're out of the circle it goes back to its usual pace. Android's geofencing also
  reports leaving a place when location is allowed all the time.
- The recording tile says when you're at a saved place, and the Timeline names stays at them (Home, Work…).

## [2.20.0] - 2026-10-20

### Added
- Select several trips on the Timeline and change their mode together: long-press a trip and choose
  Select, then tap the others (parts of grouped trips too).
- Add a trip yourself: the + button on a day, or long-press a stay and choose "Add a trip here". Pick its
  first and last dot with the start and end slider (or tap dots), and how you travelled. It can go between
  other sections or before or after them all; trips it overlaps are trimmed, and one it lands in the middle
  of is split around it.
- Battery in Settings: how much battery recording used per hour at each recording interval, over the last
  7 or 30 days, with the screen off (to leave out most other use) or for all the time recording.

## [2.19.0] - 2026-10-20

### Added
- Sub-modes: a travel mode can sit inside another. New suggested modes: CarPool, RideShare › Bike and Car,
  and PublicTransit › SkyTrain, Bus and SeaBus (modes you already have with those names are kept).
  Pick a sub-mode anywhere you pick a mode; the map filter for a mode includes its sub-modes.
- Add your own sub-modes in Settings → Travel modes with "Part of", and rename built-in modes.
- "Train or boat" kind for modes like SkyTrain and SeaBus: their trips aren't snapped to roads.
- Tap the recording tile on the map to say what kind of trip you're on right now (dots recorded from then on
  are filed under it, without being asked), and to switch between automatic and a set interval.
- Back-to-back trips in the same mode show as one entry on the Timeline. Long-press it, or tap the arrow, to
  see each part, and edit any part like a normal trip; the menu changes the mode of all parts at once.
- A badge on the Timeline tab counts the questions waiting for you.

### Changed
- "How did you travel?" and "Which road did you take?" are at the top of the Timeline, one at a time. The
  map can be pinch-zoomed and has zoom buttons, with the start in green and the end in red.
- The account menu highlights the questions waiting and takes you straight to them.
- Car share is now called RideShare, and Transit is PublicTransit.

### Fixed
- The badge on the account button is no longer cut off.
- Road questions counted in the badge can be answered again (they had no place in the app).

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

