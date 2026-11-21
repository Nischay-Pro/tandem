#!/usr/bin/env bash
# Captures Tandem screenshots on a running Pixel Fold emulator using the debug build's screenshot mode.
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
  demo enter
  demo clock -e hhmm 0941
  demo battery -e level 100 -e plugged false
  demo network -e wifi show -e level 4 -e fully true
  demo network -e mobile hide
  demo notifications -e visible false
}

wait=75
for layout in open folded; do
  # Pixel Fold screens: 2208x1840 unfolded and 1080x2092 folded, both at 420 dpi.
  if [ "$layout" = folded ]; then adb shell wm size 1080x2092; else adb shell wm size 2208x1840; fi
  adb shell wm density 420
  sleep 5
  for mode in light dark; do
    if [ "$mode" = dark ]; then adb shell cmd uimode night yes >/dev/null; else adb shell cmd uimode night no >/dev/null; fi
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
      clean_status_bar
      sleep 2
      adb exec-out screencap -p > "$out/pixel-fold-$layout-$mode-$screen.png"
      echo "captured pixel-fold-$layout-$mode-$screen"
    done
  done
done
adb shell cmd uimode night no >/dev/null
adb shell wm size reset
adb shell wm density reset
