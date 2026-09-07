#!/usr/bin/env python3
"""Erzeugt die generierten String-Ressourcen (Charaktertexte der Grossregionen).

Aufruf: python3 tools/gen_strings.py <assets/pass_data.json> <app/src/main/res>

Deutsch kommt aus pass_data.json, Polnisch aus tools/i18n/macro_character.tsv.
Beide Dateien werden vollstaendig ueberschrieben - nicht von Hand editieren.
"""
import json
import os
import sys
from xml.sax.saxutils import escape

HEADER = ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          "<!-- GENERIERT von tools/gen_strings.py - nicht von Hand aendern. -->\n"
          "<resources>\n")


def android_escape(text):
    """XML-Escaping plus die Android-spezifischen Sonderfaelle."""
    text = escape(text)
    return text.replace("'", "\\'").replace("\"", "\\\"").replace("@", "\\@")


def write(path, entries):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(HEADER)
        for key, value in entries:
            fh.write('    <string name="%s">%s</string>\n' % (key, android_escape(value)))
        fh.write("</resources>\n")


def main():
    data_path, res_dir = sys.argv[1], sys.argv[2]
    data = json.load(open(data_path, encoding="utf-8"))
    tsv = os.path.join(os.path.dirname(os.path.abspath(__file__)), "i18n", "macro_character.tsv")
    pl = {}
    for line in open(tsv, encoding="utf-8"):
        if line.startswith("#") or "\t" not in line:
            continue
        slug, text = line.rstrip("\n").split("\t", 1)
        pl[slug] = text

    de_entries, pl_entries, missing = [], [], []
    for m in data["macroRegions"]:
        key = "char_" + m["slug"].replace("-", "_")
        de_entries.append((key, m["description"]))
        if m["slug"] in pl:
            pl_entries.append((key, pl[m["slug"]]))
        else:
            missing.append(m["slug"])
    if missing:
        sys.exit("Fehlende polnische Uebersetzungen: %s" % ", ".join(missing))

    write(os.path.join(res_dir, "values", "strings_regions.xml"), de_entries)
    write(os.path.join(res_dir, "values-pl", "strings_regions.xml"), pl_entries)

    # Statt getIdentifier() eine erzeugte Zuordnung: so bleiben die Verweise
    # fuer R8 sichtbar und shrinkResources entfernt die Texte nicht.
    kt_path = sys.argv[3]
    os.makedirs(os.path.dirname(kt_path), exist_ok=True)
    with open(kt_path, "w", encoding="utf-8") as fh:
        fh.write("package de.passderdeutschenberge.ui.text\n\n")
        fh.write("import androidx.annotation.StringRes\n")
        fh.write("import de.passderdeutschenberge.R\n\n")
        fh.write("// GENERIERT von tools/gen_strings.py - nicht von Hand aendern.\n")
        fh.write("private val CHARACTER_TEXTS: Map<String, Int> = mapOf(\n")
        for m in data["macroRegions"]:
            key = "char_" + m["slug"].replace("-", "_")
            fh.write('    "%s" to R.string.%s,\n' % (key, key))
        fh.write(")\n\n")
        fh.write("/** Charaktertext einer Grossregion; 0, wenn unbekannt. */\n")
        fh.write("@StringRes\n")
        fh.write("fun characterTextRes(resName: String): Int = CHARACTER_TEXTS[resName] ?: 0\n")
    print("geschrieben: %d Charaktertexte je Sprache + Kotlin-Zuordnung" % len(de_entries))


if __name__ == "__main__":
    main()
