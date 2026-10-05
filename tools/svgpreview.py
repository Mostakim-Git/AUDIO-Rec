#!/usr/bin/env python3
"""
AUDIO-rec :: tiny SVG path rasteriser (preview / QA only).

The build never needs this: it exists so the vector artwork we ship (icons, the
AUDIO-rec logo, the adaptive launcher icon) can be *looked at* during
development from a headless box.  Supports the subset of SVG path grammar the
project uses: M m L l H h V v C c Q q A a Z z, even-odd and non-zero fills,
plus <rect>/<circle> primitives and css-free fill attributes.

    python3 tools/svgpreview.py in.svg out.png [width] [background]
"""
import math
import re
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:                                          # pragma: no cover
    print("needs pillow:  python3 -m pip install pillow")
    sys.exit(1)

TOKEN = re.compile(r"([MmLlHhVvCcQqAaZz])|(-?\d*\.?\d+(?:e-?\d+)?)")


# ------------------------------------------------------------- path parsing --
def parse_path(d):
    toks = []
    for m in TOKEN.finditer(d):
        if m.group(1):
            toks.append(m.group(1))
        else:
            toks.append(float(m.group(2)))
    subpaths, cur, pos, start, i, cmd = [], [], (0.0, 0.0), (0.0, 0.0), 0, None
    while i < len(toks):
        t = toks[i]
        if isinstance(t, str):
            cmd = t
            i += 1
            if cmd in "Zz":
                cur.append(("Z",))
                if cur:
                    subpaths.append(cur)
                    cur = []
                pos = start
                continue
        def num():
            nonlocal i
            v = toks[i]
            i += 1
            return v
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            x, y = num(), num()
            if rel:
                x, y = pos[0] + x, pos[1] + y
            if cur:
                subpaths.append(cur)
            cur = []
            cur.append(("M", x, y))
            pos = start = (x, y)
            cmd = "l" if rel else "L"
        elif c == "L":
            x, y = num(), num()
            if rel:
                x, y = pos[0] + x, pos[1] + y
            cur.append(("L", x, y))
            pos = (x, y)
        elif c == "H":
            x = num()
            x = pos[0] + x if rel else x
            cur.append(("L", x, pos[1]))
            pos = (x, pos[1])
        elif c == "V":
            y = num()
            y = pos[1] + y if rel else y
            cur.append(("L", pos[0], y))
            pos = (pos[0], y)
        elif c == "C":
            pts = [num() for _ in range(6)]
            if rel:
                pts = [pts[0] + pos[0], pts[1] + pos[1], pts[2] + pos[0],
                       pts[3] + pos[1], pts[4] + pos[0], pts[5] + pos[1]]
            cur.append(("C", *pts))
            pos = (pts[4], pts[5])
        elif c == "Q":
            pts = [num() for _ in range(4)]
            if rel:
                pts = [pts[0] + pos[0], pts[1] + pos[1], pts[2] + pos[0], pts[3] + pos[1]]
            cur.append(("Q", *pts))
            pos = (pts[2], pts[3])
        elif c == "A":
            rx, ry, rot, laf, sf, x, y = (num() for _ in range(7))
            if rel:
                x, y = pos[0] + x, pos[1] + y
            cur.append(("A", rx, ry, rot, laf, sf, x, y, pos))
            pos = (x, y)
        else:
            i += 1
    if cur:
        subpaths.append(cur)
    return subpaths


