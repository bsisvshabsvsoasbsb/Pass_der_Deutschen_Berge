#!/usr/bin/env python3
"""Extrahiert die Pass-Struktur aus dem Quell-PDF in JSON-Assets fuer die Android-App.

Aufruf:  python3 tools/extract_pass_data.py <pdf> <out-dir>
Benoetigt: pdftotext (poppler-utils)
"""
import json
import re
import subprocess
import sys
import unicodedata
from collections import OrderedDict

SYMBOLS = {
    "▲": "SUMMIT",     # Gipfel
    "◆": "PASS",       # Pass / Grat
    "≈": "WATER",      # Wasser / Wasserfall
    "⬡": "ROCK",       # Felsen / Hoehle
    "♧": "NATURE",     # Natur / Aussicht
    "⌂": "CASTLE",     # Burg / Schloss / Ort
    "➜": "TRAIL",      # Weg / Herausforderung
}
SYM_CLASS = "".join(SYMBOLS)

SECTION_HEADS = {
    "ÜBER DEN GIPFEL", "ÜBER DIE REGION", "LANDSCHAFT & CHARAKTER",
    "WICHTIGE GIPFEL", "ENTDECKUNGSREGIONEN", "BERGE IN DIESER REGION",
    "SAMMLUNGSZIELE", "NOTIZEN", "WENN DU SCHON MAL HIER BIST ...",
    "BURGEN, SCHLÖSSER & KULTUR", "NATUR & AUSSICHT", "WEGE & ERLEBNISSE",
    "FELSEN & HÖHLEN", "WASSER, QUELLEN & WASSERFÄLLE",
}
CATEGORY_HEADS = OrderedDict([
    ("BURGEN, SCHLÖSSER & KULTUR", "CASTLE"),
    ("NATUR & AUSSICHT", "NATURE"),
    ("WEGE & ERLEBNISSE", "TRAIL"),
    ("FELSEN & HÖHLEN", "ROCK"),
    ("WASSER, QUELLEN & WASSERFÄLLE", "WATER"),
    ("SAMMLUNGSZIELE", "COLLECTION"),
])
# Formularzeilen der Gipfelseite - beenden den Beschreibungstext
SUMMIT_FORM_STOPS = ("Datum der Besteigung", "Startpunkt", "AllTrails",
                     "Distanz", "Zeit", "Höhenmeter", "Meine Bewertung")
# Fusszeilen der Regionsseiten
REGION_TEXT_STOPS = ("Fortsetzung", "Alle Ziele der Umgebung", "Keine eigene Gipfelseite")

FOOTER_RE = re.compile(r"^PASS DER DEUTSCHEN BERGE\s*-\s*(?:DIGITAL A4|REGIONEN)\s+(\d+)\s*$")


def slugify(text):
    text = text.replace("ß", "ss")
    for a, b in (("ä", "ae"), ("ö", "oe"), ("ü", "ue")):
        text = text.replace(a, b).replace(a.upper(), b.upper())
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))
    text = re.sub(r"[^A-Za-z0-9]+", "-", text).strip("-").lower()
    return re.sub(r"-{2,}", "-", text)


def pdf_pages(pdf):
    out = subprocess.run(["pdftotext", "-layout", "-enc", "UTF-8", pdf, "-"],
                         check=True, capture_output=True, text=True).stdout
    return out.split("\f")


def lines_of(page):
    """Nicht-leere Zeilen ohne Footer, Original-Einrueckung erhalten."""
    res = []
    for raw in page.split("\n"):
        if not raw.strip():
            continue
        if FOOTER_RE.match(raw.strip()):
            continue
        res.append(raw.rstrip())
    return res


def paragraph_after(lines, head, stops):
    """Fliesstext zwischen Abschnittsueberschrift und naechstem Abschnitt.

    `stops` sind Zeilen-Praefixe; Formularzeilen stehen im PDF mit
    Spaltenabstand ("Datum der Besteigung        Startpunkt") und werden
    deshalb ueber den Praefix erkannt, nicht ueber Gleichheit.
    """
    try:
        i = next(n for n, l in enumerate(lines) if l.strip() == head)
    except StopIteration:
        return ""
    buf = []
    for l in lines[i + 1:]:
        s = l.strip()
        if s in SECTION_HEADS or any(s.startswith(p) for p in stops):
            break
        if re.match(r"^[" + SYM_CLASS + r"]", s):
            break
        buf.append(s)
    return re.sub(r"\s+", " ", " ".join(buf)).strip()


