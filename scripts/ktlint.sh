#!/usr/bin/env sh
# Runs the pinned ktlint on Kotlin files without Gradle. Without file arguments it checks all mobile Kotlin, as the
# Gradle ktlintCheck tasks do. The version must match ktlint in mobile/gradle/libs.versions.toml.
set -eu

version=1.8.0
sha256=369ad2b789f95a011f807e1fcb690ccef80bd7cd014fd139e73ae82dcc0baeab
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
if ! grep -qx "ktlint = \"$version\"" mobile/gradle/libs.versions.toml; then
  printf '%s\n' "mobile/gradle/libs.versions.toml doesn't use ktlint $version; update it and this script together." >&2
  exit 1
fi
has_files=false
for argument in "$@"; do
  case $argument in -*) ;; *) has_files=true ;; esac
done
if [ "$has_files" = false ]; then
  set -- "$@" 'mobile/**/src/**/*.kt' 'mobile/**/*.kts' '!**/build/**' '!mobile/settings.gradle.kts'
fi
exec java -jar "$jar" --relative "$@"
