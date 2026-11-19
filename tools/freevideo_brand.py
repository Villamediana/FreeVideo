"""FreeVideo's icons: a white play disc with wings on the ember gradient.

Writes the adaptive launcher layers, the in-app mark, the splash and the notification icon.
Usage: python3 tools/freevideo_brand.py app/src/main/res
"""
from PIL import Image, ImageDraw, ImageFilter
import math, os, sys

RES = sys.argv[1]
WHITE = (255, 255, 255, 255)
EMBER = (0xE8, 0x30, 0x4A, 255)
FEATHERS = [(255, 255, 255, 255), (255, 255, 255, 222), (255, 255, 255, 186)]


def bez(p0, p1, p2, p3, n=36):
    return [((1 - t) ** 3 * p0[0] + 3 * (1 - t) ** 2 * t * p1[0] + 3 * (1 - t) * t * t * p2[0] + t ** 3 * p3[0],
             (1 - t) ** 3 * p0[1] + 3 * (1 - t) ** 2 * t * p1[1] + 3 * (1 - t) * t * t * p2[1] + t ** 3 * p3[1]) for t in [i / n for i in range(n + 1)]]


def feather(d, cx, cy, length, width, angle, fill):
    a = math.radians(angle); ux, uy = math.cos(a), math.sin(a); nx, ny = -uy, ux
    tip = (cx + ux * length, cy + uy * length)
    p1 = (cx + ux * length * 0.35 + nx * width, cy + uy * length * 0.35 + ny * width)
    p2 = (cx + ux * length * 0.85 + nx * width * 0.7, cy + uy * length * 0.85 + ny * width * 0.7)
    q1 = (cx + ux * length * 0.85 - nx * width * 0.25, cy + uy * length * 0.85 - ny * width * 0.25)
    q2 = (cx + ux * length * 0.35 - nx * width * 0.35, cy + uy * length * 0.35 - ny * width * 0.35)
    d.polygon(bez((cx, cy), p1, p2, tip) + bez(tip, q1, q2, (cx, cy)), fill=fill)


def rounded_triangle(d, cx, cy, r, fill, round_px):
    """A play triangle (pointing right) centred optically at (cx, cy), corners softened."""
    pts = [(cx - r * 0.42, cy - r * 0.62), (cx + r * 0.66, cy), (cx - r * 0.42, cy + r * 0.62)]
    big = Image.new('L', d.im.size, 0)
    ImageDraw.Draw(big).polygon(pts, fill=255)
    soft = big.filter(ImageFilter.GaussianBlur(round_px)).point(lambda v: 255 if v > 128 else 0)
    return soft


def art(N, unit, mono=None):
    """Play disc with wings on a transparent N×N canvas; `unit` = size of the tile it is drawn for."""
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0)); d = ImageDraw.Draw(im); c = N / 2; T = unit
    R = 0.165 * T
    cols = [mono] * 3 if mono else FEATHERS
    for sgn in (-1, 1):
        ox = c + sgn * (R * 0.92); oy = c + 0.01 * T
        for (ln, wd, ang), col in zip([(1.00, 0.30, -24), (0.88, 0.27, -4), (0.72, 0.24, 16)], cols):
            feather(d, ox + sgn * 0.01 * T, oy, ln * 0.215 * T, wd * 0.2 * T * (1 if sgn > 0 else -1), ang if sgn > 0 else 180 - ang, col)
    # the disc (with a soft shadow ring on the coloured tile)
    if not mono:
        shadow = Image.new('RGBA', (N, N), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).ellipse([c - R * 1.04, c - R * 1.0, c + R * 1.04, c + R * 1.08], fill=(90, 0, 20, 90))
        im = Image.alpha_composite(im, shadow.filter(ImageFilter.GaussianBlur(T * 0.012)))
        d = ImageDraw.Draw(im)
    d.ellipse([c - R, c - R, c + R, c + R], fill=mono or WHITE)
    tri = rounded_triangle(d, c + R * 0.06, c, R * 0.78, None, max(1, int(T * 0.006)))
    if mono:
        # cut the triangle out of the disc
        alpha = im.getchannel('A')
        alpha.paste(0, (0, 0), tri)
        im.putalpha(alpha)
    else:
        layer = Image.new('RGBA', (N, N), EMBER)
        im.paste(layer, (0, 0), tri)
    return im


def gradient(N, c1=(0xFF, 0x6A, 0x55), c2=(0xE8, 0x30, 0x4A), c3=(0xB8, 0x14, 0x3C)):
    g = Image.new('RGBA', (N, N)); px = g.load(); c = N / 2; a = math.radians(45); ca, sa = math.cos(a), math.sin(a)
    for y in range(N):
        for x in range(N):
            t = min(1, max(0, ((x - c) * ca + (y - c) * sa) / (N * 0.72) + 0.5))
            if t < 0.5:
                u = t / 0.5; col = tuple(int(c1[i] + (c2[i] - c1[i]) * u) for i in range(3))
            else:
                u = (t - 0.5) / 0.5; col = tuple(int(c2[i] + (c3[i] - c2[i]) * u) for i in range(3))
            px[x, y] = col + (255,)
    return g


def save(img, path, size):
    os.makedirs(os.path.dirname(path), exist_ok=True); img.resize((size, size), Image.LANCZOS).save(path)


BIG = 1728  # 4× xxxhdpi
fg = art(BIG, BIG * 0.74); bg = gradient(BIG // 4).resize((BIG, BIG), Image.BICUBIC); mono = art(BIG, BIG * 0.74, mono=WHITE)
for dens, px in [('mdpi', 108), ('hdpi', 162), ('xhdpi', 216), ('xxhdpi', 324), ('xxxhdpi', 432)]:
    save(fg, f'{RES}/mipmap-{dens}/ic_launcher_foreground.png', px)
    save(bg, f'{RES}/mipmap-{dens}/ic_launcher_background.png', px)
    save(mono, f'{RES}/mipmap-{dens}/ic_launcher_monochrome.png', px)
# in-app mark and splash: the full rounded tile
tile = Image.new('RGBA', (BIG, BIG), (0, 0, 0, 0)); m = Image.new('L', (BIG, BIG), 0)
ImageDraw.Draw(m).rounded_rectangle([0, 0, BIG - 1, BIG - 1], radius=int(BIG * 0.23), fill=255)
tile.paste(gradient(BIG // 4).resize((BIG, BIG), Image.BICUBIC), (0, 0), m); tile = Image.alpha_composite(tile, art(BIG, BIG * 0.98))
save(tile, f'{RES}/drawable-nodpi/freevideo_mark.png', 512)
splash = Image.new('RGBA', (BIG, BIG), (0, 0, 0, 0)); s = int(BIG * 0.6)
splash.paste(tile.resize((s, s), Image.LANCZOS), ((BIG - s) // 2, (BIG - s) // 2))
save(splash, f'{RES}/drawable-nodpi/freevideo_splash.png', 768)
# notification: white silhouette, bigger in its 24dp box
note = art(BIG, BIG * 1.15, mono=WHITE)
for dens, px in [('mdpi', 24), ('hdpi', 36), ('xhdpi', 48), ('xxhdpi', 72), ('xxxhdpi', 96)]:
    save(note, f'{RES}/drawable-{dens}/ic_notification.png', px)
print('ok')