def parse_summit_page(page, pdf_page):
    lines = lines_of(page)
    if not lines:
        return None
    # Titel + rechtsbuendige Hoehe
    m = re.match(r"^(.*?)\s{2,}([\d.,]+)\s*m\s*$", lines[0].strip())
    if m:
        name, elev = m.group(1).strip(), m.group(2)
    else:
        name, elev = lines[0].strip(), None
    # Kopfzeile: "▲ Gipfel • <Grossregion>   Region: <Region> → S. <n>"
    macro = region = None
    region_page = None
    for l in lines[1:4]:
        s = l.strip()
        if s.startswith("▲"):
            left, _, right = s.partition("Region:")
            macro = left.split("•", 1)[1].strip() if "•" in left else None
            rm = re.match(r"\s*(.*?)\s*→\s*S\.\s*(\d+)\s*$", right)
            if rm:
                region, region_page = rm.group(1).strip(), int(rm.group(2))
            break
    nearby = []
    if "WENN DU SCHON MAL HIER BIST ..." in [l.strip() for l in lines]:
        i = next(n for n, l in enumerate(lines) if l.strip() == "WENN DU SCHON MAL HIER BIST ...")
        for l in lines[i + 1:]:
            s = l.strip()
            if s in SECTION_HEADS or s.startswith("Alle Ziele der Umgebung"):
                break
            em = re.match(r"^([" + SYM_CLASS + r"])\s+(.*?)(?:\s{2,}Datum)?\s*$", s)
            if em:
                nearby.append({"type": SYMBOLS[em.group(1)], "name": em.group(2).strip()})
    return {
        "id": "summit-%d" % pdf_page,
        "slug": slugify(name),
        "name": name,
        "elevationM": float(elev.replace(".", "").replace(",", ".")) if elev else None,
        "macroRegion": macro,
        "regionName": region,
        "regionPage": region_page,
        "page": pdf_page,
        "description": paragraph_after(lines, "ÜBER DEN GIPFEL", SUMMIT_FORM_STOPS),
        "nearby": nearby,
    }


def parse_entry_lines(lines, start, symbols_only=True):
    """Zieleintraege eines Abschnitts bis zur naechsten Ueberschrift."""
    out = []
    for l in lines[start:]:
        s = l.strip()
        if s in SECTION_HEADS or s.startswith("Fortsetzung auf der n"):
            break
        m = re.match(r"^([" + SYM_CLASS + r"])\s+(.*?)"
                     r"(?:\s{2,}S\.\s*(\d+))?(?:\s{2,}Datum)?\s*$", s)
        if m:
            e = {"type": SYMBOLS[m.group(1)], "name": m.group(2).strip()}
            if m.group(3):
                e["sourcePage"] = int(m.group(3))
            out.append(e)
        elif not symbols_only:
            out.append({"type": None, "name": s})
    return out


def parse_macro_page(page, pdf_page):
    lines = lines_of(page)
    name = lines[0].strip()
    summits, regions = [], []
    heads = {l.strip(): n for n, l in enumerate(lines)}
    if "WICHTIGE GIPFEL" in heads:
        summits = parse_entry_lines(lines, heads["WICHTIGE GIPFEL"] + 1)
        for l in lines[heads["WICHTIGE GIPFEL"] + 1:]:
            s = l.strip()
            if s in SECTION_HEADS:
                break
            m = re.match(r"^[" + SYM_CLASS + r"]\s+(.*?)\s{2,}S\.\s*(\d+)\s*$", s)
            if m:
                for e in summits:
                    if e["name"] == m.group(1).strip():
                        e["page"] = int(m.group(2))
    if "ENTDECKUNGSREGIONEN" in heads:
        for l in lines[heads["ENTDECKUNGSREGIONEN"] + 1:]:
            s = l.strip()
            if s in SECTION_HEADS:
                break
            m = re.match(r"^(.*?)\s{2,}(\d+)(?:\s*-\s*(\d+))?\s*$", s)
            if m:
                regions.append({
                    "name": m.group(1).strip(),
                    "pageFrom": int(m.group(2)),
                    "pageTo": int(m.group(3) or m.group(2)),
                })
    return {
        "id": "macro-%d" % pdf_page,
        "slug": slugify(name),
        "name": name,
        "page": pdf_page,
        "description": paragraph_after(lines, "LANDSCHAFT & CHARAKTER", REGION_TEXT_STOPS),
        "keySummits": summits,
        "regionRefs": regions,
    }


