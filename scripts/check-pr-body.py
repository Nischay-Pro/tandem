#!/usr/bin/env python3
"""Require pull request descriptions that follow docs/develop/pull-requests.md."""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / ".github" / "pull_request_template.md"
REQUIRED_SECTIONS = ("Summary", "Testing")

COMMENT = re.compile(r"<!--.*?(?:-->|\Z)", re.DOTALL)
HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")
FENCE = re.compile(r"^\s*(```|~~~)")
EMPTY_LINE = re.compile(r"^\s*(?:[-*+]|\d+\.)?\s*(?:\[[ xX]?\])?\s*$")
ATTRIBUTION = (
    re.compile(r"^\W*generated (?:with|by|using)\b", re.IGNORECASE | re.MULTILINE),
    re.compile(r"^\s*co-authored-by:", re.IGNORECASE | re.MULTILINE),
    re.compile(
        r"\b(?:claude|anthropic|chatgpt|openai|gpt-?\d\w*|copilot|gemini|codex|"
        r"devin|windsurf|llm|large language model|ai[- ]assistant|ai[- ]generated)\b",
        re.IGNORECASE,
    ),
    re.compile("\N{ROBOT FACE}"),
)


def template_hints() -> set[str]:
    try:
        text = TEMPLATE.read_text(encoding="utf-8")
    except OSError:
        return set()
    hints = set()
    for comment in re.findall(r"<!--(.*?)-->", text, re.DOTALL):
        for line in comment.splitlines():
            line = line.strip()
            if len(line) >= 12:
                hints.add(line)
    return hints


def sections(body: str) -> dict[str, list[str]]:
    found: dict[str, list[str]] = {}
    current: list[str] | None = None
    in_fence = False
    for line in body.splitlines():
        if FENCE.match(line):
            in_fence = not in_fence
        heading = None if in_fence else HEADING.match(line)
        if heading and len(heading.group(1)) <= 2:
            current = found.setdefault(heading.group(2).strip().lower(), [])
            continue
        if current is not None:
            current.append(line)
    return found


def problems(body: str) -> list[str]:
    body = body.replace("\r\n", "\n")
    errors = []
    for pattern in ATTRIBUTION:
        match = pattern.search(body)
        if match:
            errors.append(
                f"Remove tool attribution ({match.group(0).strip()!r}); "
                "the author is responsible for the change."
            )
            break

    visible = COMMENT.sub("", body)
    found = sections(visible)
    hints = template_hints()
    for name in REQUIRED_SECTIONS:
        lines = found.get(name.lower())
        if lines is None:
            errors.append(f"Add a '## {name}' section.")
            continue
        content = [line for line in lines if not EMPTY_LINE.match(line)]
        if not content:
            errors.append(f"Fill in the '## {name}' section.")
        elif any(line.strip() in hints for line in content):
            errors.append(
                f"Replace the template hints in '## {name}' with your own text."
            )
    return errors


def main() -> int:
    if len(sys.argv) > 2:
        print("usage: check-pr-body.py [file | -]  (default: $PR_BODY)", file=sys.stderr)
        return 2
    if len(sys.argv) == 2:
        source = sys.argv[1]
        if source == "-":
            body = sys.stdin.read()
        else:
            body = Path(source).read_text(encoding="utf-8")
    else:
        body = os.environ.get("PR_BODY", "")

    errors = problems(body)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        print("See docs/develop/pull-requests.md.", file=sys.stderr)
        return 1

    print("PR description follows the pull request guidelines.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
