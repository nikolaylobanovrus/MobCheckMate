"""Convert simple SVG files to Android VectorDrawable XML.

Supports path, circle, ellipse, line, polygon, polyline and rect elements with presentation attributes or
inline `style` (fill, stroke, stroke width, caps, joins, miter limit, fill rule). Transforms are not supported.
Used for the Chessnut piece set; see docs/THIRD_PARTY_NOTICES.md.

Usage: python3 tools/svg_to_vector_drawable.py input.svg output.xml "<!-- header comment -->"
"""
import re
import sys
import xml.etree.ElementTree as ET

NS = "{http://www.w3.org/2000/svg}"
INHERITED = {"fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "fill-rule", "display"}


def styles(el, parent):
    st = {k: v for k, v in parent.items() if k in INHERITED}
    for key in INHERITED:
        if key in el.attrib:
            st[key] = el.attrib[key]
    for part in el.attrib.get("style", "").split(";"):
        if ":" in part:
            k, v = part.split(":", 1)
            st[k.strip()] = v.strip()
    return st


def num(v):
    return float(v)


def fmt(x):
    return ("%.3f" % x).rstrip("0").rstrip(".")


def shape_path(el):
    tag = el.tag.replace(NS, "")
    a = el.attrib
    if tag == "path":
        return re.sub(r"\s+", " ", a["d"]).strip()
    if tag in ("circle", "ellipse"):
        cx, cy = num(a.get("cx", 0)), num(a.get("cy", 0))
        rx = num(a["r"]) if tag == "circle" else num(a["rx"])
        ry = num(a["r"]) if tag == "circle" else num(a["ry"])
        return (f"M{fmt(cx - rx)},{fmt(cy)} a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(2 * rx)},0 "
                f"a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(-2 * rx)},0 z")
    if tag == "line":
        return f"M{a['x1']},{a['y1']} L{a['x2']},{a['y2']}"
    if tag == "polygon" or tag == "polyline":
        pts = re.split(r"[\s,]+", a["points"].strip())
        pairs = [f"{pts[i]},{pts[i + 1]}" for i in range(0, len(pts) - 1, 2)]
        return "M" + " L".join(pairs) + (" z" if tag == "polygon" else "")
    if tag == "rect":
        x, y, w, h = (num(a.get(k, 0)) for k in ("x", "y", "width", "height"))
        return f"M{fmt(x)},{fmt(y)} h{fmt(w)} v{fmt(h)} h{fmt(-w)} z"
    return None


def color(v):
    v = v.strip()
    if v.startswith("#") and len(v) == 4:
        v = "#" + "".join(c * 2 for c in v[1:])
    return v.upper()


def walk(el, parent, out):
    st = styles(el, parent)
    if st.get("display") == "none":
        return
    d = shape_path(el)
    if d is not None:
        attrs = [f'android:pathData="{d}"']
        fill = st.get("fill", "#000000")
        if fill != "none":
            attrs.append(f'android:fillColor="{color(fill)}"')
            if st.get("fill-rule") == "evenodd":
                attrs.append('android:fillType="evenOdd"')
        stroke = st.get("stroke", "none")
        if stroke != "none":
            attrs.append(f'android:strokeColor="{color(stroke)}"')
            attrs.append(f'android:strokeWidth="{fmt(num(st.get("stroke-width", "1")))}"')
            if "stroke-linecap" in st:
                attrs.append(f'android:strokeLineCap="{st["stroke-linecap"]}"')
            if "stroke-linejoin" in st:
                attrs.append(f'android:strokeLineJoin="{st["stroke-linejoin"]}"')
            if "stroke-miterlimit" in st:
                attrs.append(f'android:strokeMiterLimit="{fmt(num(st["stroke-miterlimit"]))}"')
        if fill != "none" or stroke != "none":
            out.append("    <path\n        " + "\n        ".join(attrs) + " />")
    for child in el:
        walk(child, st, out)


def convert(src, header):
    root = ET.parse(src).getroot()
    vb = root.attrib["viewBox"].split()
    out = []
    for child in root:
        walk(child, {}, out)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n' + header + "\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="48dp"\n    android:height="48dp"\n'
        f'    android:viewportWidth="{vb[2]}"\n    android:viewportHeight="{vb[3]}">\n'
        + "\n".join(out) + "\n</vector>\n"
    )


if __name__ == "__main__":
    src, dst, header = sys.argv[1], sys.argv[2], sys.argv[3]
    with open(dst, "w") as f:
        f.write(convert(src, header))
