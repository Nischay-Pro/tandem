#!/usr/bin/env sh
set -eu

repo_root=$(git rev-parse --show-toplevel)
git -C "$repo_root" config --local core.hooksPath .githooks
printf '%s\n' 'Installed the repository pre-commit and pre-push hooks.'
