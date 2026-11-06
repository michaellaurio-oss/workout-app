"""Builds the app's starter data.

Usage: python web/to_seed.py "exercise catalog.xlsx" app/src/main/assets
Reads the LIST tab of the spreadsheet (the exercise list) and web/sessions.json (his history, not
committed), and writes catalog.json (committed) and history.json (git-ignored).
Bump CATALOG_VERSION whenever the exercise list changes, so phones that already have the app update.
"""
import json, os, re, sys

CATALOG_VERSION = 2
HERE = os.path.dirname(os.path.abspath(__file__))

AREAS = [  # id, label, target days, kind  (targets: Area Targets tab)
    ("chest", "Chest", 4, "strength"), ("back", "Back", 4, "strength"), ("shoulders", "Shoulders", 4, "strength"),
    ("arms", "Arms", 5, "strength"), ("legs", "Legs", 4, "strength"), ("glutes", "Glutes & Hamstrings", 4, "strength"),
    ("core", "Core", 3, "strength"), ("conditioning", "Conditioning", 3, "conditioning"), ("mobility", "Mobility", 2, "mobility"),
]
AREA_ID = {label.lower(): i for i, label, _, _ in AREAS}
MEASURES = ["sets", "reps", "weight", "time", "band", "resistance", "speed", "incline"]

# Names in the LIST tab that differ from the ids already used in his history.
ALIAS = {"cable chest press (single)": "cable-chest", "cable pulls (single)": "cable-pull",
         "tricep extensions (single)": "tricep-ext", "kb get up": "kb-getup", "kb two-hand swing": "kb-swing",
         "heavy bag workout": "heavy-bag", "crosscore split squats": "xc-split-squat"}
# Dropped from the LIST tab but used in history: kept hidden so old workouts keep their names.
HIDDEN = [("xc-shoulders", "Crosscore shoulders", ["shoulders"], "Crosscore", ["sets", "reps", "weight"]),
          ("bosu-split-squat", "Bosu split squats", ["legs", "glutes"], "Bosu", ["sets", "reps", "weight"]),
          ("step-over", "Step-overs", ["legs", "mobility"], "", ["sets", "reps"]),
          ("xc-core", "Crosscore core", ["core"], "Crosscore", ["sets", "reps"]),
          ("hold-lunge", "Hold lunge", ["mobility"], "", ["sets", "time"]),
          ("shoulder-mobility", "Shoulder mobility", ["mobility", "shoulders"], "", ["sets", "reps"]),
          ("side-split", "Side split", ["mobility"], "", ["sets", "time"]),
          ("stretch", "General stretch", ["mobility"], "", ["time"])]
RETIRED = ["incline-row"]  # dropped and never logged


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def catalog_from_xlsx(path, old_ids):
    import openpyxl
    rows = [r for r in openpyxl.load_workbook(path, read_only=True)["LIST"].iter_rows(min_row=2, values_only=True) if r[1]]
    by_name = {}
    exercises = []
    for main, name, tool, also, measures in (tuple(r[:5]) for r in rows):
        name = re.sub(r"\s+", " ", name).strip()
        key = name.lower()
        ex_id = ALIAS.get(key) or old_ids.get(key) or slug(name)
        areas = [AREA_ID[main.strip().lower()]]
        for a in (also or "").split(","):
            a = a.strip().lower()
            if a and AREA_ID[a] not in areas: areas.append(AREA_ID[a])
        ms = [m.strip().lower() for m in (measures or "time").split("/") if m.strip()]
        assert all(m in MEASURES for m in ms), (name, ms)
        tool = (tool or "").strip()
        exercises.append({"id": ex_id, "name": name, "areas": areas, "equip": tool[:1].upper() + tool[1:], "measures": ms})
    for i, n, a, e, ms in HIDDEN:
        exercises.append({"id": i, "name": n, "areas": a, "equip": e, "measures": ms, "hidden": True})
    ids = [e["id"] for e in exercises]
    assert len(ids) == len(set(ids)), "duplicate ids"
    return exercises


def parse(detail, measures):
    """'4x12 @40#' -> [{'sets':4,'reps':12,'weight':40}], note. Seconds/minutes go to 'time'."""
    groups, notes = [], []
    for part in [p.strip() for p in detail.split(",") if p.strip()]:
        m = re.match(r"^(\d+)x(\d+)(s)?(?:\s*@\s*(\d+(?:\.\d+)?)#)?\s*(.*)$", part)
        if m:
            s, r, sec, w, rest = m.groups()
            if sec: groups.append({"sets": int(s), "time": int(r)})
            else:
                g = {"sets": int(s), "reps": int(r)}
                if w: g["weight"] = float(w)
                groups.append(g)
            if rest: notes.append(rest)
            continue
        m = re.match(r"^(\d+(?:\.\d+)?)#$", part)
        if m: groups.append({"weight": float(m.group(1))}); continue
        m = re.match(r"^(\d+)\s*min$", part)
        if m: groups.append({"time": int(m.group(1)) * 60}); continue
        m = re.match(r"^(\d+)$", part)
        if m:  # a bare number: seconds for timed movements, otherwise reps
            groups.append({"time": int(part)} if "reps" not in measures and "time" in measures else {"reps": int(part)}); continue
        m = re.match(r"^clean x(\d+)$", part)
        if m: groups.append({"reps": int(m.group(1))}); notes.append("clean only"); continue
        notes.append(part)
    return groups, " ".join(notes)


def main(xlsx, out):
    old = json.load(open(os.path.join(HERE, "catalog.json")))
    old_ids = {e["name"].lower(): e["id"] for e in old["exercises"]}
    exercises = catalog_from_xlsx(xlsx, old_ids)
    measures = {e["id"]: e["measures"] for e in exercises}
    catalog = {"catalogVersion": CATALOG_VERSION, "retired": RETIRED,
               "areas": [dict(id=i, label=l, target=t, kind=k) for i, l, t, k in AREAS], "exercises": exercises}
    json.dump(catalog, open(os.path.join(out, "catalog.json"), "w"), indent=1)
    json.dump(catalog, open(os.path.join(HERE, "catalog.json"), "w"), indent=1)

    sessions_path = os.path.join(HERE, "sessions.json")
    if os.path.exists(sessions_path):
        ses = json.load(open(sessions_path))
        for s in ses:
            for it in s["items"]:
                assert it["ex"] in measures, it["ex"]
                d = it.pop("detail", "")
                it["groups"], it["note"] = parse(d, measures[it["ex"]])
            s.setdefault("note", "")
        json.dump({"sessions": ses}, open(os.path.join(out, "history.json"), "w"), indent=1)
    print(len(exercises), "exercises,", sum(not e.get("hidden") for e in exercises), "shown")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
