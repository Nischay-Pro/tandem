#!/usr/bin/env sh
# Runs the pinned ktlint on Kotlin files without Gradle. With no file arguments it checks all mobile Kotlin, as the
# Gradle ktlintCheck tasks do. The version must match the ktlint version set in the mobile build files.
set -eu

version=1.5.0
sha256=a27854622198800f50971a049dcdba38f2105d47e7e7258786c7d28045c5735d
url="https://repo1.maven.org/maven2/com/pinterest/ktlint/ktlint-cli/$version/ktlint-cli-$version-all.jar"

cache_dir=${XDG_CACHE_HOME:-$HOME/.cache}/tandem
jar="$cache_dir/ktlint-cli-$version-all.jar"

verify() {
  printf '%s  %s\n' "$sha256" "$1" | sha256sum -c --status
}

if [ ! -f "$jar" ] || ! verify "$jar"; then
  mkdir -p "$cache_dir"
  partial="$jar.partial"
  curl -fsSL --retry 3 -o "$partial" "$url"
  if ! verify "$partial"; then
    rm -f "$partial"
    printf '%s\n' "The downloaded ktlint $version doesn't match its pinned checksum." >&2
    exit 1
  fi
  mv "$partial" "$jar"
fi

cd "$(git rev-parse --show-toplevel)"
for build_file in mobile/api-client/build.gradle.kts mobile/data/build.gradle.kts mobile/composeApp/build.gradle.kts; do
  if ! grep -q "version.set(\"$version\")" "$build_file"; then
    printf '%s\n' "$build_file doesn't use ktlint $version; update both together." >&2
    exit 1
  fi
done
if [ "$#" -eq 0 ]; then
  set -- 'mobile/**/src/**/*.kt' 'mobile/**/*.kts' '!**/build/**'
fi
exec java -jar "$jar" --relative "$@"
