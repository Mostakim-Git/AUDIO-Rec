#!/usr/bin/env python3
"""
AUDIO-rec :: icon generator.

Emits the whole in-app icon set as Android vector drawables from compact
geometry descriptions.  Keeping one generator means every glyph shares the same
24x24 grid, stroke weight and corner radius, so the UI stays visually coherent.

    python3 tools/gen_icons.py            # writes app/src/main/res/drawable/*.xml
"""
import math
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
W = "#FFFFFFFF"


def rect(x, y, w, h, r=0.0):
    """clockwise rounded rectangle, single subpath"""
    if r <= 0:
        return "M%g,%g h%g v%g h%g z" % (x, y, w, h, -w)
    r = min(r, w / 2.0, h / 2.0)
    return ("M%g,%g h%g a%g,%g 0 0 1 %g,%g v%g a%g,%g 0 0 1 %g,%g h%g "
            "a%g,%g 0 0 1 %g,%g v%g a%g,%g 0 0 1 %g,%g z") % (
        x + r, y, w - 2 * r, r, r, r, r, h - 2 * r,
        r, r, -r, r, -(w - 2 * r), r, r, -r, -r, -(h - 2 * r),
        r, r, r, -r)


def circle(cx, cy, r):
    return ("M%g,%g a%g,%g 0 1 0 %g,0 a%g,%g 0 1 0 %g,0 z"
            % (cx - r, cy, r, r, 2 * r, r, r, -2 * r))


def ring(cx, cy, r_out, r_in):
    """outer disc + inner disc, filled even-odd -> a stroke-less ring"""
    return {"d": circle(cx, cy, r_out) + " " + circle(cx, cy, r_in), "ft": "evenOdd"}


def poly(points):
    out = "M%g,%g " % points[0]
    for p in points[1:]:
        out += "L%g,%g " % p
    return out + "z"


def rect_ring(x, y, w, h, r=0.0, t=1.8):
    """hollow rounded rectangle (frame) as one even-odd path"""
    return {"d": rect(x, y, w, h, r) + " " + rect(x + t, y + t, w - 2 * t, h - 2 * t, max(0.0, r - t)),
            "ft": "evenOdd"}


def poly_ring(points, t=1.7):
    """hollow polygon: outer contour + inset copy (even-odd)"""
    cx = sum(p[0] for p in points) / len(points)
    cy = sum(p[1] for p in points) / len(points)
    inner = []
    for (px, py) in points:
        dx, dy = px - cx, py - cy
        d = math.hypot(dx, dy) or 1.0
        inner.append((px - dx / d * t * 1.9, py - dy / d * t * 1.9))
    return {"d": poly(points) + " " + poly(inner), "ft": "evenOdd"}


def gear(cx, cy, r_out, r_in, teeth=8, tooth_h=2.6, hole=3.6):
    pts = []
    step = math.pi / teeth
    for i in range(teeth):
        a = i * 2 * step
        for (frac, rad) in ((0.16, r_in), (0.30, r_out + tooth_h), (0.62, r_out + tooth_h), (0.76, r_in)):
            ang = a + frac * 2 * step
            pts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang)))
    return {"d": poly(pts) + " " + circle(cx, cy, hole), "ft": "evenOdd"}


def plus_rot(cx, cy, size=16, t=1.1):
    """X shape from two rotated bars (used by the close button)"""
    out = []
    for a in (math.pi / 4, -math.pi / 4):
        dx, dy = math.cos(a) * size / 2, math.sin(a) * size / 2
        nx, ny = -dy / (size / 2) * t / 2 * 1.6, dx / (size / 2) * t / 2 * 1.6
        out.append(poly([(cx - dx + nx, cy - dy + ny), (cx + dx + nx, cy + dy + ny),
                         (cx + dx - nx, cy + dy - ny), (cx - dx - nx, cy - dy - ny)]))
    return " ".join(out)


def svg(name, paths, w=24, h=24, tintless=True):
    body = "\n".join(
        '    <path\n        android:fillColor="%s"\n        android:fillType="%s"\n        android:pathData="%s" />'
        % (W, p.get("ft", "nonZero"), p["d"]) if isinstance(p, dict) else
        '    <path\n        android:fillColor="%s"\n        android:pathData="%s" />' % (W, p)
        for p in paths)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="%ddp"\n    android:height="%ddp"\n'
           '    android:viewportWidth="24"\n    android:viewportHeight="24">\n%s\n</vector>\n'
           % (w, h, body))
    with open(os.path.join(OUT, "ic_%s.xml" % name), "w") as f:
        f.write(xml)