def flatten(subpaths, steps=14):
    """turn a subpath into a polygon point list"""
    pts = []
    pos = (0.0, 0.0)
    for seg in subpaths:
        op = seg[0]
        if op == "M":
            pos = (seg[1], seg[2])
            pts.append(pos)
        elif op == "L":
            pos = (seg[1], seg[2])
            pts.append(pos)
        elif op == "C":
            p0, p1, p2, p3 = pos, (seg[1], seg[2]), (seg[3], seg[4]), (seg[5], seg[6])
            for s in range(1, steps + 1):
                t = s / steps
                mt = 1 - t
                x = mt ** 3 * p0[0] + 3 * mt * mt * t * p1[0] + 3 * mt * t * t * p2[0] + t ** 3 * p3[0]
                y = mt ** 3 * p0[1] + 3 * mt * mt * t * p1[1] + 3 * mt * t * t * p2[1] + t ** 3 * p3[1]
                pts.append((x, y))
            pos = p3
        elif op == "Q":
            p0, p1, p2 = pos, (seg[1], seg[2]), (seg[3], seg[4])
            for s in range(1, steps + 1):
                t = s / steps
                mt = 1 - t
                pts.append((mt * mt * p0[0] + 2 * mt * t * p1[0] + t * t * p2[0],
                            mt * mt * p0[1] + 2 * mt * t * p1[1] + t * t * p2[1]))
            pos = p2
        elif op == "A":
            rx, ry, rot, laf, sf, x, y, p0 = seg[1:]
            pts.extend(_arc(p0, rx, ry, laf, sf, (x, y), steps))
            pos = (x, y)
        elif op == "Z":
            if pts:
                pts.append(pts[0])
    return pts


def _arc(p0, rx, ry, laf, sf, p1, steps):
    if rx == 0 or ry == 0:
        return [p1]
    phi = 0.0
    x0, y0 = p0
    x1, y1 = p1
    dx2, dy2 = (x0 - x1) / 2.0, (y0 - y1) / 2.0
    lam = dx2 * dx2 / (rx * rx) + dy2 * dy2 / (ry * ry)
    if lam > 1:
        s = math.sqrt(lam)
        rx, ry = rx * s, ry * s
    sign = 1 if laf != sf else -1
    num = rx * rx * ry * ry - rx * rx * dy2 * dy2 - ry * ry * dx2 * dx2
    den = rx * rx * dy2 * dy2 + ry * ry * dx2 * dx2
    co = sign * math.sqrt(max(0.0, num / den)) if den else 0.0
    cxp, cyp = co * rx * dy2 / ry, -co * ry * dx2 / rx
    cx, cy = cxp + (x0 + x1) / 2.0, cyp + (y0 + y1) / 2.0
    def ang(ux, uy, vx, vy):
        d = (ux * vx + uy * vy) / (math.hypot(ux, uy) * math.hypot(vx, vy) + 1e-12)
        a = math.acos(max(-1.0, min(1.0, d)))
        return -a if ux * vy - uy * vx < 0 else a
    t0 = ang(1, 0, (dx2 - cxp) / rx, (dy2 - cyp) / ry)
    dt = ang((dx2 - cxp) / rx, (dy2 - cyp) / ry, (-dx2 - cxp) / rx, (-dy2 - cyp) / ry)
    if sf == 0 and dt > 0:
        dt -= 2 * math.pi
    elif sf == 1 and dt < 0:
        dt += 2 * math.pi
    out = []
    for s in range(1, max(2, int(abs(dt) / (math.pi / 12))) + 1):
        t = t0 + dt * s / max(2, int(abs(dt) / (math.pi / 12)))
        out.append((cx + rx * math.cos(t), cy + ry * math.sin(t)))
    return out


# ------------------------------------------------------------------- raster --
def even_odd_inside(polys, pt):
    inside = False
    x, y = pt
    for pts in polys:
        n = len(pts)
        for i in range(n):
            x1, y1 = pts[i]
            x2, y2 = pts[(i + 1) % n]
            if (y1 > y) != (y2 > y):
                xint = x1 + (y - y1) * (x2 - x1) / (y2 - y1 + 1e-12)
                if x < xint:
                    inside = not inside
    return inside


