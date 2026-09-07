#!/usr/bin/env python3
"""Baut aus PDF-Extrakt + Geodaten die App-Assets.

Aufruf: python3 tools/build_assets.py <pdf> <ne_admin1.geojson> <assets-out-dir>

Erzeugt:
  pass_data.json      Pass-Hierarchie mit strukturierten (uebersetzbaren) Textbausteinen
  germany.json        Bundeslandpolygone (Natural Earth, Public Domain), vereinfacht
"""
import json
import math
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import extract_pass_data as ex  # noqa: E402

# Fokus-Bausteine der Regionstexte -> Schluessel (in strings.xml uebersetzt)
FOCUS_ATOMS = [
    ("Burgen, Schlösser und Kulturorte", "castle"),
    ("Seen, Quellen und Wasserfälle", "water"),
    ("Natur- und Aussichtspunkte", "nature"),
    ("Felsen und Höhlen", "rock"),
    ("Wanderwege und Herausforderungen", "trail"),
    ("abwechslungsreiche Natur- und Kulturziele", "mixed"),
]
SUMMIT_RE = re.compile(
    r"^(?P<name>.+?) liegt in der Tourengruppe (?P<region>.+?)\. "
    r"(?:Der Gipfel erreicht (?P<h>[\d.,]+) m\. )?"
    r"(?P<char>.*?)"
    r"(?: Besonders gut lässt sich die Tour mit (?P<nearby>.+?) verbinden\.)?"
    r" Je nach Ausgangspunkt kann die Besteigung als kurze Gipfeltour oder als Teil "
    r"einer längeren Rundwanderung geplant werden\.$"
)
REGION_RE = re.compile(
    r"^(?P<char>.*?)"
    r" In der Tourengruppe (?P<region>.+?) stehen (?P<focus>.+?) im Mittelpunkt\."
    r"(?: Zu den markanten Zielen gehören (?P<top>.+?)\.)?"
    r"(?: Die wichtigsten Gipfel sind (?P<peaks>.+?)\.)?$"
)
COLLECTION_RE = re.compile(
    r"^Diese Spezial-Sammlung bündelt .*?"
    r"(?:Zu den enthaltenen Zielen gehören (?P<top>.+?)\.)?$"
)


# Kleinschreibung bleibt erhalten bei Bindewoertern und Praepositionen, die in
# den Gebietsnamen des Passes vorkommen.
LOWER_WORDS = {"und", "bei", "am", "an", "im", "in", "der", "die", "das", "vom", "von", "zu", "zur"}


def title_case(name):
    """'OVERATH / BERGISCHES LAND' -> 'Overath / Bergisches Land'."""
    out = []
    for i, token in enumerate(name.split(" ")):
        if not token or not token.isupper():
            out.append(token)
            continue
        low = token.lower()
        if i > 0 and low in LOWER_WORDS:
            out.append(low)
        else:
            # Bindestrich- und Schraegstrich-Bestandteile einzeln umsetzen.
            out.append(re.sub(r"[^\W\d_]+", lambda m: m.group(0).capitalize(), low))
    return " ".join(out)


def split_focus(phrase):
    """Fokusphrase in Atom-Schluessel zerlegen; None wenn nicht vollstaendig zerlegbar."""
    rest = phrase
    keys = []
    for text, key in FOCUS_ATOMS:
        if text in rest:
            keys.append(key)
            rest = rest.replace(text, "", 1)
    if re.sub(r"[,\s]|und", "", rest):
        return None
    order = {k: n for n, (_, k) in enumerate(FOCUS_ATOMS)}
    return sorted(set(keys), key=lambda k: phrase.index(
        next(t for t, kk in FOCUS_ATOMS if kk == k)))


def split_list(text):
    """'A, B und C' -> ['A','B','C'] (Zielnamen enthalten selbst keine ' und ')."""
    parts = re.split(r",\s*| und ", text)
    return [p.strip() for p in parts if p.strip()]