# --------------------------------------------------------------- geometry ---
def bars(x0, widths, gap, y_bottom, heights, r=1.0):
    """vertical waveform bars"""
    p = []
    for i, h in enumerate(heights):
        p.append(rect(x0 + i * (widths + gap), y_bottom - h, widths, h, r))
    return p


ICONS = {}

# navigation ---------------------------------------------------------------
ICONS["dash"] = [rect(3, 3, 8, 8, 1.5), rect(13, 3, 8, 8, 1.5),
                 rect(3, 13, 8, 8, 1.5), rect(13, 13, 8, 8, 1.5)]
ICONS["rec"] = [ring(12, 12, 8.4, 6.2), circle(12, 12, 4.4)]
ICONS["mixer"] = [rect(3, 4, 18, 1.6, 0.8), rect(3, 11.2, 18, 1.6, 0.8), rect(3, 18.4, 18, 1.6, 0.8),
                  rect(7, 1.6, 3.4, 6.4, 1.4), rect(14, 8.8, 3.4, 6.4, 1.4), rect(5, 16, 3.4, 6.4, 1.4)]
ICONS["usb"] = [rect(10.9, 7.4, 2.2, 12.2, 1.0), poly([(12, 1.6), (15.0, 6.6), (9.0, 6.6)]),
                rect(8.8, 19.6, 6.4, 2.8, 1.0), rect(6.4, 11.2, 6.4, 2.0, 1.0), rect(11.2, 15.0, 6.4, 2.0, 1.0)]
ICONS["sessions"] = [poly([(2.5, 6.5), (9.5, 6.5), (11.5, 9), (21.5, 9), (21.5, 19.5), (2.5, 19.5)]),
                     rect(4.5, 4.2, 9.0, 2.0, 1.0)]
ICONS["library"] = [rect(4, 3, 2.6, 18, 1.2), rect(8.6, 5, 2.6, 16, 1.2), rect(13.2, 2, 2.6, 19, 1.2),
                    circle(19.6, 17.4, 2.6)]
ICONS["playlist"] = [rect(3, 4.5, 12, 2.0, 1.0), rect(3, 10, 12, 2.0, 1.0), rect(3, 15.5, 7, 2.0, 1.0),
                     poly([(16, 12.5), (22, 16.5), (16, 20.5)])]
ICONS["export"] = [poly([(12, 2), (17.5, 9), (14, 9), (14, 15), (10, 15), (10, 9), (6.5, 9)]),
                   rect(4, 17.5, 16, 2.4, 1.2)]
ICONS["preset"] = [rect(3, 3.5, 18, 2.0, 1.0), rect(3, 9.5, 18, 2.0, 1.0), rect(3, 15.5, 18, 2.0, 1.0),
                   circle(8, 4.5, 2.6), circle(16, 10.5, 2.6), circle(10, 16.5, 2.6)]
ICONS["storage"] = [rect_ring(2.8, 4.6, 18.4, 14.8, 2.4, 1.8), rect(5.2, 19.6, 13.6, 2.4, 1.2),
                    rect(6.4, 8.2, 6.0, 1.8, 0.9), rect(6.4, 12.0, 9.6, 1.8, 0.9)]
ICONS["settings"] = [gear(12, 12, 8.9, 8.0, teeth=8, tooth_h=1.7, hole=3.9)]
ICONS["about"] = [ring(12, 12, 9.6, 7.9), rect(11.0, 6.6, 2.0, 2.2, 1.0), rect(11.0, 10.2, 2.0, 7.4, 1.0)]

# transport / actions ------------------------------------------------------
ICONS["play"] = [poly([(7, 4.4), (20, 12), (7, 19.6)])]
ICONS["pause"] = [rect(6.6, 4.6, 3.9, 14.8, 1.2), rect(13.5, 4.6, 3.9, 14.8, 1.2)]
ICONS["stop"] = [rect(6, 6, 12, 12, 2.0)]
ICONS["record"] = [circle(12, 12, 7.6)]
ICONS["headphones"] = [poly([(3.4, 15.5), (3.4, 13.0), (10.4, 5.8), (13.6, 5.8), (20.6, 13.0), (20.6, 15.5), (17.4, 15.5), (17.4, 12.6), (12.0, 7.2), (6.6, 12.6), (6.6, 15.5)]),
                       rect(3.0, 14.6, 4.4, 6.4, 1.6), rect(16.6, 14.6, 4.4, 6.4, 1.6)]
