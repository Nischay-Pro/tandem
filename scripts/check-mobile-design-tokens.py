#!/usr/bin/env python3
"""Keep raw typography, shape radii, and colors in the mobile theme."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
UI_ROOT = ROOT / "mobile/composeApp/src/commonMain/kotlin/app/tandemforactual/ui"
THEME_FILE = UI_ROOT / "TandemTheme.kt"
RULES = {
    "numeric fontSize; use TandemTypography": re.compile(r"\bfontSize\s*=\s*\d+(?:\.\d+)?\.sp\b"),
    "numeric RoundedCornerShape; use TandemShapes": re.compile(
        r"\bRoundedCornerShape\s*\(\s*\d+(?:\.\d+)?\.dp\s*\)"
    ),
    "hard-coded Color; use TandemPalette": re.compile(r"\bColor\s*\(\s*0x[0-9A-Fa-f]{6,8}\s*\)"),
}
DIALOG_CALL = re.compile(r"\b(?:AlertDialog|Dialog)\s*\(")


def main() -> int:
    violations = []
    for source in sorted(UI_ROOT.rglob("*.kt")):
        if source == THEME_FILE:
            continue
        content = source.read_text(encoding="utf-8")
        for line_number, line in enumerate(content.splitlines(), start=1):
            for rule, pattern in RULES.items():
                if pattern.search(line):
                    relative = source.relative_to(ROOT)
                    violations.append(f"{relative}:{line_number}: {rule}")
        if UI_ROOT / "components" not in source.parents:
            for match in DIALOG_CALL.finditer(content):
                relative = source.relative_to(ROOT)
                line_number = content.count("\n", 0, match.start()) + 1
                violations.append(f"{relative}:{line_number}: raw dialog; use a shared ui/components wrapper")

    if violations:
        print("Mobile UI consistency violations:", file=sys.stderr)
        print("\n".join(violations), file=sys.stderr)
        return 1

    print("Mobile design tokens are used outside TandemTheme.kt.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
