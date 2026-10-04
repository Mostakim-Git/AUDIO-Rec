#!/usr/bin/env python3
"""
AUDIO-rec :: CSS flattener for the brand logo.

The official AUDIO-rec logo (assets/branding/audiorec-logo-user.svg) carries its
whole palette in a <style> block: `.g { fill: #ff9b20 }`, `.k { stroke: #000 }`
and friends.  Lightweight SVG consumers - resvg/usvg, cairosvg's default
configuration, Android's VectorDrawable importer, this repo's own
tools/svgpreview.py - understand *presentation attributes* but not CSS class
selectors, so without this pass the artwork renders as a black blob.

This tool walks the document, resolves every element / class / id selector in
document order (the normal CSS cascade: element < class < id) and writes the
winning declarations back as attributes.  The <style> element and any
class="..." attributes are dropped, so the result is a plain, dependency-free
SVG that any rasteriser can read.

    python3 tools/svg_flatten.py in.svg out.svg
"""
import re
import sys

TAG = re.compile(r"<(path|rect|circle|ellipse|polygon|polyline|g|line|text)\b([^>]*?)(/?)>", re.S)
STYLE = re.compile(r"<style\b[^>]*>(.*?)</style>", re.S)
RULE = re.compile(r"([^{}]+)\{([^{}]*)\}", re.S)
ATTR = re.compile(r"([\w:.-]+)\s*=\s*\"([^\"]*)\"")

# css property -> svg presentation attribute (they share names; this table only
# exists so unsupported properties are dropped instead of copied blindly).
KEEP = {
    "fill", "fill-rule", "fill-opacity", "stroke", "stroke-width",
    "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-opacity",
    "stroke-dasharray", "opacity", "clip-path", "clip-rule", "color",
    "paint-order", "vector-effect",
}


def parse_css(css):
    """-> (element_rules, class_rules, id_rules) as ordered dicts of dicts."""
    elem, cls, ident = {}, {}, {}
    for m in RULE.finditer(css):
        selectors = [s.strip() for s in m.group(1).split(",") if s.strip()]
        decls = {}
        for d in m.group(2).split(";"):
            if ":" not in d:
                continue
            prop, _, val = d.partition(":")
            prop, val = prop.strip().lower(), val.strip()
            if prop in KEEP and val:
                decls[prop] = val
        if not decls:
            continue
        for sel in selectors:
            if sel.startswith("."):
                cls.setdefault(sel[1:], {}).update(decls)
            elif sel.startswith("#"):
                ident.setdefault(sel[1:], {}).update(decls)
            else:
                elem.setdefault(sel.split()[0].lower(), {}).update(decls)
    return elem, cls, ident


def flatten(text):
    style = STYLE.search(text)
    elem, cls, ident = parse_css(style.group(1)) if style else ({}, {}, {})
    if style:
        text = text[:style.start()] + text[style.end():]

    def fix(m):
        name, attrs, slash = m.group(1), m.group(2), m.group(3)
        parsed = dict(ATTR.findall(attrs))
        classes = parsed.get("class", "").split()
        merged = {}
        merged.update(elem.get(name, {}))                 # element selector
        for c in classes:
            merged.update(cls.get(c, {}))                 # class selector
        if parsed.get("id"):
            merged.update(ident.get(parsed["id"], {}))    # id selector
        if not merged:
            return m.group(0)
        out = attrs
        for prop, val in merged.items():
            if re.search(r"\b%s\s*=" % re.escape(prop), out):
                continue                                   # already inline: leave it
            out += ' %s="%s"' % (prop, val)
        # classes have been baked into attributes; keeping them is harmless but
        # noisy, and a stray class rule elsewhere could double-apply.
        out = re.sub(r'\s*class\s*=\s*"[^"]*"', "", out)
        return "<%s%s%s>" % (name, out, slash)

    return TAG.sub(fix, text)


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    src, dst = argv[1], argv[2]
    text = open(src, encoding="utf-8").read()
    out = flatten(text)
    open(dst, "w", encoding="utf-8").write(out)
    print("flattened %s -> %s  (%d -> %d bytes)" % (src, dst, len(text), len(out)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
