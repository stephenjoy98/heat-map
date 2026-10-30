#!/usr/bin/env bash
# Emulator smoke test, run by CI on a booted emulator: installs the debug APK, skips onboarding, records a short
# walk along a street, taps through the main screens and saves a screenshot of each, a screen recording, the
# UI tree where a tap missed, and the logs. Exits non-zero only if the app crashed.
#
#   tools/emulator-smoke.sh <debug.apk> <version> [output dir]
set -uo pipefail

APK=$1
VERSION=$2
OUT=${3:-emulator-output}
PKG=io.github.stephenjoy98.heatmap
mkdir -p "$OUT"
NOTES="$OUT/steps.txt"
: > "$NOTES"

note() { echo "$*" | tee -a "$NOTES"; }

shot() {
    sleep "${2:-3}"
    adb exec-out screencap -p > "$OUT/$1.png" && note "screenshot $1"
}

# The emulator itself sometimes shows "… isn't responding" (often the launcher); tap Wait so it doesn't block.
dismiss_system_dialogs() {
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    if adb shell cat /sdcard/ui.xml | grep -q "isn't responding\|keeps stopping"; then
        local xy
        xy=$(adb shell cat /sdcard/ui.xml | python3 -I -c "
import re, sys
for m in re.finditer(r'text=\"(Wait|Close app|OK)\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]', sys.stdin.read()):
    x1, y1, x2, y2 = map(int, m.groups()[1:])
    print((x1 + x2) // 2, (y1 + y2) // 2)
    break
")
        if [ -n "$xy" ]; then
            # shellcheck disable=SC2086
            adb shell input tap $xy
            note "dismissed a system 'not responding' dialog"
            sleep 2
        fi
    fi
}

# Taps the centre of the first on-screen element whose text (mode text) or content description (mode desc)
# is exactly $2. Saves the UI tree and notes it if there's no such element.
tap() {
    local mode=$1 label=$2
    dismiss_system_dialogs
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    adb pull /sdcard/ui.xml "$OUT/.ui.xml" > /dev/null 2>&1
    local xy
    xy=$(python3 -I - "$OUT/.ui.xml" "$mode" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, mode, label = sys.argv[1:4]
attr = "text" if mode == "text" else "content-desc"
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
for node in root.iter("node"):
    if node.get(attr) == label:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
    if [ -n "$xy" ]; then
        # shellcheck disable=SC2086
        adb shell input tap $xy
        note "tapped $mode \"$label\" at $xy"
    else
        local name
        name="missing-$(echo "$label" | tr -c 'A-Za-z0-9' '-')"
        cp "$OUT/.ui.xml" "$OUT/$name.xml" 2>/dev/null
        note "MISSING $mode \"$label\" (UI tree saved as $name.xml)"
    fi
}

back() { adb shell input keyevent KEYCODE_BACK; sleep "${1:-2}"; }

# Prints "x y" for the centre of the first element whose text is exactly $1 (nothing if there's none).
centre_of() {
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    adb pull /sdcard/ui.xml "$OUT/.ui.xml" > /dev/null 2>&1
    python3 -I - "$OUT/.ui.xml" "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(0)
for node in root.iter("node"):
    if node.get("text") == sys.argv[2]:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
}

# Like tap, but scrolls down (up to 5 times) until the element is on screen.
tap_scrolling() {
    local mode=$1 label=$2 attr
    [ "$mode" = text ] && attr=text || attr=content-desc
    for _ in 1 2 3 4 5; do
        adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
        if adb shell cat /sdcard/ui.xml | grep -q "$attr=\"$label\""; then
            tap "$mode" "$label"
            return
        fi
        adb shell input swipe 540 1700 540 700 300
        sleep 1
    done
    tap "$mode" "$label"
}

adb wait-for-device
note "Location Ticker $VERSION on $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"

# -g grants the runtime permissions it asks for; background location needs its own grant.
adb install -r -g "$APK" | tee -a "$NOTES"
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION POST_NOTIFICATIONS ACTIVITY_RECOGNITION; do
    adb shell pm grant "$PKG" "android.permission.$p" > /dev/null 2>&1 || true
done

# Skip onboarding and "What's new", and don't let the emulator try to update itself.
cat > "$OUT/.ticker.xml" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="onboarded" value="true" />
    <string name="last_seen_version">$VERSION</string>
    <boolean name="auto_update" value="false" />
    <boolean name="auto_interval" value="true" />
</map>
XML
adb push "$OUT/.ticker.xml" /data/local/tmp/ticker.xml > /dev/null
adb shell chmod 644 /data/local/tmp/ticker.xml
adb shell run-as "$PKG" mkdir -p shared_prefs
adb shell run-as "$PKG" cp /data/local/tmp/ticker.xml shared_prefs/ticker.xml || note "couldn't preset settings"

adb logcat -c
# Start at Surrey Central, BC.
adb emu geo fix -122.8490 49.1891 > /dev/null
adb shell screenrecord --time-limit 175 --bit-rate 3000000 /sdcard/run.mp4 &
REC=$!

adb shell am start -W -n "$PKG/.ui.MainActivity" | tee -a "$NOTES"
sleep 5
dismiss_system_dialogs
shot 01-map 10

# Record a short walk north along King George Boulevard, a fix every 2 seconds.
tap desc "Start recording"
for i in $(seq 0 24); do
    lat=$(python3 -c "print(f'{49.1891 + $i * 0.00012:.6f}')")
    adb emu geo fix -122.8490 "$lat" > /dev/null
    sleep 2
done
shot 02-recording 2

# Map colours: open the layers sheet, unfold Map colours and drag the colour slider (just above its Muted / Vivid labels) to Vivid.
tap desc "Map layers"
shot 02a-layers 2
# Map colours is folded away: open it first.
tap text "Map colours"
read -r mx my <<< "$(centre_of Muted)"
read -r vx vy <<< "$(centre_of Vivid)"
if [ -n "${mx:-}" ] && [ -n "${vx:-}" ]; then
    adb shell input swipe "$mx" $((my - 70)) "$vx" $((vy - 70)) 600
    note "dragged Map colours to Vivid"
else
    note "MISSING the Map colours slider"
fi
shot 02b-layers-vivid 2
# Heatmap colours: Ice, then the map with it.
tap_scrolling text "Ice"
shot 02d-layers-ice 2
back
shot 02c-map-vivid 4
# Snow: plain white routes (no heat), then back to Fire.
tap desc "Map layers"
tap_scrolling text "Snow"
back
shot 02e-map-white 4
tap desc "Map layers"
tap_scrolling text "Fire"
back

tap text "Timeline"
shot 03-timeline 5
# Dots on the Timeline map, then back to Routes.
tap text "Dots"
shot 03a-timeline-dots 4
tap text "Routes"
tap text "Questions"
shot 04-questions 3
tap text "Trips"
sleep 2
# Long-press a stay: "Select…" starts selecting with it; the bar counts trips and stays.
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb pull /sdcard/ui.xml "$OUT/.ui.xml" > /dev/null 2>&1
stay_xy=$(python3 -I - "$OUT/.ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(0)
for node in root.iter("node"):
    if node.get("text") == "Stayed here":
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
if [ -n "$stay_xy" ]; then
    # shellcheck disable=SC2086
    set -- $stay_xy
    adb shell input swipe "$1" "$2" "$1" "$2" 900
    note "long-pressed a stay at $stay_xy"
    shot 04a-stay-menu 2
    tap text "Select…"
    shot 04b-stay-selected 3
    tap desc "Stop selecting"
    sleep 2
else
    note "MISSING a \"Stayed here\" row to long-press"
fi

tap desc "Account"
shot 05-account 3
tap text "Settings"
shot 06-settings 3
tap text "General"
shot 07-settings-general 3
back
tap text "Battery"
shot 08-settings-battery 3
tap text "Battery saver"
shot 09-battery-saver 2
tap text "User"
back
tap_scrolling text "Updates"
shot 10-settings-updates 4
back
back

tap text "Insights"
shot 11-insights 6
adb shell input swipe 540 1800 540 500 400
shot 12-insights-lower 2
adb shell input swipe 540 1800 540 500 400
shot 13-insights-bottom 2
back

tap text "Profile"
shot 14-profile 6
# Areas: the map of explored squares, in red.
tap text "AREAS ›"
shot 14a-areas-map 6
back
# Breakdowns as a pie chart (the card is further down), then back to bars.
tap_scrolling desc "Show as a pie chart"
shot 14b-profile-pie 3
tap desc "Show as bars"
back
back
tap text "Map"
shot 15-map-after 4

# Follow me: long-press the location button (its description starts "Go to my latest position" or "Turn map").
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb pull /sdcard/ui.xml "$OUT/.ui.xml" > /dev/null 2>&1
read -r fx fy <<< "$(python3 -I - "$OUT/.ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(0)
for node in root.iter("node"):
    d = node.get("content-desc") or ""
    if "long-press to follow you" in d:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)"
if [ -n "${fx:-}" ]; then
    adb shell input swipe "$fx" "$fy" "$fx" "$fy" 900
    note "long-pressed the location button (follow me)"
    shot 15a-follow 4
    adb shell input swipe 540 1000 300 1000 300
    note "dragged the map while following (it should keep following)"
    shot 15b-follow-drag 6
    adb shell input swipe "$fx" "$fy" "$fx" "$fy" 900
    note "long-pressed it again to stop following"
else
    note "MISSING the location button for follow me"
fi

# Full screen: only the compass, location, zoom, layers and full-screen buttons stay; the second shot is taken
# mid-animation to catch the controls flying out.
tap desc "Full screen"
sleep 0.15
adb exec-out screencap -p > "$OUT/16a-fullscreen-moving.png"
shot 16-fullscreen 2
tap desc "Exit full screen"
shot 17-fullscreen-exit 2

# The widget itself can't be placed here; ask for a refresh so its drawing code runs.
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n "$PKG/.widget.FollowWidget" > /dev/null 2>&1
sleep 3

kill "$REC" 2> /dev/null
wait "$REC" 2> /dev/null
sleep 3
adb pull /sdcard/run.mp4 "$OUT/run.mp4" > /dev/null 2>&1 && note "saved run.mp4"

# The widget's follow me button, as the widget sends it (its receiver isn't exported, so this needs a root shell):
# from the home screen it turns follow me on without opening the app (recording every second, shown in the
# notification), and the map follows when the app comes back; a second tap turns it off.
if adb root > /dev/null 2>&1; then
    sleep 3
    adb wait-for-device
    toggle() { adb shell am broadcast -a io.github.stephenjoy98.heatmap.TOGGLE_FOLLOW -n "$PKG/.widget.FollowWidget" > /dev/null 2>&1; }
    adb shell input keyevent KEYCODE_HOME
    sleep 2
    toggle
    note "widget button on the home screen: follow me on"
    sleep 4
    adb shell cmd statusbar expand-notifications
    shot 18-widget-follow-notification 3
    adb shell cmd statusbar collapse
    adb shell am start -n "$PKG/.ui.MainActivity" > /dev/null 2>&1
    note "opened the app while follow me is on"
    shot 19-widget-follow-map 5
    adb shell input keyevent KEYCODE_HOME
    sleep 2
    toggle
    note "widget button again: follow me off"
    sleep 3
    adb shell am start -n "$PKG/.ui.MainActivity" > /dev/null 2>&1
    shot 20-widget-follow-off 5
else
    note "no root shell: skipped the widget's follow me button"
fi

adb logcat -d > "$OUT/logcat.txt"
adb logcat -d -b crash > "$OUT/crash.txt"
rm -f "$OUT/.ui.xml" "$OUT/.ticker.xml"

if grep -q "Process: $PKG" "$OUT/crash.txt"; then
    note "CRASH: see crash.txt"
    exit 1
fi
if ! adb shell pidof "$PKG" > /dev/null; then
    note "The app isn't running at the end of the test"
    exit 1
fi
note "No crashes"
