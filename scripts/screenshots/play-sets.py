#!/usr/bin/env python3
"""Sorts captured screenshots into the folders Google Play's listing asks for, at the exact ratios it requires."""

import shutil
import struct
import subprocess
import sys
from pathlib import Path

# Prefix of the captured file, Play folders it belongs in, and the exact width:height Play requires (None: any allowed ratio).
SETS = [
    ("phone-", ["phone"], None),
    ("foldable-1-1-outer-", ["phone"], None),
    ("foldable-1-1-inner-", ["foldable"], (1, 1)),
    ("foldable-4-3-inner-", ["foldable"], (4, 3)),
    ("foldable-4-3-outer-", ["foldable"], (10, 16)),
    ("tablet-", ["tablet-7-inch", "tablet-10-inch"], None),
]


def size(png: Path) -> tuple[int, int]:
    with png.open("rb") as f:
        header = f.read(24)
    return struct.unpack(">II", header[16:24])


def exact(width: int, height: int, ratio: tuple[int, int]) -> tuple[int, int]:
    rw, rh = ratio
    if width * rh > height * rw:
        return height * rw // rh, height
    return width, width * rh // rw


def crop_command(png: Path, out: Path, width: int, height: int) -> list[str]:
    if shutil.which("magick"):
        return ["magick", str(png), "-gravity", "center", "-crop", f"{width}x{height}+0+0", "+repage", str(out)]
    if shutil.which("convert"):
        return ["convert", str(png), "-gravity", "center", "-crop", f"{width}x{height}+0+0", "+repage", str(out)]
    if shutil.which("ffmpeg"):
        return ["ffmpeg", "-loglevel", "error", "-y", "-i", str(png), "-vf", f"crop={width}:{height}", str(out)]
    sys.exit("Cropping needs ImageMagick or ffmpeg on the PATH.")


def main(captures: Path, play: Path) -> None:
    lines = ["| Screenshot | Captured | In the listing set |", "| --- | --- | --- |"]
    for png in sorted(captures.glob("*.png")):
        match = next((s for s in SETS if png.name.startswith(s[0])), None)
        if match is None:
            continue
        _, folders, ratio = match
        width, height = size(png)
        target = exact(width, height, ratio) if ratio else (width, height)
        for folder in folders:
            out = play / folder / png.name
            out.parent.mkdir(parents=True, exist_ok=True)
            if target == (width, height):
                shutil.copyfile(png, out)
            else:
                subprocess.run(crop_command(png, out, *target), check=True)
        note = f"{target[0]}x{target[1]}" + (" (cropped)" if target != (width, height) else "")
        lines.append(f"| {png.name} | {width}x{height} | {', '.join(folders)} at {note} |")
    print("\n".join(lines))


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
