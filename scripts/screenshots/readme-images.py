#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow>=10"]
# ///
"""Builds the README screenshots in docs/images/screenshots/ from a downloaded App screenshots run.

Usage: uv run scripts/screenshots/readme-images.py <artifacts dir> [output dir]
"""

import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

# Output name: (captures left to right, screen height, corner radius, outer margin, gap between screens).
IMAGES = {
    "hero": (["iphone-17-{mode}-budget", "phone-{mode}-activity", "iphone-17-{mode}-reports", "phone-{mode}-category"], 900, 26, 45, 48),
    "foldable-two-pane": (["foldable-1-1-inner-{mode}-activity"], 760, 40, 38, 0),
    "ipad-accounts": (["ipad-{mode}-accounts"], 760, 32, 38, 0),
    "category-insights": (["iphone-17-{mode}-category"], 900, 26, 45, 0),
}
SHADOW_OFFSET = 10
SHADOW_BLUR = 14
SHADOW_OPACITY = 0.3
QUALITY = 88


def find(captures: Path, stem: str) -> Path:
    matches = sorted(p for p in captures.rglob(f"{stem}.png") if "play" not in p.parts)
    if not matches:
        sys.exit(f"Missing capture {stem}.png in {captures}")
    return matches[0]


def rounded(shot: Image.Image, radius: int) -> Image.Image:
    scale = 4
    mask = Image.new("L", (shot.width * scale, shot.height * scale), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, mask.width - 1, mask.height - 1), radius * scale, fill=255)
    shot = shot.convert("RGBA")
    shot.putalpha(ImageChops.multiply(shot.getchannel("A"), mask.resize(shot.size, Image.LANCZOS)))
    return shot


def compose(paths: list[Path], height: int, radius: int, margin: int, gap: int) -> Image.Image:
    shots = []
    for path in paths:
        with Image.open(path) as raw:
            shots.append(rounded(raw.resize((round(raw.width * height / raw.height), height), Image.LANCZOS), radius))
    width = 2 * margin + sum(s.width for s in shots) + gap * (len(shots) - 1)
    canvas = Image.new("RGBA", (width, height + 2 * margin), (0, 0, 0, 0))
    shadow = Image.new("L", canvas.size, 0)
    x = margin
    for shot in shots:
        shadow.paste(shot.getchannel("A").point(lambda a: round(a * SHADOW_OPACITY)), (x, margin + SHADOW_OFFSET))
        x += shot.width + gap
    canvas.putalpha(shadow.filter(ImageFilter.GaussianBlur(SHADOW_BLUR)))
    x = margin
    for shot in shots:
        canvas.alpha_composite(shot, (x, margin))
        x += shot.width + gap
    return canvas


def main() -> None:
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__.strip().splitlines()[-1])
    captures = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) == 3 else Path(__file__).resolve().parents[2] / "docs/images/screenshots"
    out.mkdir(parents=True, exist_ok=True)
    for name, (stems, height, radius, margin, gap) in IMAGES.items():
        for mode in ("light", "dark"):
            paths = [find(captures, stem.format(mode=mode)) for stem in stems]
            image = compose(paths, height, radius, margin, gap)
            target = out / f"{name}-{mode}.webp"
            image.save(target, "WEBP", quality=QUALITY, alpha_quality=90, method=6)
            print(f"{target.name}: {image.width}x{image.height} from {', '.join(p.name for p in paths)}")


if __name__ == "__main__":
    main()
