#!/usr/bin/env python3
"""Check required iOS and watchOS Info.plist keys before the slower Xcode simulator job."""

import plistlib
from pathlib import Path


ios_app = Path(__file__).resolve().parents[1] / "mobile/iosApp"


def load(relative_path: str) -> dict:
    with (ios_app / relative_path).open("rb") as file:
        return plistlib.load(file)


def require(relative_path: str, key: str, expected: object) -> None:
    actual = load(relative_path).get(key)
    if actual != expected or type(actual) is not type(expected):
        raise SystemExit(f"mobile/iosApp/{relative_path} must set {key} to {expected!r}, found {actual!r}")


require("Info.plist", "CADisableMinimumFrameDurationOnPhone", True)

# The watch app only installs alongside the iPhone app and never runs on its own.
require("Watch/App/Info.plist", "WKApplication", True)
require("Watch/App/Info.plist", "WKCompanionAppBundleIdentifier", "app.tandemforactual.ios")
require("Watch/App/Info.plist", "WKRunsIndependentlyOfCompanionApp", False)

complications = load("Watch/Complications/Info.plist").get("NSExtension", {})
if complications.get("NSExtensionPointIdentifier") != "com.apple.widgetkit-extension":
    raise SystemExit("mobile/iosApp/Watch/Complications/Info.plist must be a WidgetKit extension")

project = (ios_app / "TandemForActual.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
for setting in (
    "PRODUCT_BUNDLE_IDENTIFIER = app.tandemforactual.ios.watchkitapp;",
    "PRODUCT_BUNDLE_IDENTIFIER = app.tandemforactual.ios.watchkitapp.complications;",
    'dstPath = "$(CONTENTS_FOLDER_PATH)/Watch";',
):
    if setting not in project:
        raise SystemExit(f"TandemForActual.xcodeproj must contain {setting}")

print("iOS and watchOS Info.plist files satisfy runtime and packaging requirements")
