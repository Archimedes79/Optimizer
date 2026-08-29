"""Render the Portfolio Optimizer Classic launcher icons.

The adaptive icon (API 26+) is defined by the vector drawables in
app/src/main/res/drawable/ic_launcher_*.xml. This script mirrors that exact
geometry to produce the legacy raster mipmaps needed on API 24-25, plus the
512x512 store icon, so both paths stay visually identical.

Usage:  python3 tools/generate_icons.py
Requires: Pillow
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DOCS = os.path.join(ROOT, "docs")

S = 16                                    # supersampling: 1 dp -> 16 px
CANVAS = 108 * S                          # adaptive icon canvas
SAFE = (18 * S, 18 * S, 90 * S, 90 * S)   # inner 72dp safe zone (legacy crop)

GRAD = [(0.00, (0x14, 0xA3, 0xA8)),
        (0.55, (0x0D, 0x73, 0x77)),
        (1.00, (0x06, 0x39, 0x3B))]
GRID_Y = (44, 58, 72)

# Individual asset price paths, drawn back to front.
LINES = [
    ("#FF8A80", 0.9, 2.8, [(24, 72), (35.6, 76), (47.2, 66), (58.8, 68), (70.4, 54), (82, 57)]),
    ("#F6C445", 0.9, 2.8, [(24, 78), (35.6, 72), (47.2, 74), (58.8, 58), (70.4, 60), (82, 45)]),
    ("#8FE7E4", 0.9, 2.8, [(24, 74), (35.6, 64), (47.2, 58), (58.8, 46), (70.4, 42), (82, 31)]),
]
# The portfolio index: the weighted aggregate running through the bundle.
INDEX = [(24, 75), (35.6, 71), (47.2, 66), (58.8, 57), (70.4, 52), (82, 44)]
MARKER = (82, 44)

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def gradient_bg():
    """Diagonal linear gradient from (0,0) to (108,108)."""
    img = Image.new("RGB", (CANVAS, CANVAS))
    px = img.load()
    for y in range(CANVAS):
        for x in range(CANVAS):
            t = (x + y) / (2 * (CANVAS - 1))
            for i in range(len(GRAD) - 1):
                o0, c0 = GRAD[i]
                o1, c1 = GRAD[i + 1]
                if t <= o1 or i == len(GRAD) - 2:
                    px[x, y] = lerp(c0, c1, min(1.0, max(0.0, (t - o0) / (o1 - o0))))
                    break
    return img.convert("RGBA")


def rgba(hex_color, alpha):
    h = hex_color.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (round(alpha * 255),)


def draw_polyline(draw, pts, color, width_dp):
    """Polyline with round joins and caps, matching the vector drawable."""
    w = round(width_dp * S)
    scaled = [(x * S, y * S) for x, y in pts]
    draw.line(scaled, fill=color, width=w, joint="curve")
    r = w / 2
    for x, y in (scaled[0], scaled[-1]):
        draw.ellipse((x - r, y - r, x + r, y + r), fill=color)


def render():
    base = gradient_bg()

    grid = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grid)
    for y in GRID_Y:
        gd.line([(24 * S, y * S), (82 * S, y * S)], fill=(255, 255, 255, 26),
                width=round(0.8 * S))
    base = Image.alpha_composite(base, grid)

    for color, alpha, w, pts in LINES:
        layer = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
        draw_polyline(ImageDraw.Draw(layer), pts, rgba(color, alpha), w)
        base = Image.alpha_composite(base, layer)

    fg = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    d = ImageDraw.Draw(fg)
    draw_polyline(d, INDEX, (255, 255, 255, 255), 4.0)
    mx, my = MARKER[0] * S, MARKER[1] * S
    for r, fill in ((5.0 * S, (255, 255, 255, 255)), (2.0 * S, (0x0D, 0x73, 0x77, 255))):
        d.ellipse((mx - r, my - r, mx + r, my + r), fill=fill)
    return Image.alpha_composite(base, fg)


def masked(img, size, circle):
    mask = Image.new("L", (size * 4, size * 4), 0)
    md = ImageDraw.Draw(mask)
    box = (0, 0, size * 4 - 1, size * 4 - 1)
    if circle:
        md.ellipse(box, fill=255)
    else:
        md.rounded_rectangle(box, radius=size * 4 * 0.22, fill=255)
    out = img.copy()
    out.putalpha(mask.resize((size, size), Image.LANCZOS))
    return out


def main():
    full = render()
    safe = full.crop(SAFE)

    for name, size in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        scaled = safe.resize((size, size), Image.LANCZOS)
        masked(scaled, size, False).save(
            os.path.join(folder, "ic_launcher.webp"), lossless=True)
        masked(scaled, size, True).save(
            os.path.join(folder, "ic_launcher_round.webp"), lossless=True)
        print("wrote mipmap-%s (%dpx)" % (name, size))

    os.makedirs(DOCS, exist_ok=True)
    safe.resize((512, 512), Image.LANCZOS).convert("RGB").save(
        os.path.join(DOCS, "icon-512.png"))
    print("wrote docs/icon-512.png")


if __name__ == "__main__":
    main()
