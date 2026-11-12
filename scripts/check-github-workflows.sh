#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.12 .github/workflows/*.yml