def parse_region_page(page, pdf_page):
    lines = lines_of(page)
    name = lines[0].strip()
    sub = lines[1].strip() if len(lines) > 1 else ""
    macro = sub.split("•")[0].strip()
    part = 1
    total = 1
    pm = re.search(r"REGIONALSEITE (\d+)/(\d+)", sub)
    if pm:
        part, total = int(pm.group(1)), int(pm.group(2))
    heads = {}
    for n, l in enumerate(lines):
        s = l.strip()
        if s in SECTION_HEADS and s not in heads:
            heads[s] = n
    summits = []
    if "BERGE IN DIESER REGION" in heads:
        for l in lines[heads["BERGE IN DIESER REGION"] + 1:]:
            s = l.strip()
            if s in SECTION_HEADS:
                break
            m = re.match(r"^[" + SYM_CLASS + r"]\s+(.*?)\s{2,}S\.\s*(\d+)\s*$", s)
            if m:
                summits.append({"name": m.group(1).strip(), "page": int(m.group(2))})
    targets = []
    for head, cat in CATEGORY_HEADS.items():
        if head in heads:
            for e in parse_entry_lines(lines, heads[head] + 1):
                e["category"] = cat
                targets.append(e)
    return {
        "name": name,
        "macroRegion": macro,
        "part": part,
        "partsTotal": total,
        "page": pdf_page,
        "description": paragraph_after(lines, "ÜBER DIE REGION", REGION_TEXT_STOPS),
        "summitRefs": summits,
        "targets": targets,
    }


def main():
    pdf, outdir = sys.argv[1], sys.argv[2]
    pages = pdf_pages(pdf)
    summits, macros, region_parts = [], [], []
    for idx, page in enumerate(pages, start=1):
        if "ÜBER DEN GIPFEL" in page:
            s = parse_summit_page(page, idx)
            if s:
                summits.append(s)
        elif "REGIONSÜBERSICHT" in page[:300]:
            macros.append(parse_macro_page(page, idx))
        elif "ÜBER DIE REGION" in page or "Fortsetzung der Entdeckungsziele" in page:
            region_parts.append(parse_region_page(page, idx))

    # Mehrseitige Regionen zusammenfuehren
    regions = []
    for rp in region_parts:
        if rp["part"] == 1 or not regions or regions[-1]["name"] != rp["name"]:
            regions.append({
                "id": "region-%d" % rp["page"],
                "slug": slugify(rp["name"]),
                "name": rp["name"],
                "macroRegion": rp["macroRegion"],
                "page": rp["page"],
                "pages": [rp["page"]],
                "description": rp["description"],
                "summitRefs": list(rp["summitRefs"]),
                "targets": list(rp["targets"]),
            })
        else:
            cur = regions[-1]
            cur["pages"].append(rp["page"])
            cur["summitRefs"].extend(rp["summitRefs"])
            cur["targets"].extend(rp["targets"])

    # Ziel-IDs vergeben + Verknuepfungen aufloesen
    by_page = {s["page"]: s for s in summits}
    for r in regions:
        seen = set()
        merged = []
        for t in r["targets"]:
            key = (t["name"], t["category"])
            if key in seen:
                continue
            seen.add(key)
            t["id"] = "%s--%s" % (r["id"], slugify(t["name"]))
            merged.append(t)
        r["targets"] = merged
        for sr in r["summitRefs"]:
            s = by_page.get(sr["page"])
            sr["id"] = s["id"] if s else None
    region_by_page = {}
    for r in regions:
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
            rr["id"] = r["id"] if r else None
            if r:
                m["regionIds"].append(r["id"])
                r["macroRegionId"] = m["id"]
        for ks in m["keySummits"]:
            s = by_page.get(ks.get("page"))
            ks["id"] = s["id"] if s else None
    # Fallback: Uebersichtsseiten kappen die Liste ("+ N weitere Unterregionen"),
    # daher Restzuordnung ueber den Grossregionsnamen der Regionsseite.
    for r in regions:
        if "macroRegionId" not in r:
            m = macro_by_name.get(r["macroRegion"])
            r["macroRegionId"] = m["id"] if m else None
            if m and r["id"] not in m["regionIds"]:
                m["regionIds"].append(r["id"])
    for m in macros:
        order = {rid: n for n, rid in enumerate(m["regionIds"])}
        m["regionIds"].sort(key=lambda rid: order[rid])
    for s in summits:
        m = macro_by_name.get(s["macroRegion"])
        s["macroRegionId"] = m["id"] if m else None

    import os
    os.makedirs(outdir, exist_ok=True)
    data = {"macroRegions": macros, "regions": regions, "summits": summits}
    for key, val in data.items():
        with open(os.path.join(outdir, key + ".json"), "w", encoding="utf-8") as fh:
            json.dump(val, fh, ensure_ascii=False, indent=1)
    stats = {
        "macroRegions": len(macros),
        "regions": len(regions),
        "summits": len(summits),
        "targets": sum(len(r["targets"]) for r in regions),
        "regionsWithoutMacroId": sum(1 for r in regions if not r.get("macroRegionId")),
        "summitsWithoutRegionId": sum(1 for s in summits if not s.get("regionId")),
        "summitsWithoutElevation": sum(1 for s in summits if s["elevationM"] is None),
    }
    print(json.dumps(stats, indent=1))


if __name__ == "__main__":
    main()