ICONS["refresh"] = [{"d": "M20,12 a8,8 0 1 1 -2.4,-5.7 L20,4.4 L20,10.2 L14.2,10.2 L16.6,7.9 a5.6,5.6 0 1 0 1.7,4 z", "ft": "nonZero"}]
ICONS["plus"] = [rect(10.8, 4, 2.4, 16, 1.2), rect(4, 10.8, 16, 2.4, 1.2)]
ICONS["edit"] = [poly([(4, 17.6), (5.2, 12.4), (15.0, 2.6), (18.4, 6.0), (8.6, 15.8)]),
                 rect(15.8, 1.8, 6.4, 2.6, 1.0)]
ICONS["trash"] = [rect(5.2, 7.2, 13.6, 14.0, 1.8), rect(9.4, 9.6, 1.8, 9.2, 0.8),
                  rect(12.8, 9.6, 1.8, 9.2, 0.8), rect(3.6, 4.4, 16.8, 2.2, 1.1),
                  rect(9.0, 1.8, 6.0, 2.2, 1.0)]
ICONS["share"] = [circle(17.5, 5.5, 2.9), circle(6.5, 12, 2.9), circle(17.5, 18.5, 2.9),
                  {"d": "M8.9,10.6 L15.1,6.9 L16.4,8.9 L10.2,12.6 z", "ft": "nonZero"},
                  {"d": "M10.2,11.4 L16.4,15.1 L15.1,17.1 L8.9,13.4 z", "ft": "nonZero"}]
ICONS["chev_right"] = [poly([(9, 4.6), (17, 12), (9, 19.4), (7.2, 17.6), (13.4, 12), (7.2, 6.4)])]
ICONS["chev_down"] = [poly([(4.6, 9), (12, 17), (19.4, 9), (17.6, 7.2), (12, 13.4), (6.4, 7.2)])]
ICONS["back"] = [poly([(15, 4.6), (16.8, 6.4), (10.6, 12), (16.8, 17.6), (15, 19.4), (7, 12)])]
ICONS["menu"] = [rect(3, 4.6, 18, 2.2, 1.1), rect(3, 10.9, 18, 2.2, 1.1), rect(3, 17.2, 18, 2.2, 1.1)]
ICONS["close"] = [{"d": plus_rot(12, 12, 16.4, 1.0), "ft": "nonZero"}]
ICONS["check"] = [poly([(4.4, 12.4), (9.4, 17.4), (19.6, 7.2), (17.8, 5.4), (9.4, 13.8), (6.2, 10.6)])]
ICONS["warn"] = [poly_ring([(12, 2.4), (22.4, 20.6), (1.6, 20.6)], 1.5),
                 rect(11.0, 9.4, 2.0, 5.6, 1.0), rect(11.0, 16.4, 2.0, 2.0, 1.0)]
ICONS["info"] = [ring(12, 12, 9.6, 7.9), rect(11.0, 10.2, 2.0, 7.4, 1.0), rect(11.0, 6.6, 2.0, 2.2, 1.0)]
ICONS["search"] = [ring(10.6, 10.6, 6.4, 4.6), {"d": "M15.2,15.2 L21,21 L19.4,22.6 L13.6,16.8 z", "ft": "nonZero"}]
ICONS["folder"] = [poly([(2.5, 5.5), (9.0, 5.5), (11.0, 8.0), (21.5, 8.0), (21.5, 19.5), (2.5, 19.5)])]
ICONS["file"] = [poly([(5, 2.5), (14.5, 2.5), (19, 7), (19, 21.5), (5, 21.5)]),
                 rect(7.6, 12, 8.8, 1.8, 0.9), rect(7.6, 16, 8.8, 1.8, 0.9)]
ICONS["wave"] = bars(2.4, 2.0, 1.0, 21, [6, 13, 18, 9, 15, 20, 11, 5])
ICONS["mic"] = [rect(9.2, 2.4, 5.6, 11.6, 2.8), {"d": "M6,11.4 L8.2,11.4 a3.8,3.8 0 0 0 7.6,0 L18,11.4 a6,6 0 0 1 -4.2,5.7 L13.8,21 L18,21 L18,22.6 L6,22.6 L6,21 L10.2,21 L10.2,17.1 A6,6 0 0 1 6,11.4 z", "ft": "nonZero"}]
ICONS["speaker"] = [rect(4.4, 2.6, 9.0, 18.8, 2.0), circle(8.9, 7.6, 2.0), circle(8.9, 16.2, 3.4),
                    {"d": "M15.6,8.2 a5.2,5.2 0 0 1 0,7.6 L14.2,14.4 a3.2,3.2 0 0 0 0,-4.8 z", "ft": "nonZero"},
                    {"d": "M18.4,5.4 a9.2,9.2 0 0 1 0,13.2 L17,17.2 a7.2,7.2 0 0 0 0,-10.4 z", "ft": "nonZero"}]
