"""Rebuild the 15 authored, transparent 16px slot outlines using Python 3 only.

Silhouettes follow the supplied plate, pendant, nameplate, wrap, guard, blade,
link, gear, soul, wings, crown, chest ornament, cape, greaves and boots references.
One-pixel strokes and a single neutral gray avoid texture noise at native size.
No Curios assets are copied. Run from any directory; output paths are repo-local.
"""
from pathlib import Path
import struct
import zlib

# Each polyline is expressed directly on the final pixel grid; no downsampling.
STROKES = {
    'armor_decoration': [
        [(3, 2), (8, 2), (10, 4), (8, 10), (4, 11), (2, 9), (2, 4), (3, 2)],
        [(11, 6), (13, 7), (13, 11), (10, 14), (7, 13), (7, 12)],
        [(4, 4)], [(4, 8)], [(8, 4)], [(11, 9)],
    ],
    'pendant': [
        [(7, 1), (8, 1), (9, 2), (9, 3), (8, 4), (7, 4), (6, 3), (6, 2), (7, 1)],
        [(7, 5), (3, 9), (7, 13), (8, 13), (12, 9), (8, 5)],
        [(7, 8), (8, 8), (8, 10), (7, 10), (7, 8)], [(7, 14), (8, 14)],
    ],
    'nameplate': [
        [(3, 4), (12, 4), (14, 6), (14, 10), (12, 12), (3, 12), (1, 10), (1, 6), (3, 4)],
        [(3, 7), (3, 9)], [(6, 7), (11, 7)], [(6, 9), (9, 9)],
    ],
    'grip_wrap': [
        [(2, 10), (10, 2), (12, 2), (14, 4), (14, 6), (6, 14), (4, 14), (2, 12), (2, 10)],
        [(3, 9), (7, 13)], [(5, 7), (9, 11)], [(7, 5), (11, 9)], [(9, 3), (13, 7)],
    ],
    'guard': [
        [(1, 6), (1, 9), (4, 10), (6, 9)], [(14, 6), (14, 9), (11, 10), (9, 9)],
        [(2, 6), (4, 7), (5, 7)], [(13, 6), (11, 7), (10, 7)],
        [(7, 3), (8, 3), (10, 6), (8, 12), (7, 12), (5, 6), (7, 3)], [(7, 6), (8, 6)],
    ],
    'blade': [
        [(14, 1), (13, 7), (8, 12), (4, 8), (9, 3), (14, 1)],
        [(11, 4), (7, 8)], [(3, 7), (9, 13)],
        [(4, 10), (1, 13), (2, 14), (5, 11)],
    ],
    'fitting': [
        [(9, 1), (12, 1), (14, 3), (14, 6), (11, 9)],
        [(9, 1), (6, 4), (6, 7)], [(9, 4), (11, 3), (12, 4), (10, 6)],
        [(4, 6), (1, 9), (1, 12), (3, 14), (6, 14), (9, 11), (9, 8)],
        [(4, 9), (3, 11), (4, 12), (6, 10)], [(6, 9), (9, 6)],
    ],
    'tool_module': [
        [(6, 1), (9, 1), (9, 3), (11, 4), (13, 3), (14, 6), (12, 7), (12, 9), (14, 10), (13, 12), (11, 11), (9, 12), (9, 14), (6, 14), (6, 12), (4, 11), (2, 12), (1, 10), (3, 9), (3, 7), (1, 6), (2, 3), (4, 4), (6, 3), (6, 1)],
        [(6, 6), (9, 6), (10, 7), (10, 9), (9, 10), (6, 10), (5, 9), (5, 7), (6, 6)],
    ],
    'soul': [
        [(8, 1), (6, 4), (8, 5), (9, 4), (8, 1)],
        [(7, 7), (10, 9), (8, 12), (5, 10), (7, 7)],
        [(11, 5), (13, 7), (13, 11), (10, 14), (7, 14)],
        [(4, 5), (2, 7), (2, 11), (4, 13)], [(4, 8), (4, 9)],
    ],
    'wing': [
        [(1, 2), (5, 5), (6, 8), (5, 12), (3, 10), (1, 2)],
        [(14, 2), (10, 5), (9, 8), (10, 12), (12, 10), (14, 2)],
        [(2, 5), (4, 7)], [(13, 5), (11, 7)],
        [(3, 8), (4, 10)], [(12, 8), (11, 10)],
        [(7, 10), (8, 10), (8, 13), (7, 13), (7, 10)],
    ],
    'head': [
        [(1, 4), (4, 7), (7, 2), (8, 2), (11, 7), (14, 4), (12, 12), (3, 12), (1, 4)],
        [(3, 10), (12, 10)], [(7, 6), (8, 6), (8, 8), (7, 8), (7, 6)],
    ],
    'chest': [
        [(1, 4), (3, 8), (6, 10)], [(14, 4), (12, 8), (9, 10)],
        [(1, 4), (4, 5), (6, 7)], [(14, 4), (11, 5), (9, 7)],
        [(7, 5), (8, 5), (10, 8), (8, 11), (7, 11), (5, 8), (7, 5)],
        [(7, 2), (8, 3)], [(4, 10), (4, 12)], [(11, 10), (11, 12)], [(7, 13), (8, 13)],
    ],
    'back': [
        [(4, 2), (6, 3), (9, 3), (11, 2), (12, 7), (14, 13), (10, 12), (8, 14), (7, 14), (5, 12), (1, 13), (3, 7), (4, 2)],
        [(5, 5), (4, 10)], [(10, 5), (11, 10)], [(7, 5), (8, 5)],
    ],
    'leg': [
        [(2, 2), (6, 2), (5, 6), (5, 12), (4, 14), (3, 12), (3, 6), (2, 2)],
        [(9, 2), (13, 2), (12, 6), (12, 12), (11, 14), (10, 12), (10, 6), (9, 2)],
        [(3, 5), (5, 5)], [(10, 5), (12, 5)], [(4, 8)], [(11, 8)],
    ],
    'boot': [
        [(3, 2), (6, 2), (6, 12), (5, 13), (1, 13), (1, 11), (3, 10), (3, 2)],
        [(9, 2), (12, 2), (12, 10), (14, 11), (14, 13), (10, 13), (9, 12), (9, 2)],
        [(3, 5), (6, 5)], [(9, 5), (12, 5)], [(4, 8), (5, 8)], [(10, 8), (11, 8)],
    ],
}


