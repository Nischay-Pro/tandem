#!/usr/bin/env bash
# Captures Tandem screenshots on the running emulator using the debug build's screenshot mode.
# Usage: capture-android.sh <apk> <output dir> <phone|foldable-1-1|foldable-4-3|tablet> [server url]
set -euo pipefail
apk="$1"
out="$2"
set_name="$3"
server="${4:-http://10.0.2.2:19753}"
package=app.tandemforactual.android.localtest
mkdir -p "$out"

adb install -r "$apk" >/dev/null
adb shell settings put global sysui_demo_allowed 1
adb shell settings put system fold_lock_behavior_setting stay_awake_on_fold_key || true
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

# Foldable profiles list the inner display first and the outer display second.
capture() {
  local file="$1" display_line="$2" id
  id="$(adb shell dumpsys SurfaceFlinger --display-id | awk -v n="$display_line" 'NR==n {print $2}' | tr -d '\r')"
  if [ -n "$id" ]; then
    adb exec-out screencap -d "$id" -p > "$file"
  else
    adb exec-out screencap -p > "$file"
  fi
}

# Locks the current display to landscape, or to portrait with "portrait", whatever its natural orientation.
orient() {
  local size width height
  size="$(adb shell wm size | awk -F': ' '/Physical size/ {print $2}' | tr -d '\r')"
  width="${size%x*}"
  height="${size#*x}"
  if { [ "$1" = landscape ] && [ "$width" -ge "$height" ]; } || { [ "$1" = portrait ] && [ "$width" -lt "$height" ]; }; then
    adb shell wm user-rotation lock 0
  else
    adb shell wm user-rotation lock 1
  fi
}

# Scrolls the open screen by most of its height; the short side is used so it works in either orientation.
scroll_down() {
  local size short
  size="$(adb shell wm size | awk -F': ' '/Physical size/ {print $2}' | tr -d '\r')"
  short="${size%x*}"
  [ "${size#*x}" -lt "$short" ] && short="${size#*x}"
  adb shell input swipe $((short / 2)) $((short * 8 / 10)) $((short / 2)) $((short / 10)) 400
  sleep 3
}

wait=75
shoot() {
  local name="$1" display_line="$2"
  sleep 5
  for mode in light dark; do
    if [ "$mode" = dark ]; then adb shell cmd uimode night yes >/dev/null; else adb shell cmd uimode night no >/dev/null; fi
    clean_status_bar
    for spec in "budget:" "activity:Corner Market" "accounts:Everyday checking" "reports:" "category:Phone"; do
      screen="${spec%%:*}"
      select="${spec#*:}"
      adb shell am force-stop "$package"
      adb shell input keyevent KEYCODE_WAKEUP
      adb shell wm dismiss-keyguard
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
      capture "$out/$name-$mode-$screen.png" "$display_line"
      echo "captured $name-$mode-$screen"
      if [ "$screen" = budget ]; then
        scroll_down
        capture "$out/grid-$name-$mode.png" "$display_line"
      fi
    done
  done
}

case "$set_name" in
  phone)
    shoot phone 1
    ;;
  foldable-1-1 | foldable-4-3)
    adb emu unfold || true
    sleep 5
    orient landscape
    shoot "$set_name-inner" 1
    adb emu fold || true
    sleep 5
    orient portrait
    shoot "$set_name-outer" 2
    adb emu unfold || true
    ;;
  tablet)
    orient landscape
    shoot tablet 1
    ;;
  *)
    echo "Unknown screenshot set: $set_name" >&2
    exit 2
    ;;
esac
adb shell cmd uimode night no >/dev/null
