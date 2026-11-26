#!/usr/bin/env bash
set -euo pipefail

changed_files="$(mktemp)"
trap 'rm -f "$changed_files"' EXIT

if ! git diff --name-only --no-renames -z "$1" "$2" -- > "$changed_files" 2>/dev/null; then
  echo true
  exit 0
fi

while IFS= read -r -d '' file; do
  case "$file" in
    mobile/* | .github/workflows/ci.yml | .github/actions/*) echo true; exit 0 ;;
  esac
done < "$changed_files"

echo false
