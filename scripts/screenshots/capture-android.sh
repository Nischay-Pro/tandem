#!/usr/bin/env bash
# Captures Tandem screenshots at Pixel 11 and Pixel 11 Pro Fold screen sizes using the debug build's screenshot mode.
# Usage: capture-android.sh <apk> <output dir> [server url]
set -euo pipefail
apk="$1"
out="$2"
server="${3:-http://10.0.2.2:19753}"
package=app.tandemforactual.android.localtest
mkdir -p "$out"

adb install -r "$apk" >/dev/null
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
clean_status_bar() {
  demo exit
  demo enter
  demo clock -e hhmm 0941
  demo battery -e level 100 -e plugged false
  demo network -e wifi show -e level 4 -e fully true
  demo network -e mobile hide
  demo notifications -e visible false
}

wait=75
# name:size:density. Pixel 11 is 1080x2424; Pixel 11 Pro Fold is 2076x2152 inside and 1080x2342 on the cover.
for device in pixel-11:1080x2424:420 pixel-11-pro-fold-open:2076x2152:390 pixel-11-pro-fold-folded:1080x2342:420; do
  IFS=: read -r name size density <<<"$device"
  adb shell wm size "$size"
  adb shell wm density "$density"
  sleep 5
  for mode in light dark; do
    if [ "$mode" = dark ]; then adb shell cmd uimode night yes >/dev/null; else adb shell cmd uimode night no >/dev/null; fi
    clean_status_bar
    for spec in "budget:" "activity:Corner Market" "accounts:Everyday checking" "reports:" "category:Phone"; do
      screen="${spec%%:*}"
      select="${spec#*:}"
      adb shell am force-stop "$package"
      adb shell am start -n "$package/app.tandemforactual.android.MainActivity" \
        --es TANDEM_SCREENSHOT_SERVER "$server" \
        --es TANDEM_SCREENSHOT_PASSWORD demo-password \
        --es TANDEM_SCREENSHOT_BUDGET "'Demo household'" \
        --es TANDEM_SCREENSHOT_SCREEN "$screen" \
        --es TANDEM_SCREENSHOT_SELECT "'$select'" >/dev/null
      sleep "$wait"
      wait=25
      adb exec-out screencap -p > "$out/$name-$mode-$screen.png"
      echo "captured $name-$mode-$screen"
    done
  done
done
adb shell cmd uimode night no >/dev/null
adb shell wm size reset
adb shell wm density reset
