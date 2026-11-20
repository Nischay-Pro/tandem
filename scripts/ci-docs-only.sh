#!/usr/bin/env bash
set -euo pipefail

changed_files="$(mktemp)"
trap 'rm -f "$changed_files"' EXIT

# Count both paths in a rename so renaming a source file to Markdown runs full CI.
if ! git diff --name-only --no-renames -z "$1" "$2" -- > "$changed_files"; then
  echo false
  exit 0
fi

docs_only=false
while IFS= read -r -d '' file; do
  case "$file" in
    *.md) docs_only=true ;;
    *) echo false; exit 0 ;;
  esac
done < "$changed_files"

echo "$docs_only"
