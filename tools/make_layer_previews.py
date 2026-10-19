"""Generates the map-type preview images for the layers sheet (Default and Terrain)."""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 384  # rendered size; drawn at 64 dp, so this stays sharp up to 6x
out = sys.argv[1]

# Light pictures use classic OpenStreetMap colours; dark ones match the app's near-black map.
THEMES = {
    "": dict(
        bg='#f2efe9', park='#c8facc', building='#d9d0c9', building_edge='#c9bfb6', water='#aad3df',
        casing='#c4bdb4', road='#ffffff', main_casing='#c99a5b', main='#fcd6a4',
        ramp=[(0.0, '#9fcf8a'), (0.35, '#c7d99a'), (0.55, '#e3d29a'), (0.75, '#c4a173'), (0.9, '#a98a6a'), (1.0, '#f4f1ec')],
        contour=0.62, stream='#7fb6d6', trail='#ffffff',
    ),
    "_dark": dict(
        bg='#161616', park='#1c2b1f', building='#262626', building_edge='#2e2e2e', water='#1c3d57',
        casing='#1f1f1f', road='#3a3a3a', main_casing='#4a3418', main='#8a5a26',
        ramp=[(0.0, '#22371f'), (0.35, '#34452a'), (0.55, '#4d4a30'), (0.75, '#5c4a35'), (0.9, '#4e4136'), (1.0, '#8c8780')],
        contour=1.45, stream='#2f6c8f', trail='#9e9e9e',
    ),
}

def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

MARKER_RED = '#e53935'  # the same red as the location marker in the app


def marker(img, cx, cy):
    """The app's location marker: a soft red halo, a white ring and a red dot."""
    halo = Image.new('RGBA', img.size, (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse([cx - 40, cy - 40, cx + 40, cy + 40], fill=hexc(MARKER_RED) + (70,))
    base = img.convert('RGBA')
    base.alpha_composite(halo)
    d = ImageDraw.Draw(base)
    d.ellipse([cx - 20, cy - 20, cx + 20, cy + 20], fill=(255, 255, 255, 255))
    d.ellipse([cx - 14, cy - 14, cx + 14, cy + 14], fill=hexc(MARKER_RED) + (255,))
    return base.convert('RGB')


def render(suffix, t):
    rng = np.random.default_rng(7)
    # ---------- Default: classic OpenStreetMap-style street map ----------
    img = Image.new('RGB', (S, S), hexc(t['bg']))
    d = ImageDraw.Draw(img)
    # Park
    d.polygon([(30, 230), (150, 205), (175, 330), (40, 360)], fill=hexc(t['park']))
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
            d.rectangle([bx + 10, by + 10, bx + 32, by + 52], fill=hexc(t['building']), outline=hexc(t['building_edge']))
            d.rectangle([bx + 36, by + 10, bx + 54, by + 28], fill=hexc(t['building']), outline=hexc(t['building_edge']))
    # River
    river = [(S, 40), (300, 70), (250, 120), (230, 180), (260, 250), (240, 320), (200, S)]
    d.line(river, fill=hexc(t['water']), width=26, joint='curve')
    # Minor roads (grey casing, white fill)
    for v in range(32, S, 64):
        d.line([(v, 0), (v, S)], fill=hexc(t['casing']), width=11)
        d.line([(0, v), (S, v)], fill=hexc(t['casing']), width=11)
    for v in range(32, S, 64):
        d.line([(v, 0), (v, S)], fill=hexc(t['road']), width=7)
        d.line([(0, v), (S, v)], fill=hexc(t['road']), width=7)
    # Main road (orange) on a diagonal
    d.line([(0, 330), (S, 60)], fill=hexc(t['main_casing']), width=20)
    d.line([(0, 330), (S, 60)], fill=hexc(t['main']), width=14)
    # Location pin dot
    img = marker(img, 218, 178)
    img.filter(ImageFilter.SMOOTH).save(f'{out}/layer_default{suffix}.webp', quality=88, method=6)

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

    stops = t['ramp']
    ramp = np.zeros((S, S, 3))
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        m = (h >= a) & (h <= b)
        f = ((h - a) / (b - a))[..., None]
        ramp[m] = (np.array(hexc(ca)) * (1 - f) + np.array(hexc(cb)) * f)[m]
    rgb = np.clip(ramp * shade[..., None], 0, 255)

    # Contour lines every 1/14 of the height range
    levels = h * 14
    edge = np.abs(np.diff(np.floor(levels), axis=0, prepend=np.floor(levels[:1]))) + \
           np.abs(np.diff(np.floor(levels), axis=1, prepend=np.floor(levels[:, :1])))
    rgb[edge > 0] = np.clip(rgb[edge > 0] * t['contour'], 0, 255)

    terrain = Image.fromarray(rgb.astype(np.uint8))
    d = ImageDraw.Draw(terrain)
    # A valley stream and a winding road, so it still reads as a map
    d.line([(0, 300), (90, 280), (170, 315), (260, 300), (S, 340)], fill=hexc(t['stream']), width=8, joint='curve')
    d.line([(20, S), (80, 250), (150, 210), (210, 150), (300, 120), (S, 90)], fill=hexc(t['trail']), width=7, joint='curve')
    terrain = marker(terrain, 150, 210)
    terrain.filter(ImageFilter.SMOOTH).save(f'{out}/layer_terrain{suffix}.webp', quality=85, method=6)

for suffix, theme in THEMES.items():
    render(suffix, theme)
print('ok')
