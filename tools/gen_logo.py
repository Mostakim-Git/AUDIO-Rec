#!/usr/bin/env python3
"""
AUDIO-rec :: brand mark generator.

Emits the AUDIO-rec logo in every form the project needs, from one geometry
description so nothing can drift out of sync:

    assets/branding/audiorec-logo.svg      full badge (docs, README, store)
    assets/branding/audiorec-mark.svg      mark only, transparent
    assets/branding/logo.txt               the same SVG in .txt form
    res/drawable/ic_logo.xml               vector mark for the UI (24dp grid)
    res/drawable/ic_logo_wordmark.xml      mark + "AUDIO-rec" lockup
    res/drawable/ic_launcher_foreground.xml
    res/mipmap-*/ic_launcher.png           legacy raster launcher icons

usage: python3 tools/gen_logo.py
"""
import math
import os
import subprocess
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
BRAND = os.path.join(ROOT, "assets", "branding")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

CYAN = "#2FD4E6"
CYAN_DEEP = "#17A7B8"
ORANGE = "#FF8A2B"
INK = "#0A0D12"
PANEL = "#11161E"
SLATE = "#26313F"
LIGHT = "#E9EFF7"

# level envelope: the "loudest peak" is the orange bar -> reads as a meter
BARS = [0.30, 0.56, 0.82, 1.00, 0.66, 0.44, 0.24]
PEAK_INDEX = 3


def bar_paths(cx, cy, height, bar_w=0.105, gap=0.058, radius=0.5):
    """vertical rounded bars centred on (cx,cy); all units are fractions of `height`"""
    n = len(BARS)
    total_w = n * bar_w + (n - 1) * gap
    x0 = cx - total_w / 2.0
    out = []
    for i, amp in enumerate(BARS):
        h = height * amp
        x = x0 + i * (bar_w + gap)
        y = cy - h / 2.0
        r = min(radius * bar_w, h / 2.0, bar_w / 2.0)
        out.append((x, y, bar_w, h, r, i == PEAK_INDEX, amp))
    return out


def rrect(x, y, w, h, r):
    return ("M%g,%g h%g a%g,%g 0 0 1 %g,%g v%g a%g,%g 0 0 1 %g,%g h%g "
            "a%g,%g 0 0 1 %g,%g v%g a%g,%g 0 0 1 %g,%g z"
            % (x + r, y, w - 2 * r, r, r, r, r, h - 2 * r,
               r, r, -r, r, -(w - 2 * r), r, r, -r, -r, -(h - 2 * r),
               r, r, r, -r))


def mark_svg(size=512, bg=None, pad=0.18, ink=None):
    """mark: level-meter burst inside an optional rounded badge"""
    S = float(size)
    cx = cy = S / 2.0
    h = S * (1 - 2 * pad)
    s = []
    if bg:
        s.append('  <rect x="0" y="0" width="%g" height="%g" rx="%g" fill="%s"/>'
                 % (S, S, S * 0.235, bg))
    if ink:
        s.append('  <rect x="%g" y="%g" width="%g" height="%g" rx="%g" fill="none" '
                 'stroke="%s" stroke-width="%g"/>' % (S * .04, S * .04, S * .92, S * .92,
                                                      S * .19, ink, S * .012))
    for (x, y, w, bh, r, peak, amp) in bar_paths(cx, cy, h, bar_w=S * 0.079,
                                                 gap=S * 0.044, radius=0.5):
        # subtle vertical two-tone: taller bars read darker
        col = ORANGE if peak else (CYAN if amp > 0.4 else CYAN_DEEP)
        s.append('  <path d="%s" fill="%s"/>' % (rrect(x, y, w, bh, r), col))
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<svg xmlns="http://www.w3.org/2000/svg" width="%g" height="%g" '
            'viewBox="0 0 %g %g">\n%s\n</svg>\n' % (S, S, S, S, "\n".join(s)))