ICONS["input"] = [rect_ring(2.6, 9.2, 12.4, 5.6, 1.6, 1.6), rect(15.0, 10.4, 3.4, 3.2, 1.0), poly([(21, 8.4), (21, 15.6), (18.4, 12)])]
ICONS["output"] = [rect_ring(9.0, 9.2, 12.4, 5.6, 1.6, 1.6), rect(5.6, 10.4, 3.4, 3.2, 1.0), poly([(3, 8.4), (3, 15.6), (5.6, 12)])]
ICONS["clock"] = [ring(12, 12, 9.4, 7.8), rect(11.0, 6.6, 2.0, 6.4, 1.0), rect(11.0, 11.4, 5.6, 2.0, 1.0)]
ICONS["grid"] = [rect(3, 3, 5.4, 5.4, 1.4), rect(9.3, 3, 5.4, 5.4, 1.4), rect(15.6, 3, 5.4, 5.4, 1.4),
                 rect(3, 9.3, 5.4, 5.4, 1.4), rect(9.3, 9.3, 5.4, 5.4, 1.4), rect(15.6, 9.3, 5.4, 5.4, 1.4),
                 rect(3, 15.6, 5.4, 5.4, 1.4), rect(9.3, 15.6, 5.4, 5.4, 1.4), rect(15.6, 15.6, 5.4, 5.4, 1.4)]
ICONS["knob"] = [ring(12, 12, 9.0, 6.6), rect(11.1, 4.4, 1.8, 5.4, 0.9)]
ICONS["sd"] = [poly([(4, 3), (15, 3), (20, 8.4), (20, 21), (4, 21)]),
               rect(7.2, 5.6, 1.7, 4.2, 0.7), rect(10.3, 5.6, 1.7, 4.2, 0.7), rect(13.4, 5.6, 1.7, 4.2, 0.7)]
ICONS["monitor"] = [rect_ring(2.4, 4.0, 19.2, 12.8, 2.0, 1.8), rect(9.6, 17.4, 4.8, 2.4, 1.0), rect(6.4, 20, 11.2, 2.0, 1.0)]
ICONS["level"] = [rect_ring(2.4, 8.4, 19.2, 7.2, 1.8, 1.7), rect(4.8, 10.6, 3.4, 2.8, 0.9), rect(10.3, 10.6, 3.4, 2.8, 0.9),
                  rect(15.8, 10.6, 3.4, 2.8, 0.9)]
ICONS["lock"] = [rect(5, 10.4, 14, 11.2, 2.0), {"d": "M8.4,10.4 L8.4,7.6 a3.6,3.6 0 0 1 7.2,0 L15.6,10.4 L13.4,10.4 L13.4,7.6 a1.4,1.4 0 0 0 -2.8,0 L10.6,10.4 z", "ft": "nonZero"}]
ICONS["wrench"] = [{"d": "M17.2,2.6 a5.6,5.6 0 0 0 -5.2,7.5 L2.6,19.5 L4.5,21.4 L14.1,11.8 a5.6,5.6 0 0 0 7.5,-5.2 l-3.1,3.1 -2.4,-0.6 -0.6,-2.4 z", "ft": "nonZero"}]
ICONS["swap"] = [poly([(7.4, 3.4), (7.4, 16.6), (3.6, 16.6), (9, 21.6), (14.4, 16.6), (10.6, 16.6), (10.6, 3.4)]),
                 poly([(16.6, 20.6), (16.6, 7.4), (20.4, 7.4), (15, 2.4), (9.6, 7.4), (13.4, 7.4), (13.4, 20.6)])]
ICONS["bolt"] = [poly([(13.6, 2), (6, 13.4), (11, 13.4), (9.4, 22), (17.6, 10.2), (12.4, 10.2)])]
ICONS["wave_out"] = [rect(3, 11, 18, 2.0, 1.0), rect(6.6, 6.6, 2.4, 10.8, 1.2), rect(15, 6.6, 2.4, 10.8, 1.2)]

for name, paths in ICONS.items():
    svg(name, paths)

print("wrote %d icons to %s" % (len(ICONS), os.path.normpath(OUT)))
