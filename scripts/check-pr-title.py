#!/usr/bin/env python3
"""Require pull request titles that produce clean Conventional Commit subjects."""

from __future__ import annotations

import re
import sys


CONVENTIONAL_TITLE = re.compile(
    r"^[a-z][a-z0-9-]*(?:\([a-z0-9][a-z0-9._/-]*\))?!?: \S(?:.*\S)?$"
)
PR_NUMBER_SUFFIX = re.compile(r" \(#\d+\)$")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: check-pr-title.py <title>", file=sys.stderr)
        return 2

    title = sys.argv[1]
    if not CONVENTIONAL_TITLE.fullmatch(title):
        print(
            "PR title must use Conventional Commit format, for example "
            "'feat: add budget privacy mode'.",
            file=sys.stderr,
        )
        return 1
    if PR_NUMBER_SUFFIX.search(title):
        print(
            "Do not add a '(#number)' suffix; GitHub adds the PR number to the squash commit.",
            file=sys.stderr,
        )
        return 1

    print("PR title follows Conventional Commit format.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
