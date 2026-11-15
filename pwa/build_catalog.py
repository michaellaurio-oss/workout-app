"""Builds the web app's starting exercise list (pwa/catalog.json) from a spreadsheet.

Usage: python pwa/build_catalog.py "gym exercise list.xlsx"
Same LIST-tab columns as the phone app's list (see web/to_seed.py). Bump CATALOG_VERSION when the list changes.
"""
import json, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "web"))
import to_seed

CATALOG_VERSION = 2

def main(xlsx):
    to_seed.HIDDEN = []  # those are the home-gym history's old movements
    exercises = to_seed.catalog_from_xlsx(xlsx, {})
    out = {"catalogVersion": CATALOG_VERSION, "restDays": [], "sessions": [],
           "areas": [dict(id=i, label=l, target=t, kind=k, credits=2.0) for i, l, t, k in to_seed.AREAS],
           "exercises": exercises}
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "catalog.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=1, ensure_ascii=False)
    print(len(exercises), "movements ->", path)

if __name__ == "__main__":
    main(sys.argv[1])
