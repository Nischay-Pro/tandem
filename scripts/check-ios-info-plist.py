#!/usr/bin/env python3
"""Check required iOS runtime keys before the slower Xcode simulator job."""

import plistlib
from pathlib import Path


info_plist = Path(__file__).resolve().parents[1] / "mobile/iosApp/Info.plist"
with info_plist.open("rb") as file:
    values = plistlib.load(file)

if values.get("CADisableMinimumFrameDurationOnPhone") is not True:
    raise SystemExit(
        "mobile/iosApp/Info.plist must set "
        "CADisableMinimumFrameDurationOnPhone to true"
    )

print("iOS Info.plist satisfies Compose runtime requirements")
