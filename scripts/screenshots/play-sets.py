#!/usr/bin/env python3
"""Sorts captured screenshots into the folders Google Play's listing asks for, at the exact ratios it requires."""

import shutil
import struct
import sys
import zlib
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


def _chunks(data: bytes):
    pos = 8
    while pos < len(data):
        length = struct.unpack(">I", data[pos : pos + 4])[0]
        yield data[pos + 4 : pos + 8], data[pos + 8 : pos + 8 + length]
        pos += 12 + length


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    return a if pa <= pb and pa <= pc else b if pb <= pc else c


def crop(png: Path, out: Path, width: int, height: int) -> None:
    """Centre-crops an 8-bit, non-interlaced RGB or RGBA PNG without any image library."""
    data = png.read_bytes()
    header = next(body for kind, body in _chunks(data) if kind == b"IHDR")
    full_w, full_h, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", header)
    if depth != 8 or colour not in (2, 6) or interlace:
        sys.exit(f"{png.name}: only 8-bit RGB/RGBA non-interlaced PNGs can be cropped")
    bpp = 4 if colour == 6 else 3
    raw = zlib.decompress(b"".join(body for kind, body in _chunks(data) if kind == b"IDAT"))
    stride = full_w * bpp
    rows, previous = [], bytearray(stride)
    for y in range(full_h):
        kind, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1 : (y + 1) * (stride + 1)])
        for i in range(stride):
            left = line[i - bpp] if i >= bpp else 0
            up = previous[i]
            upper_left = previous[i - bpp] if i >= bpp else 0
            if kind == 1:
                line[i] = (line[i] + left) & 0xFF
            elif kind == 2:
                line[i] = (line[i] + up) & 0xFF
            elif kind == 3:
                line[i] = (line[i] + (left + up) // 2) & 0xFF
            elif kind == 4:
                line[i] = (line[i] + _paeth(left, up, upper_left)) & 0xFF
        rows.append(line)
        previous = line
    x0, y0 = (full_w - width) // 2 * bpp, (full_h - height) // 2
    body = b"".join(b"\x00" + bytes(row[x0 : x0 + width * bpp]) for row in rows[y0 : y0 + height])

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)

    out.write_bytes(
        data[:8]
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, colour, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(body, 9))
        + chunk(b"IEND", b""),
    )


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
                crop(png, out, *target)
        note = f"{target[0]}x{target[1]}" + (" (cropped)" if target != (width, height) else "")
        lines.append(f"| {png.name} | {width}x{height} | {', '.join(folders)} at {note} |")
    print("\n".join(lines))


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
