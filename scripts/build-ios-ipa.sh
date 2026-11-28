#!/usr/bin/env bash
# Builds an unsigned Release .ipa for iOS devices. Install it with a re-signing tool such as AltStore or Sideloadly.
# Usage: build-ios-ipa.sh <output.ipa> <build number> [version, such as 2026.11.28]
set -euo pipefail
output="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
build_number="$2"
version_settings=()
if [[ -n "${3:-}" ]]; then
  version_settings=(MARKETING_VERSION="$3")
fi
derived="${RUNNER_TEMP:-/tmp}/tandem-ios-derived"
staging="$(mktemp -d)"

xcodebuild \
  -project mobile/iosApp/TandemForActual.xcodeproj \
  -scheme TandemForActual \
  -configuration Release \
  -destination "generic/platform=iOS" \
  -derivedDataPath "$derived" \
  CURRENT_PROJECT_VERSION="$build_number" \
  ${version_settings[@]+"${version_settings[@]}"} \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="" \
  build

app="$derived/Build/Products/Release-iphoneos/TandemForActual.app"
test -d "$app"
test -d "$app/PlugIns/TandemShare.appex"
test -d "$app/PlugIns/TandemWidgets.appex"
test -d "$app/Watch/TandemWatch.app"
mkdir -p "$staging/Payload"
cp -R "$app" "$staging/Payload/"
(cd "$staging" && zip -qry "$output" Payload)
rm -rf "$staging"
echo "Built $output"
