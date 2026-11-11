#!/usr/bin/env python3
"""Keep mobile typography, colors, shapes, and list-row geometry on theme tokens."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
UI_ROOT = ROOT / "mobile/composeApp/src/commonMain/kotlin/app/tandemforactual/ui"
THEME_FILE = UI_ROOT / "TandemTheme.kt"
RULES = {
    "numeric fontSize; use TandemTypography": re.compile(r"\bfontSize\s*=\s*\d+(?:\.\d+)?\.sp\b"),
    "fixed list row height; use TandemLayout": re.compile(
        r"\b(?:heightIn|defaultMinSize)\s*\(\s*min(?:Height)?\s*=\s*64\.dp\b"
    ),
    "numeric RoundedCornerShape; use TandemShapes": re.compile(
        r"\bRoundedCornerShape\s*\(\s*\d+(?:\.\d+)?\.dp\s*\)"
    ),
    "hard-coded Color; use TandemPalette": re.compile(r"\bColor\s*\(\s*0x[0-9A-Fa-f]{6,8}\s*\)"),
}
DIALOG_CALL = re.compile(r"\b(?:AlertDialog|Dialog)\s*\(")
RAW_SETTINGS_SPACING = re.compile(r"\.(?:padding|spacedBy)\([^)]*\b\d+(?:\.\d+)?\.dp\b", re.DOTALL)
SHARED_SETTINGS_SPACING_FILES = (
    UI_ROOT / "screens/SettingsScreen.kt",
    UI_ROOT / "templates/SettingsRowTemplates.kt",
    UI_ROOT / "components/Rows.kt",
)
LAYOUT_CONTRACTS = {
    UI_ROOT / "TandemTheme.kt": (
        re.compile(r"listRowMinHeight\s*=\s*64\.dp"),
        re.compile(r"compactListRowMinHeight\s*=\s*56\.dp"),
        re.compile(r"listRowHorizontalInset\s*=\s*TandemSpacing\.large"),
        re.compile(r"listRowVerticalInset\s*=\s*TandemSpacing\.small"),
    ),
    UI_ROOT / "components/Rows.kt": (
        re.compile(r"defaultMinSize\(minHeight\s*=\s*rowMinHeight\)"),
        re.compile(r"horizontal\s*=\s*TandemLayout\.listRowHorizontalInset"),
        re.compile(r"TandemLayout\.listRowVerticalInset"),
        re.compile(r"TandemLayout\.compactListRowVerticalInset"),
        re.compile(r"Arrangement\.spacedBy\(TandemLayout\.listRowGap\)"),
    ),
    UI_ROOT / "screens/SettingsScreen.kt": (
        re.compile(r"heightIn\(min\s*=\s*TandemLayout\.listRowMinHeight\)"),
        re.compile(r"horizontal\s*=\s*TandemLayout\.listRowHorizontalInset"),
        re.compile(r"vertical\s*=\s*TandemLayout\.listRowVerticalInset"),
        re.compile(r"Arrangement\.spacedBy\(TandemLayout\.listRowGap\)"),
    ),
    UI_ROOT / "templates/SettingsRowTemplates.kt": (
        re.compile(r"defaultMinSize\(minHeight\s*=\s*TandemLayout\.listRowMinHeight\)"),
        re.compile(r"horizontal\s*=\s*TandemLayout\.listRowHorizontalInset"),
        re.compile(r"vertical\s*=\s*TandemLayout\.listRowVerticalInset"),
        re.compile(r"Arrangement\.spacedBy\(TandemLayout\.listRowGap\)"),
    ),
    UI_ROOT / "screens/AccountListScreens.kt": (
        re.compile(r"defaultMinSize\(minHeight\s*=\s*TandemLayout\.listRowMinHeight\)"),
        re.compile(r"horizontal\s*=\s*TandemLayout\.listRowHorizontalInset"),
        re.compile(r"vertical\s*=\s*TandemLayout\.listRowVerticalInset"),
        re.compile(r"Arrangement\.spacedBy\(TandemLayout\.listRowGap\)"),
    ),
    UI_ROOT / "screens/CategoryDetailScreen.kt": (
        re.compile(r"heightIn\(min\s*=\s*TandemLayout\.listRowMinHeight\)"),
        re.compile(r"horizontal\s*=\s*TandemLayout\.listRowHorizontalInset"),
        re.compile(r"vertical\s*=\s*TandemLayout\.listRowVerticalInset"),
        re.compile(r"Arrangement\.spacedBy\(TandemLayout\.listRowGap\)"),
        re.compile(r"size\(TandemLayout\.listRowLeadingSize\)"),
        re.compile(r"size\(TandemLayout\.listRowIconSize\)"),
    ),
}


def main() -> int:
    violations = []
    for source, patterns in LAYOUT_CONTRACTS.items():
        content = source.read_text(encoding="utf-8")
        for pattern in patterns:
            if not pattern.search(content):
                relative = source.relative_to(ROOT)
                violations.append(f"{relative}: shared row layout must use TandemLayout tokens")

    for source in SHARED_SETTINGS_SPACING_FILES:
        content = source.read_text(encoding="utf-8")
        for match in RAW_SETTINGS_SPACING.finditer(content):
            relative = source.relative_to(ROOT)
            line_number = content.count("\n", 0, match.start()) + 1
            violations.append(f"{relative}:{line_number}: raw settings/list spacing; use TandemSpacing or TandemLayout")

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

    print("Mobile design tokens and shared row layout are consistent.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
