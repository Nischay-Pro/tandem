#!/usr/bin/env bash
set -euo pipefail

script="$(cd "$(dirname "$0")" && pwd)/ci-ios-needed.sh"
repo="$(mktemp -d)"
trap 'rm -rf "$repo"' EXIT
cd "$repo"
git init -q
git -c user.name=ci -c user.email=ci@example.invalid commit -q --allow-empty -m base
base="$(git rev-parse HEAD)"

change() {
  mkdir -p "$(dirname "$1")"
  echo "$RANDOM" >> "$1"
  git add -A
  git -c user.name=ci -c user.email=ci@example.invalid commit -q -m "change $1"
  git rev-parse HEAD
}

expect() {
  local actual
  actual="$(bash "$script" "$1" "$2")"
  if [[ "$actual" != "$3" ]]; then
    echo "expected $3 for $4, got $actual" >&2
    exit 1
  fi
}

services="$(change services/api/src/main.ts)"
expect "$base" "$services" false "a service-only change"
deploy="$(change deploy/tandem-setup.sh)"
expect "$base" "$deploy" false "service and deploy changes"
mobile="$(change mobile/data/src/A.kt)"
expect "$deploy" "$mobile" true "a mobile change"
workflow="$(change .github/workflows/ci.yml)"
expect "$mobile" "$workflow" true "a CI workflow change"
action="$(change .github/actions/setup-mobile-build/action.yml)"
expect "$workflow" "$action" true "a build action change"
expect 0000000000000000000000000000000000000000 "$action" true "an unknown base"
echo "ci-ios-needed: all cases pass"
