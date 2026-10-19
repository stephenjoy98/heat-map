"""Generates the map-type preview images for the layers sheet (Default and Terrain)."""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 384  # rendered size; drawn at 64 dp, so this stays sharp up to 6x
out = sys.argv[1]
rng = np.random.default_rng(7)

def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

# ---------- Default: classic OpenStreetMap-style street map ----------
img = Image.new('RGB', (S, S), hexc('#f2efe9'))
d = ImageDraw.Draw(img)
# Park
d.polygon([(30, 230), (150, 205), (175, 330), (40, 360)], fill=hexc('#c8facc'))
# Building blocks
# Blocks sit between the roads (which run along 32 + 64k); two buildings per block.
for x in range(-96, S, 64):
    for y in range(-96, S, 64):
        if ((x + 32) // 64 * 7 + (y + 32) // 64 * 3) % 5 == 0:
            continue
        # Block interior runs from x + 6 to x + 58 (roads at x and x + 64, with x = 32 + 64k).
        bx, by = x + 64, y + 64
        if bx < 140 and by > 190:
            continue  # leave the park open
        d.rectangle([bx + 10, by + 10, bx + 32, by + 52], fill=hexc('#d9d0c9'), outline=hexc('#c9bfb6'))
        d.rectangle([bx + 36, by + 10, bx + 54, by + 28], fill=hexc('#d9d0c9'), outline=hexc('#c9bfb6'))
# River
river = [(S, 40), (300, 70), (250, 120), (230, 180), (260, 250), (240, 320), (200, S)]
d.line(river, fill=hexc('#aad3df'), width=26, joint='curve')
# Minor roads (grey casing, white fill)
for v in range(32, S, 64):
    d.line([(v, 0), (v, S)], fill=hexc('#c4bdb4'), width=11)
    d.line([(0, v), (S, v)], fill=hexc('#c4bdb4'), width=11)
for v in range(32, S, 64):
    d.line([(v, 0), (v, S)], fill=(255, 255, 255), width=7)
    d.line([(0, v), (S, v)], fill=(255, 255, 255), width=7)
# Main road (orange) on a diagonal
d.line([(0, 330), (S, 60)], fill=hexc('#c99a5b'), width=20)
d.line([(0, 330), (S, 60)], fill=hexc('#fcd6a4'), width=14)
# Location pin dot
d.ellipse([200, 160, 236, 196], fill=(255, 255, 255))
d.ellipse([206, 166, 230, 190], fill=hexc('#4285f4'))
img.filter(ImageFilter.SMOOTH).save(f'{out}/layer_default.webp', quality=88, method=6)

# ---------- Terrain: shaded relief with contours ----------
def noise(scale):
    n = rng.random((S // scale + 2, S // scale + 2))
    small = Image.fromarray((n * 255).astype(np.uint8))
    return np.asarray(small.resize((S, S), Image.BICUBIC), dtype=float) / 255.0

h = 0.5 * noise(96) + 0.3 * noise(48) + 0.15 * noise(24) + 0.05 * noise(12)
# A ridge running across, so it reads as mountains
yy, xx = np.mgrid[0:S, 0:S] / S
h += 0.55 * np.exp(-((yy - 0.35 - 0.25 * xx) ** 2) / 0.02)
h = (h - h.min()) / (h.max() - h.min())

# Hill shading from the north-west
gy, gx = np.gradient(h * 40)
shade = np.clip(0.75 + (-gx * 0.6 - gy * 0.6) / np.sqrt(1 + gx ** 2 + gy ** 2), 0.35, 1.25)

stops = [(0.0, '#9fcf8a'), (0.35, '#c7d99a'), (0.55, '#e3d29a'), (0.75, '#c4a173'), (0.9, '#a98a6a'), (1.0, '#f4f1ec')]
ramp = np.zeros((S, S, 3))
for (a, ca), (b, cb) in zip(stops, stops[1:]):
    m = (h >= a) & (h <= b)
    t = ((h - a) / (b - a))[..., None]
    ramp[m] = (np.array(hexc(ca)) * (1 - t) + np.array(hexc(cb)) * t)[m]
rgb = np.clip(ramp * shade[..., None], 0, 255)

# Contour lines every 1/14 of the height range
levels = h * 14
edge = np.abs(np.diff(np.floor(levels), axis=0, prepend=np.floor(levels[:1]))) + \
       np.abs(np.diff(np.floor(levels), axis=1, prepend=np.floor(levels[:, :1])))
rgb[edge > 0] = rgb[edge > 0] * 0.62

terrain = Image.fromarray(rgb.astype(np.uint8))
d = ImageDraw.Draw(terrain)
# A valley stream and a winding road, so it still reads as a map
d.line([(0, 300), (90, 280), (170, 315), (260, 300), (S, 340)], fill=hexc('#7fb6d6'), width=8, joint='curve')
d.line([(20, S), (80, 250), (150, 210), (210, 150), (300, 120), (S, 90)], fill=(255, 255, 255), width=7, joint='curve')
terrain.filter(ImageFilter.SMOOTH).save(f'{out}/layer_terrain.webp', quality=85, method=6)
print('ok')