def line(pixels, a, b):
    x, y = a
    dx, dy = abs(b[0] - x), -abs(b[1] - y)
    sx, sy = 1 if x < b[0] else -1, 1 if y < b[1] else -1
    error = dx + dy
    while True:
        pixels.add((x, y))
        if (x, y) == b:
            return
        double = 2 * error
        if double >= dy:
            error += dy
            x += sx
        if double <= dx:
            error += dx
            y += sy


def chunk(kind, data):
    return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data))


def render(output):
    output.mkdir(parents=True, exist_ok=True)
    for name, strokes in STROKES.items():
        pixels = set()
        for stroke in strokes:
            for a, b in zip(stroke, stroke[1:] or stroke):
                line(pixels, a, b)
        assert all(1 <= x <= 14 and 1 <= y <= 14 for x, y in pixels), name
        raw = b''.join(b'\x00' + b''.join(bytes((140, 140, 140, 255)) if (x, y) in pixels
                else bytes(4) for x in range(16)) for y in range(16))
        png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', 16, 16, 8, 6, 0, 0, 0))
        (output / (name + '.png')).write_bytes(png + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


if __name__ == '__main__':
    render(Path(__file__).resolve().parents[1] / 'src/main/resources/assets/equipment_structure_api/textures/gui/sprites/slot')
