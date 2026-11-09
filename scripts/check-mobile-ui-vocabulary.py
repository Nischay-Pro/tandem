#!/usr/bin/env python3
"""Keep a few repeated mobile UI terms aligned with docs/mobile-ui.md."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOTS = (
    ROOT / "mobile" / "composeApp" / "src" / "commonMain",
    ROOT / "mobile" / "androidApp" / "src" / "main",
)
STRING_LITERAL = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"')
RULES = (
    ("say ‘this device’ for local-device status", re.compile(r"\bthis phone\b", re.IGNORECASE)),
    ("use ‘Try again’ for retry actions", re.compile(r"\bRetry\b")),
    ('use "Delete transaction?" for a single transaction', re.compile(r"\bDelete this transaction\?")),
)
PREFERRED_TERMS = ("Saved on this device", "Try again", "Remove edit", "Delete transaction?")


def main() -> int:
    failures: list[str] = []
    for source_root in SOURCE_ROOTS:
        if not source_root.is_dir():
            print(f"missing mobile source directory: {source_root}", file=sys.stderr)
            return 2
        for path in sorted(source_root.rglob("*.kt")):
            if "build" in path.parts or path.name.endswith("Test.kt"):
                continue
            source = path.read_text(encoding="utf-8")
            for match in STRING_LITERAL.finditer(source):
                literal = match.group()
                line = source.count("\n", 0, match.start()) + 1
                relative_path = path.relative_to(ROOT)
                for message, pattern in RULES:
                    if pattern.search(literal):
                        failures.append(f"{relative_path}:{line}: {message}")

    wording_doc = ROOT / "docs" / "mobile-ui.md"
    wording = wording_doc.read_text(encoding="utf-8")
    for term in PREFERRED_TERMS:
        if term not in wording:
            failures.append(f"{wording_doc.relative_to(ROOT)}: missing preferred term {term!r}")

    if failures:
        print("Mobile UI wording violations:", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        return 1

    print("mobile-ui-vocabulary: passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