def simplify(points, tol):
    """Douglas-Peucker, iterativ (kein Rekursionslimit bei langen Ringen)."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        lo, hi = stack.pop()
        if hi <= lo + 1:
            continue
        ax, ay = points[lo]
        bx, by = points[hi]
        dx, dy = bx - ax, by - ay
        norm = math.hypot(dx, dy)
        best, best_i = -1.0, -1
        for i in range(lo + 1, hi):
            px, py = points[i]
            if norm == 0:
                d = math.hypot(px - ax, py - ay)
            else:
                d = abs(dy * px - dx * py + bx * ay - by * ax) / norm
            if d > best:
                best, best_i = d, i
        if best > tol:
            keep[best_i] = True
            stack.append((lo, best_i))
            stack.append((best_i, hi))
    return [p for p, k in zip(points, keep) if k]


def build_germany(geojson_path, tol=0.004):
    gj = json.load(open(geojson_path, encoding="utf-8"))
    out = []
    for f in gj["features"]:
        p = f["properties"]
        if p.get("adm0_a3") != "DEU":
            continue
        geom = f["geometry"]
        polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
        rings = []
        for poly in polys:
            for ring in poly:
                pts = [(round(x, 4), round(y, 4)) for x, y in ring]
                pts = simplify(pts, tol)
                if len(pts) >= 4:
                    rings.append([c for pt in pts for c in pt])
        if rings:
            out.append({
                "code": p.get("iso_3166_2") or p.get("postal") or ex.slugify(p["name"]),
                "name": p["name"],
                "rings": rings,
            })
    out.sort(key=lambda s: s["name"])
    return out


def main():
    pdf, geojson, outdir = sys.argv[1], sys.argv[2], sys.argv[3]
    pages = ex.pdf_pages(pdf)

    summits, macros, region_parts = [], [], []
    for idx, page in enumerate(pages, start=1):
        if "ÜBER DEN GIPFEL" in page:
            s = ex.parse_summit_page(page, idx)
            if s:
                summits.append(s)
        elif "REGIONSÜBERSICHT" in page[:300]:
            macros.append(ex.parse_macro_page(page, idx))
        elif "ÜBER DIE REGION" in page or "Fortsetzung der Entdeckungsziele" in page:
            region_parts.append(ex.parse_region_page(page, idx))

    regions = []
    for rp in region_parts:
        if rp["part"] == 1 or not regions or regions[-1]["name"] != rp["name"]:
            regions.append({
                "id": "region-%d" % rp["page"], "slug": ex.slugify(rp["name"]),
                "name": rp["name"], "macroRegion": rp["macroRegion"], "page": rp["page"],
                "pages": [rp["page"]], "description": rp["description"],
                "summitRefs": list(rp["summitRefs"]), "targets": list(rp["targets"]),
            })
        else:
            regions[-1]["pages"].append(rp["page"])
            regions[-1]["summitRefs"].extend(rp["summitRefs"])
            regions[-1]["targets"].extend(rp["targets"])

    by_page = {s["page"]: s for s in summits}
    region_by_page = {}
    for r in regions:
        for t in r["targets"]:
            t["id"] = "%s--%s" % (r["id"], ex.slugify(t["name"]))
        for sr in r["summitRefs"]:
            sr["id"] = by_page[sr["page"]]["id"] if sr["page"] in by_page else None
        for p in r["pages"]:
            region_by_page[p] = r
    for s in summits:
        r = region_by_page.get(s["regionPage"])
        s["regionId"] = r["id"] if r else None
    macro_by_name = {m["name"]: m for m in macros}
    for m in macros:
        m["regionIds"] = []
        for rr in m["regionRefs"]:
            r = region_by_page.get(rr["pageFrom"])
            if r:
                m["regionIds"].append(r["id"])
                r["macroRegionId"] = m["id"]
        for ks in m["keySummits"]:
            ks["id"] = by_page[ks["page"]]["id"] if ks.get("page") in by_page else None
    for r in regions:
        if "macroRegionId" not in r:
            m = macro_by_name.get(r["macroRegion"])
            r["macroRegionId"] = m["id"] if m else None
            if m and r["id"] not in m["regionIds"]:
                m["regionIds"].append(r["id"])
    # Die Kopfzeile der Gipfelseite nennt die Grossregion in gemischter
    # Schreibweise, die Uebersichtsseite in Versalien - deshalb nicht ueber den
    # Namen verknuepfen, sondern ueber die Region des Gipfels.
    rid_all = {r["id"]: r for r in regions}
    for s in summits:
        r = rid_all.get(s["regionId"])
        s["macroRegionId"] = r["macroRegionId"] if r else None

    # Anzeigenamen: Seitenueberschriften stehen in Versalien, die Index- und
    # Listeneintraege in gemischter Schreibweise. Fuer die UI die lesbare Form
    # bevorzugen und nur zur Not selbst umsetzen.
    display = {}
    for m in macros:
        for rr in m["regionRefs"]:
            r = region_by_page.get(rr["pageFrom"])
            if r:
                display[r["id"]] = rr["name"]
        for ks in m["keySummits"]:
            if ks.get("id"):
                display[ks["id"]] = ks["name"]
    for r in regions:
        for sr in r["summitRefs"]:
            if sr.get("id"):
                display.setdefault(sr["id"], sr["name"])
    for item in macros + regions + summits:
        item["displayName"] = display.get(item["id"]) or title_case(item["name"])

    # --- Textbausteine strukturieren (fuer DE/PL-Rendering zur Laufzeit) ---
    stats = {"summitTemplate": 0, "summitRaw": 0, "regionTemplate": 0,
             "regionCollection": 0, "regionRaw": 0}
    for s in summits:
        m = SUMMIT_RE.match(s["description"])
        if m:
            s["text"] = {"kind": "summit",
                         "nearby": split_list(m.group("nearby")) if m.group("nearby") else []}
            stats["summitTemplate"] += 1
        else:
            s["text"] = {"kind": "raw", "de": s["description"]}
            stats["summitRaw"] += 1
        del s["description"]
    for r in regions:
        m = REGION_RE.match(r["description"])
        focus = split_focus(m.group("focus")) if m else None
        if m and focus is not None:
            r["text"] = {"kind": "region", "focus": focus,
                         "top": split_list(m.group("top")) if m.group("top") else [],
                         "peaks": split_list(m.group("peaks")) if m.group("peaks") else []}
            stats["regionTemplate"] += 1
        else:
            c = COLLECTION_RE.match(r["description"])
            if c:
                r["text"] = {"kind": "collection",
                             "top": split_list(c.group("top")) if c.group("top") else []}
                stats["regionCollection"] += 1
            else:
                r["text"] = {"kind": "raw", "de": r["description"]}
                stats["regionRaw"] += 1
        del r["description"]

    # --- Geodaten anlagern ---
    geo = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                      "geo", "region_coordinates.json"),
                         encoding="utf-8"))["regions"]
    for r in regions:
        c = geo.get(r["name"])
        r["lat"], r["lon"] = (c[0], c[1]) if c else (None, None)
    for m in macros:
        pts = [(regions_by_id[i]["lat"], regions_by_id[i]["lon"])
               for regions_by_id in [{r["id"]: r for r in regions}]
               for i in m["regionIds"]
               if regions_by_id[i]["lat"] is not None]
        if pts:
            m["lat"] = round(sum(p[0] for p in pts) / len(pts), 4)
            m["lon"] = round(sum(p[1] for p in pts) / len(pts), 4)
            m["bbox"] = [min(p[0] for p in pts), min(p[1] for p in pts),
                         max(p[0] for p in pts), max(p[1] for p in pts)]
        else:
            m["lat"] = m["lon"] = None
            m["bbox"] = None
        m["virtual"] = m["lat"] is None
    rid = {r["id"]: r for r in regions}
    for s in summits:
        r = rid.get(s["regionId"])
        s["lat"], s["lon"] = (r["lat"], r["lon"]) if r else (None, None)

    for m in macros:
        m.pop("regionRefs", None)
    for r in regions:
        r.pop("macroRegion", None)
    for s in summits:
        for k in ("macroRegion", "regionName", "regionPage", "nearby"):
            s.pop(k, None)

    os.makedirs(outdir, exist_ok=True)
    data = {
        "schema": 1,
        "source": os.path.basename(pdf),
        "macroRegions": macros,
        "regions": regions,
        "summits": summits,
    }
    with open(os.path.join(outdir, "pass_data.json"), "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, separators=(",", ":"))
    germany = build_germany(geojson)
    with open(os.path.join(outdir, "germany.json"), "w", encoding="utf-8") as fh:
        json.dump({"attribution": "Natural Earth (public domain), ne_10m_admin_1_states_provinces",
                   "states": germany}, fh, ensure_ascii=False, separators=(",", ":"))

    stats.update({
        "macroRegions": len(macros), "regions": len(regions), "summits": len(summits),
        "targets": sum(len(r["targets"]) for r in regions),
        "regionsWithoutCoords": sum(1 for r in regions if r["lat"] is None),
        "states": len(germany),
        "statePoints": sum(len(x) // 2 for s in germany for x in s["rings"]),
        "passDataKB": os.path.getsize(os.path.join(outdir, "pass_data.json")) // 1024,
        "germanyKB": os.path.getsize(os.path.join(outdir, "germany.json")) // 1024,
    })
    print(json.dumps(stats, indent=1))


if __name__ == "__main__":
    main()
