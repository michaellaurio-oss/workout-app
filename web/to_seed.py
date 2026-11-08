"""Builds the app's starter data.

Usage: python web/to_seed.py "exercise catalog.xlsx" app/src/main/assets
Reads the LIST tab of the spreadsheet (the exercise list) and web/sessions.json (his history, not
committed), and writes catalog.json (committed) and history.json (git-ignored).
Bump CATALOG_VERSION whenever the exercise list changes, so phones that already have the app update.
"""
import json, os, re, sys

CATALOG_VERSION = 8
HERE = os.path.dirname(os.path.abspath(__file__))

AREAS = [  # id, label, target days, kind  (targets: Area Targets tab)
    ("chest", "Chest", 4, "strength"), ("back", "Back", 4, "strength"), ("shoulders", "Shoulders", 4, "strength"),
    ("arms", "Arms", 5, "strength"), ("legs", "Legs", 4, "strength"), ("glutes", "Glutes & Hamstrings", 4, "strength"),
    ("core", "Core", 3, "strength"), ("conditioning", "Conditioning", 3, "conditioning"), ("mobility", "Mobility", 2, "mobility"),
]
AREA_ID = {label.lower(): i for i, label, _, _ in AREAS}
# Other words he uses in "Also works" for an area.
AREA_ID.update({"triceps": "arms", "biceps": "arms", "glutes": "glutes", "hamstrings": "glutes"})
MEASURES = ["sets", "reps", "weight", "time", "band", "resistance", "speed", "incline"]

# Names in the LIST tab that differ from the ids already used in his history.
ALIAS = {"cable chest press (single)": "cable-chest", "cable pulls (single)": "cable-pull",
         "tricep extensions (single)": "tricep-ext", "kb get up": "kb-getup", "kb two-hand swing": "kb-swing",
         "heavy bag workout": "heavy-bag", "crosscore split squats": "xc-split-squat"}
# Dropped from the LIST tab but used in history: kept hidden so old workouts keep their names.
HIDDEN = [("xc-shoulders", "Crosscore shoulders", ["shoulders"], "Crosscore", ["sets", "reps", "weight"]),
          ("step-over", "Step-overs", ["legs", "mobility"], "", ["sets", "reps"]),
          ("xc-core", "Crosscore core", ["core"], "Crosscore", ["sets", "reps"]),
          ("hold-lunge", "Hold lunge", ["mobility"], "", ["sets", "time"]),
          ("shoulder-mobility", "Shoulder mobility", ["mobility", "shoulders"], "", ["sets", "reps"]),
          ("side-split", "Side split", ["mobility"], "", ["sets", "time"]),
          ("stretch", "General stretch", ["mobility"], "", ["time"])]
# LIST tab columns: A main area, B exercise, C tool, D also works, E measures,
# F every session (x), G always first (x), H season (e.g. "Jun-Sep", blank = all year),
# plus a "Video" column (found by its header) with a link to an instruction video.
EVERY_SESSION = set()  # extra every-session ids beyond column F
# Settings the LIST tab has no column for. (Pinned and season now live in columns G and H.)
OVERRIDES = {}
MONTHS = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"]
YES = ("x", "yes", "y", "true", "1")


def parse_season(text):
    """'Jun-Sep' -> [6,7,8,9]; 'Dec-Feb' wraps; 'Jan, Mar' -> [1,3]; blank -> [] (all year)."""
    months = []
    for part in (text or "").replace("–", "-").split(","):
        part = part.strip().lower()
        if not part: continue
        a, _, b = part.partition("-")
        start, end = MONTHS.index(a.strip()[:3]) + 1, MONTHS.index((b or a).strip()[:3]) + 1
        m = start
        while True:
            months.append(m)
            if m == end: break
            m = m % 12 + 1
    return sorted(set(months))
RETIRED = ["incline-row"]  # dropped and never logged
# Old id -> the movement it was folded into (history moves over). Confirmed by Michael.
MERGED = {"bosu-split-squat": "xc-split-squat"}


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def catalog_from_xlsx(path, old_ids):
    import openpyxl
    sheet = openpyxl.load_workbook(path)["LIST"]
    header = [str(c.value or "").strip().lower() for c in sheet[1]]
    video_col = header.index("video") if "video" in header else None
    rows, videos = [], []
    for cells in sheet.iter_rows(min_row=2):
        r = tuple(c.value for c in cells)
        if len(r) > 1 and r[1]:
            rows.append(r)
            v = cells[video_col] if video_col is not None and video_col < len(cells) else None
            link = (v.hyperlink.target if v is not None and v.hyperlink else None) or (str(v.value).strip() if v is not None and v.value else "")
            videos.append(link if link.startswith("http") else "")
    by_name = {}
    exercises = []
    for r, video in zip(rows, videos):
        main, name, tool, also, measures = tuple(r[:5])
        col = lambda i: str(r[i]).strip() if len(r) > i and r[i] is not None else ""
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
        e = {"id": ex_id, "name": name, "areas": areas, "equip": tool[:1].upper() + tool[1:], "measures": ms}
        if ex_id in EVERY_SESSION or col(5).lower() in YES:
            e["everySession"] = True
        if col(6).lower() in YES:
            e["pinned"] = True
        if parse_season(col(7)):
            e["months"] = parse_season(col(7))
        if video:
            e["video"] = video
        e.update(OVERRIDES.get(ex_id, {}))
        exercises.append(e)
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
    catalog = {"catalogVersion": CATALOG_VERSION, "retired": RETIRED, "merged": MERGED,
               "areas": [dict(id=i, label=l, target=t, kind=k) for i, l, t, k in AREAS], "exercises": exercises}
    json.dump(catalog, open(os.path.join(out, "catalog.json"), "w"), indent=1)
    json.dump(catalog, open(os.path.join(HERE, "catalog.json"), "w"), indent=1)

    sessions_path = os.path.join(HERE, "sessions.json")
    if os.path.exists(sessions_path):
        ses = json.load(open(sessions_path))
        for s in ses:
            for it in s["items"]:
                it["ex"] = MERGED.get(it["ex"], it["ex"])
                assert it["ex"] in measures, it["ex"]
                d = it.pop("detail", "")
                it["groups"], it["note"] = parse(d, measures[it["ex"]])
            s.setdefault("note", "")
        json.dump({"sessions": ses}, open(os.path.join(out, "history.json"), "w"), indent=1)
    print(len(exercises), "exercises,", sum(not e.get("hidden") for e in exercises), "shown")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
