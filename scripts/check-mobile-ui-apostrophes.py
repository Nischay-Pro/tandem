#!/usr/bin/env python3
"""Reject straight apostrophes in string literals used by mobile UI sources."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOTS = (
    ROOT / "mobile" / "composeApp" / "src",
    ROOT / "mobile" / "androidApp" / "src",
    ROOT / "mobile" / "iosApp",
)
STRING_LITERAL = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"')
STRAIGHT_APOSTROPHE = re.compile(r"[A-Za-z]'[a-z]")
SOURCE_SUFFIXES = {".kt", ".swift"}


def is_test_source(path: Path) -> bool:
    return path.name.endswith(("Test.kt", "Tests.swift")) or any(part.endswith("Test") for part in path.parts)


def main() -> int:
    failures: list[str] = []
    source_count = 0

    for source_root in SOURCE_ROOTS:
        if not source_root.is_dir():
            print(f"missing mobile source directory: {source_root}", file=sys.stderr)
            return 2

        for path in sorted(source_root.rglob("*")):
            if path.suffix not in SOURCE_SUFFIXES or "build" in path.parts or is_test_source(path):
                continue
            source_count += 1
            content = path.read_text(encoding="utf-8")
            for match in STRING_LITERAL.finditer(content):
                if STRAIGHT_APOSTROPHE.search(match.group()):
                    line = content.count("\n", 0, match.start()) + 1
                    relative_path = path.relative_to(ROOT)
                    failures.append(
                        f"{relative_path}:{line}: use a curly apostrophe (’), or reword the UI text"
                    )

    if not source_count:
        print("no mobile Kotlin sources found", file=sys.stderr)
        return 2

    if failures:
        print("Straight apostrophes found in mobile UI string literals:")
        print("\n".join(failures))
        return 1

    print(f"mobile-ui-apostrophes: passed ({source_count} mobile source files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
