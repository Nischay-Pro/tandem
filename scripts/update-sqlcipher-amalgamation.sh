#!/bin/sh
# Regenerates the vendored SQLCipher amalgamation for iOS. Keep VERSION in step with
# `sqlcipher` in mobile/gradle/libs.versions.toml so Android and iOS share one SQLCipher.
set -eu
VERSION="4.20.0"
SHA256="38705f51b61ac0636a5fd064f9a77fafabc7227b2aa2513f1b14c8bb1102affc"
repo="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
curl -sSfL -o "$work/sqlcipher.tar.gz" "https://github.com/sqlcipher/sqlcipher/archive/refs/tags/v$VERSION.tar.gz"
echo "$SHA256  $work/sqlcipher.tar.gz" | sha256sum -c -
mkdir "$work/src"
tar xzf "$work/sqlcipher.tar.gz" -C "$work/src" --strip-components=1
docker run --rm -v "$work/src":/src -w /src debian:bookworm sh -c '
  apt-get update -qq && apt-get install -y -qq tcl build-essential libssl-dev >/dev/null
  ./configure --with-tempstore=yes CFLAGS="-DSQLITE_HAS_CODEC" LDFLAGS="-lcrypto" >/dev/null
  make sqlite3.c >/dev/null'
gzip -9 -n -c "$work/src/sqlite3.c" > "$repo/mobile/data/src/nativeInterop/sqlcipher/sqlite3.c.gz"
echo "Updated SQLCipher amalgamation to $VERSION"
