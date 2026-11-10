#!/usr/bin/env python3
"""Keep mobile UI wording aligned with docs/mobile-ui.md."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOTS = (
    ROOT / "mobile" / "composeApp" / "src" / "commonMain",
    ROOT / "mobile" / "androidApp" / "src" / "main",
    ROOT / "mobile" / "iosApp",
)
RULES = (
    ("say ‘this device’ for local-device status", re.compile(r"\bthis phone\b", re.IGNORECASE)),
    ("use ‘Try again’ for retry actions", re.compile(r"\bretry\b", re.IGNORECASE)),
    ('use "Delete transaction?" for a single transaction', re.compile(r"\bDelete this transaction\?")),
    ('use "Needs review" as the review state label', re.compile(r"\bNeeds your review\b|\bReview changes\b")),
    ('never show a transaction record ID as its name', re.compile(r"Transaction \$\{[^}]*recordId[^}]*\}")),
    ("use the mobile-data setting name", re.compile(r"Use unrestricted networks")),
    ('use "Rejected edit alerts" for phone notifications', re.compile(r"Not applied alerts")),
    ("use sentence case for account creation", re.compile(r"\bAdd Account\b")),
    (
        "use turn on/off wording for notification permissions",
        re.compile(r"\b(?:Enable|Disable) in notification settings\b|\bEnable\b"),
    ),
    ("describe Tandem as a local-first app", re.compile(r"mobile companion|independent, local-first", re.IGNORECASE)),
    ("end a failure message with a next step", re.compile(r"Nothing was changed\.\s*$", re.IGNORECASE)),
    (
        "name the subject of an edit result",
        re.compile(r"^(?:Kept it\b|Cleared the pending deletion\b)", re.IGNORECASE),
    ),
    (
        "avoid internal terms in user-facing text",
        re.compile(r"\b(?:split child|revision|operation|projection)\b", re.IGNORECASE),
    ),
    ("use ‘decimals’ instead of ‘cents’", re.compile(r"\bcents?\b", re.IGNORECASE)),
    (
        "use Needs review, Status unknown, or Rejected for edit states",
        re.compile(r"^(?:Needs confirmation|Still unconfirmed|Change not applied|Not applied)$", re.IGNORECASE),
    ),
    ("avoid ‘conflict’ or ‘uncertain’ in user-facing text", re.compile(r"\b(?:conflict|uncertain)\b", re.IGNORECASE)),
    ('say what is kept in the Actual-version action', re.compile(r"^Keep last synced$", re.IGNORECASE)),
)
PREFERRED_TERMS = (
    "Saved on this device",
    "Try again",
    "Remove edit",
    "Delete transaction?",
    "Needs review",
    "Status unknown",
    "Rejected",
    "Sync on mobile data",
    "Rejected edit alerts",
    "Allow notifications",
    "Turn on in notification settings",
    "Turn off in notification settings",
    "A local-first app for Actual Budget",
    "Essentials",
)


def is_user_facing_literal(literal: str) -> bool:
    """Skip protocol keys and other machine tokens while checking UI copy."""
    return any(character.isspace() for character in literal) or bool(literal[:1].isupper())


def source_string_literals(source: str) -> list[tuple[str, int]]:
    """Return Kotlin and Swift string contents and offsets, ignoring comments."""
    literals: list[tuple[str, int]] = []
    index = 0
    while index < len(source):
        if source.startswith("//", index):
            newline = source.find("\n", index + 2)
            index = len(source) if newline < 0 else newline + 1
            continue
        if source.startswith("/*", index):
            index += 2
            depth = 1
            while index < len(source) and depth:
                if source.startswith("/*", index):
                    depth += 1
                    index += 2
                elif source.startswith("*/", index):
                    depth -= 1
                    index += 2
                else:
                    index += 1
            continue
        if source[index] == "'":
            index += 1
            while index < len(source):
                if source[index] == "\\":
                    index += 2
                elif source[index] == "'":
                    index += 1
                    break
                else:
                    index += 1
            continue
        if source.startswith('"""', index):
            start = index
            content_start = index + 3
            end = source.find('"""', content_start)
            if end < 0:
                break
            literals.append((source[content_start:end], start))
            index = end + 3
            continue
        if source[index] == '"':
            start = index
            index += 1
            content_start = index
            while index < len(source):
                if source[index] == "\\":
                    index += 2
                elif source[index] == '"':
                    literals.append((source[content_start:index], start))
                    index += 1
                    break
                else:
                    index += 1
            continue
        index += 1
    return literals


def source_failures(source: str, relative_path: str) -> list[str]:
    failures: list[str] = []
    interpolation = re.compile(r"\$\{[^{}]*\}|\$[A-Za-z_]\w*")
    for literal, offset in source_string_literals(source):
        literal = interpolation.sub("", literal)
        literal = re.sub(r"\\\([^)]*\)", "", literal)
        if not is_user_facing_literal(literal):
            continue
        line = source.count("\n", 0, offset) + 1
        for message, pattern in RULES:
            if pattern.search(literal):
                failures.append(f"{relative_path}:{line}: {message}")
    return failures


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source-root",
        action="append",
        type=Path,
        help="scan this mobile source root instead of the repository defaults (repeatable)",
    )
    parser.add_argument(
        "--wording-doc",
        type=Path,
        default=ROOT / "docs" / "mobile-ui.md",
        help="wording guide whose preferred terms must be present",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source_roots = args.source_root or SOURCE_ROOTS
    failures: list[str] = []
    source_count = 0

    for source_root in source_roots:
        if not source_root.is_dir():
            print(f"missing mobile source directory: {source_root}", file=sys.stderr)
            return 2
        for path in sorted(source_root.rglob("*")):
            if path.suffix not in {".kt", ".swift"}:
                continue
            if "build" in path.parts or path.name.endswith(("Test.kt", "Tests.swift")):
                continue
            source_count += 1
            content = path.read_text(encoding="utf-8")
            try:
                relative_path = path.relative_to(ROOT).as_posix()
            except ValueError:
                relative_path = path.as_posix()
            failures.extend(source_failures(content, relative_path))

    if not source_count:
        print("no mobile sources found", file=sys.stderr)
        return 2

    if not args.wording_doc.is_file():
        print(f"missing mobile UI wording guide: {args.wording_doc}", file=sys.stderr)
        return 2
    wording = args.wording_doc.read_text(encoding="utf-8")
    for term in PREFERRED_TERMS:
        if term not in wording:
            failures.append(f"{args.wording_doc}: missing preferred term {term!r}")

    if failures:
        print("Mobile UI wording violations:", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        return 1

    print(f"mobile-ui-vocabulary: passed ({source_count} mobile source files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