def render(svg_text, out, width=512, bg=(0, 0, 0, 0), ss=3):
    vb = re.search(r'viewBox="([\d.\-\s]+)"', svg_text)
    if vb:
        vx, vy, vw, vh = [float(v) for v in vb.group(1).split()]
    else:
        wm = re.search(r'width="([\d.]+)', svg_text)
        hm = re.search(r'height="([\d.]+)', svg_text)
        vx, vy = 0, 0
        vw = float(wm.group(1)) if wm else 24
        vh = float(hm.group(1)) if hm else 24
    scale = width / vw
    height = max(1, int(round(vh * scale)))
    img = Image.new("RGBA", (width * ss, height * ss), bg)
    dr = ImageDraw.Draw(img)

    def tr(p):
        return ((p[0] - vx) * scale * ss, (p[1] - vy) * scale * ss)

    for m in re.finditer(r"<(path|rect|circle|polygon)\b([^>]*?)/?>|<text\b([^>]*?)>(.*?)</text>", svg_text, re.S):
        if m.group(1) == None:
            attrs, body = m.group(3), m.group(4)
            px = float(re.search(r'font-size="([\d.]+)"', attrs).group(1)) if re.search(r'font-size="([\d.]+)"', attrs) else 24
            x = float(re.search(r'x="([-\d.]+)"', attrs).group(1))
            y = float(re.search(r'y="([-\d.]+)"', attrs).group(1))
            col = re.search(r'fill="([^"]+)"', attrs)
            col = hexcol(col.group(1)) if col else (255, 255, 255, 255)
            bold = 'bold' in attrs
            try:
                from PIL import ImageFont
                fp = "/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf" % ("-Bold" if bold else "")
                fnt = ImageFont.truetype(fp, int(px * scale * ss))
            except Exception:
                fnt = None
            txt = body.replace("&amp;", "&").replace("&lt;", "<")
            dr.text((x * scale * ss, y * scale * ss), txt, font=fnt, fill=col, anchor="ls")
            continue
        kind, attrs = m.group(1), m.group(2)
        fill = re.search(r'fill(?:Color)?="([^"]+)"', attrs)
        fill = fill.group(1) if fill else "#000000"
        if "none" in fill:
            continue
        op = re.search(r'fill-opacity="([\d.]+)"', attrs)
        alpha = int(255 * float(op.group(1))) if op else 255
        col = hexcol(fill, alpha)
        ft = re.search(r'fillType="([^"]+)"|fill-rule="([^"]+)"', attrs)
        even_odd = bool(ft) and "evenOdd" in (ft.group(1) or ft.group(2) or "")
        if kind == "path":
            d = re.search(r'pathData="([^"]+)"|d="([^"]+)"', attrs)
            d = d.group(1) or d.group(2)
            subs = parse_path(d)
            polys = [flatten(s) for s in subs]
            polys = [[tr(p) for p in pl] for pl in polys if pl]
            if even_odd:
                for pl in polys:
                    dr.polygon(pl, fill=col)
                # punch inner contours back out
                for pl in polys[1:]:
                    dr.polygon(pl, fill=(0, 0, 0, 0) if bg[3] == 0 else bg)
            else:
                for pl in polys:
                    dr.polygon(pl, fill=col)
        elif kind == "rect":
            g = lambda k, dflt=0.0: float(re.search(k + r'="([\d.]+)"', attrs).group(1)) if re.search(k + r'="([\d.]+)"', attrs) else dflt
            x, y, w, h = g("x"), g("y"), g("width"), g("height")
            r = g("rx") or g("ry")
            if r:
                p0, p1 = tr((x, y)), tr((x + w, y + h))
                dr.rounded_rectangle([p0, p1], radius=r * scale * ss, fill=col)
            else:
                dr.rectangle([tr((x, y)), tr((x + w, y + h))], fill=col)
        elif kind == "text":
            import re as _re
            tm = _re.search(r">([^<]*)</text>", svg_text[m.end():]) if False else None
        elif kind == "circle":
            g = lambda k: float(re.search(k + r'="([\d.]+)"', attrs).group(1))
            cx, cy, r = g("cx"), g("cy"), g("r")
            dr.ellipse([tr((cx - r, cy - r)), tr((cx + r, cy + r))], fill=col)
    img = img.resize((width, height), Image.LANCZOS)
    img.save(out)
    return out


def hexcol(v, alpha=255):
    v = v.strip()
    if v.startswith("url("):
        return (120, 120, 120, alpha)
    if v.startswith("#"):
        h = v[1:]
        if len(h) == 3:
            h = "".join(c * 2 for c in h)
        if len(h) == 6:
            h += "ff"
        return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4, 6))[:3] + (alpha,)
    named = {"white": (255, 255, 255), "black": (0, 0, 0), "red": (255, 0, 0)}
    return named.get(v, (200, 200, 200)) + (alpha,)


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    src, dst = sys.argv[1], sys.argv[2]
    w = int(sys.argv[3]) if len(sys.argv) > 3 else 512
    bgc = (18, 22, 28, 255)
    render(open(src).read(), dst, w, bgc)
    print("wrote", dst)
