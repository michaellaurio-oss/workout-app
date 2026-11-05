"""Builds the app's starter data from web/catalog.json and web/sessions.json.
Usage: python to_seed.py ../app/src/main/assets"""
import json, re, sys
def parse(detail):
    groups, notes = [], []
    for part in [p.strip() for p in detail.split(',') if p.strip()]:
        m = re.match(r'^(\d+)x(\d+)(s)?(?:\s*@\s*(\d+(?:\.\d+)?)#)?\s*(.*)$', part)
        if m:
            s, r, sec, w, rest = m.groups()
            if sec:
                groups.append({'sets': int(s)}); notes.append(f'{r}s each')
            else:
                g = {'sets': int(s), 'reps': int(r)}
                if w: g['weight'] = float(w)
                groups.append(g)
            if rest: notes.append(rest)
            continue
        m = re.match(r'^(\d+(?:\.\d+)?)#$', part)
        if m: groups.append({'weight': float(m.group(1))}); continue
        notes.append(part)
    return groups, ' '.join(notes)
cat = json.load(open('catalog.json')); ses = json.load(open('sessions.json'))
for s in ses:
    for it in s['items']:
        d = it.pop('detail', '')
        it['groups'], it['note'] = parse(d)
        if d: print(f"{s['date']} {it['ex']:16} {d!r:28} -> {it['groups']} {it['note']!r}")
    s.setdefault('note', '')
out = sys.argv[1]  # the app's assets folder
# catalog.json (exercise list, committed) and history.json (his log, git-ignored, stays on the PC)
json.dump({'areas': cat['areas'], 'exercises': cat['exercises']}, open(out + '/catalog.json', 'w'), indent=1)
json.dump({'sessions': ses}, open(out + '/history.json', 'w'), indent=1)
