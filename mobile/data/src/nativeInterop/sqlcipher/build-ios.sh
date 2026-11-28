#!/bin/sh
# Builds the private SQLCipher library for one iOS SDK: build-ios.sh <sdk> <target-triple> <output-dir>
set -eu
# Archives without timestamps, so the same sources make the same library and Gradle can reuse what depends on it.
export ZERO_AR_DATE=1
sdk="$1"
target="$2"
out="$3"
here="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$out"
gunzip -c "$here/sqlite3.c.gz" > "$out/sqlite3.c"
xcrun --sdk "$sdk" clang -c -O2 -target "$target" -DNDEBUG -DSQLCIPHER_CRYPTO_CC \
  -I "$out" -I "$here" "$here/tandem_sqlcipher.c" -o "$out/tandem_sqlcipher.o"
rm -f "$out/libtandemsqlcipher.a"
xcrun --sdk "$sdk" libtool -static -o "$out/libtandemsqlcipher.a" "$out/tandem_sqlcipher.o"
