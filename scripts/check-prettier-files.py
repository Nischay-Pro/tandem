#!/usr/bin/env python3
"""Run Prettier on staged or tracked files that it can format."""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


PRETTIER_SUFFIXES = {
    ".cjs",
    ".css",
    ".html",
    ".js",
    ".json",
    ".md",
    ".mjs",
    ".ts",
    ".tsx",
    ".yaml",
    ".yml",
}


def main() -> int:
    if len(sys.argv) not in (2, 3) or (len(sys.argv) == 3 and sys.argv[2] != "--tracked"):
        raise SystemExit("usage: check-prettier-files.py PRETTIER_OR_PNPM [--tracked]")

    tracked = len(sys.argv) == 3
    changed = subprocess.run(
        ["git", "ls-files", "-z"] if tracked else ["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z"],
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    files = [
        os.fsdecode(path)
        for path in changed.split(b"\0")
        if path
        and Path(os.fsdecode(path)).suffix.lower() in PRETTIER_SUFFIXES
        and (not tracked or Path(os.fsdecode(path)).is_file())
    ]
    if not files:
        return 0

    executable = Path(sys.argv[1])
    if executable.name == "pnpm":
        command = [str(executable), "exec", "prettier", "--check", *files]
    else:
        command = [str(executable), "--check", *files]
    return subprocess.run(command, check=False).returncode


if __name__ == "__main__":
    raise SystemExit(main())