def wordmark_svg(width=1600):
    """horizontal lockup: badge + AUDIO-rec wordmark + tagline"""
    H = width * 0.25
    badge = width * 0.20
    s = ['  <rect x="0" y="0" width="%g" height="%g" rx="%g" fill="%s"/>'
         % (width, H, H * 0.10, PANEL),
         '  <rect x="%g" y="%g" width="%g" height="%g" rx="%g" fill="none" stroke="%s" stroke-width="%g"/>'
         % (H * .06, H * .06, width - H * .12, H - H * .12, H * .07, SLATE, max(1.0, H * .004))]
    bx, by = H * 0.16, H * 0.095
    for (x, y, w, bh, r, peak, amp) in bar_paths(bx + badge / 2, by + badge / 2, badge * 0.74,
                                                 bar_w=badge * 0.079, gap=badge * 0.044):
        col = ORANGE if peak else (CYAN if amp > 0.4 else CYAN_DEEP)
        s.append('  <path d="%s" fill="%s"/>' % (rrect(x, y, w, bh, r), col))
    tx = H * 0.16 + badge + H * 0.12
    s.append('  <text x="%g" y="%g" font-family="Helvetica,Arial,sans-serif" font-size="%g" '
             'font-weight="700" fill="%s" letter-spacing="%g">AUDIO</text>'
             % (tx, H * 0.635, H * 0.50, LIGHT, H * 0.006))
    s.append('  <text x="%g" y="%g" font-family="Helvetica,Arial,sans-serif" font-size="%g" '
             'font-weight="400" fill="%s">-rec</text>'
             % (tx + H * 1.66, H * 0.635, H * 0.50, CYAN))
    s.append('  <text x="%g" y="%g" font-family="Helvetica,Arial,sans-serif" font-size="%g" '
             'fill="%s" letter-spacing="%g">USB AUDIO RECORDING &amp; PRODUCTION WORKSTATION</text>'
             % (tx + H * 0.03, H * 0.845, H * 0.095, "#9AACBF", H * 0.006))
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<svg xmlns="http://www.w3.org/2000/svg" width="%g" height="%g" viewBox="0 0 %g %g">\n%s\n</svg>\n'
            % (width, H, width, H, "\n".join(s)))


def vector_mark(path_fill_dims, viewport=108, pad=0.18, peak_color=ORANGE):
    """Android <vector> for the adaptive-icon foreground (108x108 grid)"""
    S = float(viewport)
    paths = []
    for (x, y, w, bh, r, peak, amp) in bar_paths(S / 2, S / 2, S * (1 - 2 * pad),
                                                 bar_w=S * 0.079, gap=S * 0.044):
        col = peak_color if peak else CYAN
        paths.append('    <path\n        android:fillColor="%s"\n        '
                     'android:pathData="%s" />' % (col, rrect(x, y, w, bh, r)))
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="%ddp"\n    android:height="%ddp"\n'
            '    android:viewportWidth="%d"\n    android:viewportHeight="%d">\n%s\n</vector>\n'
            % (path_fill_dims, path_fill_dims, viewport, viewport, "\n".join(paths)))


def vector_ui(size_dp=28, viewport=24):
    """24dp-grid vector of the mark, tintable with android:tint for the appbar"""
    S = float(viewport)
    paths = []
    for (x, y, w, bh, r, peak, amp) in bar_paths(S / 2, S / 2, S - 4.0, bar_w=S * 0.079,
                                                 gap=S * 0.047):
        paths.append('    <path\n        android:fillColor="#FFFFFFFF"\n        '
                     'android:pathData="%s" />' % rrect(x, y, w, bh, r))
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="%ddp"\n    android:height="%ddp"\n'
            '    android:viewportWidth="24"\n    android:viewportHeight="24">\n%s\n</vector>\n'
            % (size_dp, size_dp, "\n".join(paths)))


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)
    print("  %s" % os.path.relpath(path, ROOT))


def main():
    print("AUDIO-rec :: brand assets")
    logo = mark_svg(512, bg=INK, ink=SLATE)
    write(os.path.join(BRAND, "audiorec-logo.svg"), logo)
    write(os.path.join(BRAND, "logo.txt"), logo)                      # SVG in txt form
    write(os.path.join(BRAND, "audiorec-mark.svg"), mark_svg(512))
    write(os.path.join(BRAND, "audiorec-lockup.svg"), wordmark_svg(1600))
    write(os.path.join(RES, "drawable", "ic_logo.xml"), vector_ui(28))
    write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"),
          vector_mark(108, viewport=108, pad=0.235))
    write(os.path.join(RES, "drawable", "ic_launcher_mono.xml"),
          vector_mark(108, viewport=108, pad=0.235, peak_color="#FFFFFFFF"))

    # ---- legacy raster mipmaps (adaptive icons still cover API 26+, but old
    #      launchers and some OEM galleries pick up the PNGs)
    try:
        from svgpreview import render
    except Exception:
        print("  (skipping PNG mipmaps: preview rasteriser unavailable)")
        return
    for dpi, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        d = os.path.join(RES, "mipmap-" + dpi)
        os.makedirs(d, exist_ok=True)
        tmp_svg = os.path.join("/tmp", "audiorec_icon_%s.svg" % dpi)
        with open(tmp_svg, "w") as f:
            f.write(mark_svg(px * 4, bg="#11161E"))
        render(open(tmp_svg).read(), os.path.join(d, "ic_launcher.png"), px, (17, 22, 30, 255), ss=2)
        # round variant: punch the badge to a circle
        with open(tmp_svg, "w") as f:
            f.write(mark_svg(px * 4, bg=None))
        render(open(tmp_svg).read(), os.path.join(d, "ic_launcher_round.png"), px, (17, 22, 30, 255), ss=2)
        print("  res/mipmap-%s/ic_launcher.png (+_round)" % dpi)


if __name__ == "__main__":
    main()
