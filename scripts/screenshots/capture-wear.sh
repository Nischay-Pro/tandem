#!/usr/bin/env bash
# Captures the Wear OS app on the running watch emulator from the debug build's screenshot activity.
# Usage: capture-wear.sh <apk> <output dir> <set name>
set -euo pipefail
apk="$1"
out="$2"
set_name="$3"
package=app.tandemforactual.android.localtest
activity="$package/app.tandemforactual.wear.screenshots.ScreenshotActivity"
mkdir -p "$out"

adb install -r "$apk" >/dev/null
adb shell settings put system screen_off_timeout 600000 || true
adb shell svc power stayon true || true

size="$(adb shell wm size | awk -F': ' '/Physical size/ {print $2}' | tr -d '\r')"
width="${size%x*}"
height="${size#*x}"

open() {
  adb shell am force-stop "$package"
  adb shell am start -W -n "$activity" "$@" >/dev/null
  sleep 6
}

snap() { adb exec-out screencap -p > "$out/wear-$set_name-$1.png"; }

open
snap glance

adb shell input swipe $((width * 4 / 5)) $((height / 2)) $((width / 5)) $((height / 2)) 250
sleep 3
snap bills

open --es screen add
snap add

adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile \
  --ecn component "$package/app.tandemforactual.wear.tiles.LeftToSpendTileService" >/dev/null || true
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 0 >/dev/null || true
sleep 6
snap tile

adb shell am force-stop "$package"
ls -la "$out"
