/*
 * Workout Log for the web (iPhone). Screens: Next, Log, History, Exercises, Areas.
 * Rules live in logic.js (a port of the Android app). Data stays in this browser (localStorage), in the same
 * format as the Android app's backups.
 */
(function () {
  "use strict";
  const KEY = "workoutlog.data";
  const $ = (sel, el) => (el || document).querySelector(sel);

  // ---------- state ----------
  let data = null;
  let catalog = null;
  let today = WL.todayStr();
  let tab = "next";
  let picked = [];            // [{ex, groups:[{...}], note}]
  let logDate = today;
  let logNote = "";
  let logSearch = "";
  let exSearch = "";
  const expanded = new Set(); // Next: area cards showing every movement
  let openAreaId = null;      // Next: an area opened from the All areas list
  let scrollTo = null;

  function save() {
    try { localStorage.setItem(KEY, JSON.stringify(data)); }
    catch (e) { toast("Couldn't save on this phone. Save a backup now."); }
  }
  function loadSaved() {
    try { const t = localStorage.getItem(KEY); return t ? WL.normData(JSON.parse(t)) : null; } catch (e) { return null; }
  }
  function starter() { return WL.normData(JSON.parse(JSON.stringify(catalog))); }

  /** A newer built-in list: names, areas and measures from the list; their own settings and additions stay. */
  function upgrade(d) {
    if (!catalog || (catalog.catalogVersion || 1) <= d.catalogVersion) return d;
    const byId = {};
    d.exercises.forEach((e) => (byId[e.id] = e));
    for (const raw of catalog.exercises) {
      const c = WL.normExercise(raw), e = byId[c.id];
      if (!e) { d.exercises.push(c); continue; }
      Object.assign(e, { name: c.name, areas: c.areas, measures: c.measures, equip: c.equip,
        everySession: e.everySession || c.everySession, pinned: e.pinned || c.pinned,
        months: c.months.length ? c.months : e.months, video: c.video || e.video });
    }
    d.catalogVersion = catalog.catalogVersion;
    return d;
  }

  // ---------- helpers ----------
  function h(tag, attrs, ...kids) {
    const el = document.createElement(tag);
    for (const k in attrs || {}) {
      const v = attrs[k];
      if (v == null || v === false) continue;
      if (k === "class") el.className = v;
      else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
      else if (k === "html") el.innerHTML = v;
      else el.setAttribute(k, v === true ? "" : v);
    }
    for (const kid of kids.flat()) if (kid != null && kid !== false) el.append(kid instanceof Node ? kid : String(kid));
    return el;
  }
  let toastTimer = null;
  function toast(msg, action, fn) {
    const t = $("#toast");
    t.replaceChildren(...[h("span", null, msg), action ? h("button", { onclick: () => { t.hidden = true; fn(); } }, action) : null].filter(Boolean));
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => (t.hidden = true), action ? 6000 : 3000);
  }
  function openSheet(build) {
    const card = $("#sheet-card");
    card.replaceChildren(...[].concat(build(closeSheet)).flat().filter(Boolean));
    $("#sheet").hidden = false;
    card.scrollTop = 0;
  }
  function closeSheet() { $("#sheet").hidden = true; $("#sheet-card").replaceChildren(); }
  function areaLabel(id) { const a = data.areas.find((x) => x.id === id); return a ? a.label : id; }
  function exName(id) { const e = data.exercises.find((x) => x.id === id); return e ? e.name : id; }
  function videoBtn(e) {
    if (!e.video) return null;
    return h("a", { class: "video", href: videoHref(e), target: "_blank", rel: "noopener", "aria-label": "Video for " + e.name, onclick: (ev) => ev.stopPropagation() }, "▶");
  }

  // ---------- dead video links ----------
  // A web page can't ask YouTube whether a video still exists, but its thumbnail tells: a removed video's
  // thumbnail is a 120x90 gray placeholder instead of the real 320x180 image. Checked about once a week;
  // a link found dead opens a YouTube search for the movement instead.
  const VKEY = "workoutlog.videoCheck";
  const WEEK = 7 * 86400000;
  let videoCheck = {};
  try { videoCheck = JSON.parse(localStorage.getItem(VKEY) || "{}") || {}; } catch (e) { videoCheck = {}; }
  function youtubeId(url) {
    const m = /(?:youtube\.com\/(?:watch\?(?:.*&)?v=|shorts\/|embed\/)|youtu\.be\/)([\w-]{11})/.exec(url || "");
    return m ? m[1] : null;
  }
  function searchUrl(name) { return "https://www.youtube.com/results?search_query=" + encodeURIComponent(name + " proper form"); }
  function videoHref(e) {
    const id = youtubeId(e.video);
    return id && videoCheck[id] && videoCheck[id].dead ? searchUrl(e.name) : e.video;
  }
  function checkVideos() {
    if (!navigator.onLine) return;
    const now = Date.now();
    const ids = Array.from(new Set(data.exercises.filter((e) => !e.hidden).map((e) => youtubeId(e.video)).filter(Boolean)))
      .filter((id) => !videoCheck[id] || now - videoCheck[id].at > WEEK);
    let changed = false, i = 0;
    const next = () => {
      if (i >= ids.length) { if (changed) render(); return; }
      const id = ids[i++];
      const img = new Image();
      img.onload = () => {
        const dead = img.naturalWidth === 120;
        if (!!(videoCheck[id] && videoCheck[id].dead) !== dead) changed = true;
        videoCheck[id] = { dead, at: Date.now() };
        try { localStorage.setItem(VKEY, JSON.stringify(videoCheck)); } catch (e) {}
        next();
      };
      img.onerror = () => next(); // offline or blocked: leave it for next time
      img.src = "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
    };
    next();
  }
  // Goals are "X credits per week", Sunday to Saturday (the Android app uses "every N days").
  function stats() { return new WL.Stats(data, today, { weekly: true }); }
  function isPicked(id) { return picked.some((p) => p.ex === id); }
  function togglePick(id, st) {
    const i = picked.findIndex((p) => p.ex === id);
    if (i >= 0) picked.splice(i, 1);
    else {
      const last = (st || stats()).lastEx[id];
      const groups = last && last.groups.length ? last.groups.map((g) => Object.assign({}, g)) : [{}];
      picked.push({ ex: id, groups, note: "" });
    }
    renderTabs();
  }

  // ---------- Next ----------
  function renderNext() {
    const st = stats();
    const focus = st.focus();
    const allFresh = focus.length === 0;
    const cards = allFresh ? st.allByRest() : focus;
    const resting = st.resting();
    const routine = st.everySession();
    const out = [];

    out.push(h("div", null,
      h("h1", null, "Next workout"),
      h("div", { class: "sub" }, WL.prettyDate(today, today)),
      h("div", { class: "lead" }, allFresh ? "You're on track everywhere. Pick anything, or rest." : "Work on: " + focus.map((s) => s.area.label).join(", ")),
      h("div", { class: "sub small" },
        (allFresh ? "All areas are below, longest since you worked them first; the one you did most recently is last. " : "") +
        "Movements are listed oldest first, so the ones you haven't done in a while are on top. Tick what you do, then tap Log.")));
    if (routine.length) {
      out.push(h("section", { class: "card" },
        h("div", { class: "card-head" }, h("h2", { class: "card-title" }, "Every session")),
        h("div", { class: "sub small" }, "Your routine. Turn on \"Do every session\" for a movement on the Exercises tab to add it here."),
        routine.map((m) => movementRow(m, st))));
    }
    for (const s of cards) out.push(focusCard(s, st));
    if (resting.length) {
      out.push(h("div", { class: "section" }, "Moved down"));
      for (const s of resting) out.push(restingCard(s));
    }
    if (!allFresh) {
      out.push(h("div", { class: "section" }, "All areas · credits short and when"));
      const list = h("div", { class: "card" });
      const cardIds = new Set(cards.map((s) => s.area.id));
      for (const s of st.areas.slice().sort((a, b) => b.ratio - a.ratio)) {
        const resting = WL.isResting(s.area, today);
        list.append(h("div", { class: "arearow", onclick: () => {
          if (cardIds.has(s.area.id)) { expanded.add(s.area.id); scrollTo = "card-" + s.area.id; }
          else { openAreaId = openAreaId === s.area.id ? null : s.area.id; }
          render();
        } },
          h("span", { class: "name" }, s.area.label),
          targetBar(s),
          h("span", { class: "short " + (resting ? "muted" : "s-" + s.status) }, resting ? "moved down" : WL.goalShort(s, today))));
        if (openAreaId === s.area.id && !cardIds.has(s.area.id)) list.append(focusCard(s, st, true));
      }
      out.push(list);
    }
    return out;
  }

  function targetBar(s) {
    const pct = s.weekly ? Math.min(1, s.have / s.need) * 100 : Math.min(1, s.ratio / 2) * 100;
    const color = { FRESH: "var(--fresh)", SOON: "var(--soon)", DUE: "var(--due)", OVERDUE: "var(--overdue)" }[s.status];
    return h("div", { class: "bar" }, h("div", { style: `width:${pct}%;background:${color}` }));
  }

  function focusCard(s, st, inline) {
    const list = st.movements(s.area.id);
    const open = expanded.has(s.area.id);
    const card = h("section", { class: "card", id: "card-" + s.area.id, style: inline ? "padding:12px 0 4px;background:none" : null },
      h("div", { class: "card-head" },
        h("h2", { class: "card-title" }, s.area.label),
        h("span", { class: "pill s-" + s.status }, WL.WEEK_STATUS[s.status])),
      h("div", { class: "goal s-" + s.status }, WL.goalText(s, today)),
      h("div", { class: "sub small" }, WL.whyText(s)),
      list.length ? null : h("div", { class: "muted" }, "No movements here yet. Add some on the Exercises tab."),
      (open ? list : list.slice(0, 4)).map((m) => movementRow(m, st)));
    const btns = h("div", { class: "btns" });
    if (list.length > 4) btns.append(h("button", { class: "btn text", onclick: () => { open ? expanded.delete(s.area.id) : expanded.add(s.area.id); render(); } }, open ? "Show fewer" : "Show all " + list.length));
    btns.append(h("span", { class: "spacer" }), moveDown(s.area));
    card.append(btns);
    return card;
  }

  function moveDown(area) {
    const wrap = h("div", { class: "btns" });
    const btn = h("button", { class: "btn text", onclick: () => {
      wrap.replaceChildren(h("span", { class: "sub small" }, "Rest " + area.label + ":"),
        ...[[1, "Today"], [2, "2 days"], [3, "3 days"]].map(([n, t]) => h("button", { class: "chip", onclick: () => {
          area.restUntil = WL.addDays(today, n - 1); save(); render(); toast(area.label + " moved down.");
        } }, t)),
        h("button", { class: "btn text", onclick: () => render() }, "Cancel"));
    } }, "⌄ Move down");
    wrap.append(btn);
    return wrap;
  }

  function restingCard(s) {
    const back = s.area.restUntil === today ? "Back tomorrow" : "Resting through " + WL.weekday(s.area.restUntil);
    return h("div", { class: "card dim" }, h("div", { class: "btns" },
      h("div", null, h("div", { style: "font-weight:800;text-transform:uppercase" }, s.area.label), h("div", { class: "sub small" }, back)),
      h("span", { class: "spacer" }),
      h("button", { class: "btn text", onclick: () => { s.area.restUntil = ""; save(); render(); } }, "Bring back")));
  }

  function movementRow(m, st) {
    const e = m.exercise;
    const detail = m.last ? WL.formatDetail(m.last.groups, m.last.note) : "";
    return h("div", { class: "row tap", onclick: () => { togglePick(e.id, st); render(); } },
      h("span", { class: "check" + (isPicked(e.id) ? " on" : "") }),
      h("div", { class: "grow" }, h("div", null, e.name), e.equip ? h("div", { class: "sub small" }, e.equip) : null),
      videoBtn(e),
      h("div", { class: "right" },
        m.last ? [h("div", { class: "sub small" }, WL.ago(st.daysAgo(m.last.date))), detail ? h("div", { class: "sub", style: "font-size:12px" }, detail) : null]
          : h("div", { class: "never" }, "never logged")));
  }

  // ---------- Log ----------
  function renderLog() {
    const st = stats();
    const out = [h("div", null, h("h1", null, "Log a workout"), h("div", { class: "sub" }, "What you did, with sets, weights and times."))];
    const date = h("input", { type: "date", value: logDate, max: today, onchange: (ev) => { logDate = ev.target.value || today; render(); } });
    out.push(h("label", { class: "field" }, "Date", date));
    if (data.restDays.includes(logDate)) out.push(h("div", { class: "sub small" }, "This is a rest day: it's saved in History but gives no credits."));

    for (const p of picked) out.push(pickedCard(p, st));

    const search = h("input", { type: "search", placeholder: "Add a movement (name or equipment)", value: logSearch, autocomplete: "off",
      oninput: (ev) => { logSearch = ev.target.value; drawResults(); } });
    const results = h("div", { class: "results" });
    const month = WL.monthOf(today);
    function drawResults() {
      const q = logSearch.trim().toLowerCase();
      results.replaceChildren();
      if (!q) return;
      const hits = data.exercises.filter((e) => !e.hidden && WL.inSeason(e, month) && !isPicked(e.id) &&
        (e.name.toLowerCase().includes(q) || e.equip.toLowerCase().includes(q))).slice(0, 12);
      if (!hits.length) results.append(h("div", { class: "sub small", style: "padding:8px 0" }, "No match. Add new movements on the Exercises tab."));
      for (const e of hits) {
        results.append(h("div", { class: "row tap", onclick: () => { togglePick(e.id, st); logSearch = ""; render(); } },
          h("div", { class: "grow" }, h("div", null, e.name), h("div", { class: "sub small" }, [areaLabel(e.areas[0]), e.equip].filter(Boolean).join(" · "))),
          h("span", { class: "btn text" }, "Add")));
      }
    }
    drawResults();
    out.push(h("div", { class: "card" }, search, results));

    out.push(h("label", { class: "field" }, "Note for the day (optional)",
      h("textarea", { rows: 2, oninput: (ev) => (logNote = ev.target.value) }, logNote)));
    out.push(h("div", { class: "btns" },
      h("button", { class: "btn primary", disabled: picked.length ? null : true, onclick: saveWorkout }, picked.length ? "Save workout (" + picked.length + ")" : "Save workout"),
      picked.length ? h("button", { class: "btn text", onclick: () => { picked = []; render(); } }, "Clear") : null));
    if (!picked.length) out.push(h("div", { class: "sub small" }, "Tick movements on the Next tab, or search above."));
    return out;
  }

  function pickedCard(p, st) {
    const e = data.exercises.find((x) => x.id === p.ex) || WL.normExercise({ id: p.ex, name: p.ex });
    const minutes = WL.timeInMinutes(e);
    const last = st.lastEx[e.id];
    const card = h("section", { class: "card" },
      h("div", { class: "card-head" },
        h("div", { class: "grow", style: "flex:1" }, h("div", { style: "font-weight:700;font-size:17px" }, e.name),
          h("div", { class: "sub small" }, last ? "Last: " + (WL.formatDetail(last.groups, last.note) || "logged") + " · " + WL.ago(st.daysAgo(last.date)) : "First time")),
        videoBtn(e),
        h("button", { class: "btn text", onclick: () => { picked = picked.filter((x) => x !== p); render(); } }, "Remove")));
    p.groups.forEach((g, gi) => {
      const fields = h("div", { class: "fields" });
      for (const m of e.measures) {
        const shown = g[m] == null ? "" : m === "time" ? WL.timeForField(g[m], minutes) : String(g[m]);
        const input = h("input", {
          type: m === "band" ? "text" : m === "time" ? "text" : "number",
          inputmode: m === "band" ? "text" : m === "time" ? "decimal" : m === "sets" || m === "reps" ? "numeric" : "decimal",
          step: "any", value: shown, placeholder: m === "time" ? (minutes ? "min" : "sec") : "",
          oninput: (ev) => {
            const v = ev.target.value.trim();
            if (!v) { delete g[m]; return; }
            if (m === "band") g[m] = v;
            else if (m === "time") { const t = WL.parseTime(v, minutes); if (t == null) delete g[m]; else g[m] = t; }
            else { const n = Number(v.replace(",", ".")); if (isNaN(n)) delete g[m]; else g[m] = m === "sets" || m === "reps" ? Math.round(n) : n; }
          },
        });
        fields.append(h("label", { class: "field" }, WL.measureLabel(m, minutes), input));
      }
      if (p.groups.length > 1) fields.append(h("button", { class: "btn text", "aria-label": "Remove line", onclick: () => { p.groups.splice(gi, 1); render(); } }, "✕"));
      card.append(fields);
    });
    card.append(h("div", { class: "btns" },
      h("button", { class: "btn text", onclick: () => { p.groups.push(Object.assign({}, p.groups[p.groups.length - 1] || {})); render(); } }, "+ Add another line")));
    card.append(h("input", { type: "text", placeholder: "Note (optional)", value: p.note, oninput: (ev) => (p.note = ev.target.value) }));
    return card;
  }

  function saveWorkout() {
    if (!picked.length) return;
    const items = picked.map((p) => ({ ex: p.ex, groups: p.groups.filter((g) => !WL.groupEmpty(g)), note: p.note.trim() }));
    let s = data.sessions.find((x) => x.date === logDate);
    if (!s) { s = { date: logDate, items: [], areas: [], raw: "", tag: "", note: "" }; data.sessions.push(s); }
    s.items = s.items.concat(items);
    if (logNote.trim()) s.note = [s.note, logNote.trim()].filter(Boolean).join(" · ");
    data.sessions.sort((a, b) => (a.date < b.date ? 1 : -1));
    save();
    picked = []; logNote = ""; logDate = today;
    tab = "next";
    render();
    window.scrollTo(0, 0);
    toast("Saved " + items.length + " movement" + (items.length === 1 ? "" : "s") + ".");
  }

  // ---------- History ----------
  function renderHistory() {
    const out = [h("div", null, h("h1", null, "History"), h("div", { class: "sub" }, data.sessions.length ? "Tap a movement to see its progress." : "Nothing logged yet."))];
    let year = null;
    for (const s of data.sessions) {
      const y = s.date.slice(0, 4);
      if (y !== year && y !== today.slice(0, 4)) { out.push(h("div", { class: "section" }, y)); }
      year = y;
      const rest = data.restDays.includes(s.date);
      const card = h("section", { class: "card" },
        h("div", { class: "card-head" },
          h("div", { style: "flex:1;font-weight:700" }, WL.prettyDate(s.date, today)),
          rest ? h("span", { class: "pill", style: "color:var(--muted)" }, "rest day") : null),
        s.items.map((it) => h("div", { class: "row tap", onclick: () => progress(it.ex) },
          h("div", { class: "grow" }, h("div", null, exName(it.ex)), h("div", { class: "sub small" }, WL.formatDetail(it.groups, it.note))))),
        s.areas.length ? h("div", { class: "sub" }, "Worked: " + s.areas.map(areaLabel).join(", ")) : null,
        s.raw ? h("div", { class: "sub small" }, s.raw) : null,
        s.note ? h("div", { class: "sub small" }, s.note) : null);
      const del = h("div", { class: "btns" }, h("span", { class: "spacer" }), h("button", { class: "btn text danger", onclick: () => {
        del.replaceChildren(h("span", { class: "sub small" }, "Delete this day?"), h("span", { class: "spacer" }),
          h("button", { class: "btn text", onclick: () => render() }, "Keep"),
          h("button", { class: "btn danger", onclick: () => {
            const i = data.sessions.indexOf(s);
            data.sessions.splice(i, 1); save(); render();
            toast("Deleted.", "Undo", () => { data.sessions.splice(i, 0, s); save(); render(); });
          } }, "Delete"));
      } }, "Delete"));
      card.append(del);
      out.push(card);
    }
    return out;
  }

  /** Heaviest weight per day (else most reps, else longest time), best marked, plus every day logged. */
  function progress(exId) {
    const days = data.sessions.filter((s) => s.items.some((i) => i.ex === exId)).slice().reverse();
    const all = days.flatMap((s) => s.items.filter((i) => i.ex === exId).flatMap((i) => i.groups));
    const key = all.some((g) => g.weight != null) ? "weight" : all.some((g) => g.reps != null) ? "reps" : all.some((g) => g.time != null) ? "time" : null;
    const points = key ? days.map((s) => ({ date: s.date, v: Math.max(0, ...s.items.filter((i) => i.ex === exId).flatMap((i) => i.groups.map((g) => g[key] || 0))) })) : [];
    const best = Math.max(0, ...points.map((p) => p.v));
    const unit = { weight: " lb", reps: " reps", time: "" }[key] || "";
    const fmt = (v) => (key === "time" ? WL.formatTime(v) : WL.num(v) + unit);
    openSheet((close) => [
      h("div", { class: "card-head" }, h("h2", { style: "flex:1" }, exName(exId)), h("button", { class: "btn text", onclick: close }, "Done")),
      key ? h("div", { class: "sub" }, "Best: " + fmt(best) + " · " + { weight: "heaviest weight", reps: "most reps", time: "longest time" }[key] + " each day") : null,
      key && points.length > 1 ? h("div", { class: "chart" }, points.map((p) => h("div", { class: p.v === best ? "best" : "", title: p.date, style: `height:${best ? Math.max(4, (p.v / best) * 100) : 4}%` }))) : null,
      days.slice().reverse().map((s) => h("div", { class: "row" },
        h("div", { class: "grow" }, h("div", null, WL.prettyDate(s.date, today)),
          h("div", { class: "sub small" }, s.items.filter((i) => i.ex === exId).map((i) => WL.formatDetail(i.groups, i.note)).filter(Boolean).join("; ") || "logged")))),
    ]);
  }

  // ---------- Exercises ----------
  function renderExercises() {
    const out = [h("div", null, h("h1", null, "Exercises"), h("div", { class: "sub" }, "Tap a movement to change it. Main area first, then areas it also works."))];
    const list = h("div", { style: "display:flex;flex-direction:column;gap:12px" });
    function draw() {
      list.replaceChildren();
      const q = exSearch.trim().toLowerCase();
      for (const a of data.areas) {
        const items = data.exercises.filter((e) => !e.hidden && e.areas[0] === a.id && (!q || e.name.toLowerCase().includes(q) || e.equip.toLowerCase().includes(q)))
          .sort((x, y) => (y.pinned - x.pinned) || x.name.localeCompare(y.name));
        if (q && !items.length) continue;
        list.append(h("section", { class: "card" },
          h("div", { class: "card-head" }, h("h2", { class: "card-title" }, a.label), h("span", { class: "sub small" }, items.length)),
          items.map((e) => h("div", { class: "row tap", onclick: () => editExercise(e) },
            h("div", { class: "grow" }, h("div", null, e.name),
              h("div", { class: "sub small" }, [e.equip, e.areas.length > 1 ? "also " + e.areas.slice(1).map(areaLabel).join(", ") : "",
                e.measures.map(WL.measureName).join(" / "), e.everySession ? "every session" : "", e.pinned ? "always first" : "",
                WL.formatMonths(e.months)].filter(Boolean).join(" · "))),
            videoBtn(e)))));
      }
      if (q && !list.children.length) list.append(h("div", { class: "sub" }, "No movement matches \"" + exSearch + "\"."));
    }
    out.push(h("input", { type: "search", placeholder: "Search name or equipment", value: exSearch, oninput: (ev) => { exSearch = ev.target.value; draw(); } }));
    out.push(h("div", { class: "btns" }, h("button", { class: "btn primary", onclick: () => editExercise(null) }, "+ Add a movement")));
    draw();
    out.push(list);
    out.push(backupCard());
    return out;
  }

  function editExercise(e) {
    const isNew = !e;
    const f = e ? JSON.parse(JSON.stringify(e)) : WL.normExercise({ id: "", name: "", areas: [data.areas[0].id] });
    if (isNew) f.name = "";
    openSheet((close) => {
      const name = h("input", { type: "text", value: f.name, placeholder: "e.g. Cable fly" });
      const equip = h("input", { type: "text", value: f.equip, placeholder: "e.g. Cable machine" });
      const video = h("input", { type: "url", value: f.video, placeholder: "https://…" });
      const main = h("select", null, data.areas.map((a) => h("option", { value: a.id, selected: a.id === f.areas[0] ? true : null }, a.label)));
      const chipSet = (opts, isOn, toggle, skip) => {
        const box = h("div", { class: "chips" });
        const draw = () => box.replaceChildren(...opts.filter(([v]) => !(skip && skip(v))).map(([v, label]) => h("button", { class: "chip" + (isOn(v) ? " on" : ""), onclick: () => { toggle(v); draw(); } }, label)));
        draw();
        box.redraw = draw;
        return box;
      };
      const also = chipSet(data.areas.map((a) => [a.id, a.label]), (v) => f.areas.slice(1).includes(v), (v) => {
        const rest = f.areas.slice(1);
        f.areas = [f.areas[0]].concat(rest.includes(v) ? rest.filter((x) => x !== v) : rest.concat(v));
      }, (v) => v === f.areas[0]);
      main.onchange = () => { f.areas = [main.value].concat(f.areas.slice(1).filter((x) => x !== main.value)); also.redraw(); };
      const measures = chipSet(WL.MEASURES.map((m) => [m, WL.measureName(m)]), (v) => f.measures.includes(v), (v) => {
        f.measures = f.measures.includes(v) ? f.measures.filter((x) => x !== v) : WL.MEASURES.filter((m) => m === v || f.measures.includes(m));
      });
      const months = chipSet(WL.MONTHS.map((m, i) => [i + 1, m]), (v) => f.months.includes(v), (v) => {
        f.months = f.months.includes(v) ? f.months.filter((x) => x !== v) : f.months.concat(v).sort((a, b) => a - b);
      });
      const toggle = (key, label, help) => {
        const box = h("span", { class: "check" + (f[key] ? " on" : "") });
        return h("div", { class: "toggle", onclick: () => { f[key] = !f[key]; box.className = "check" + (f[key] ? " on" : ""); } },
          box, h("div", null, h("div", null, label), h("div", { class: "sub small" }, help)));
      };
      const used = !isNew && data.sessions.some((s) => s.items.some((i) => i.ex === e.id));
      const delRow = h("div", { class: "btns" });
      if (!isNew) delRow.append(h("button", { class: "btn text danger", onclick: () => {
        delRow.replaceChildren(h("span", { class: "sub small" }, used ? "Your history keeps its name." : "Delete " + e.name + "?"),
          h("span", { class: "spacer" }),
          h("button", { class: "btn danger", onclick: () => {
            if (used) e.hidden = true; else data.exercises = data.exercises.filter((x) => x !== e);
            picked = picked.filter((p) => p.ex !== e.id);
            save(); close(); render(); toast("Deleted " + e.name + ".");
          } }, "Delete"));
      } }, "Delete movement"));
      return [
        h("div", { class: "card-head" }, h("h2", { style: "flex:1" }, isNew ? "New movement" : "Edit movement"), h("button", { class: "btn text", onclick: close }, "Cancel")),
        h("label", { class: "field" }, "Name", name),
        h("label", { class: "field" }, "Equipment", equip),
        h("label", { class: "field" }, "Main area (1 credit)", main),
        h("div", { class: "field" }, h("span", { class: "sub small" }, "Also works (½ credit each)"), also),
        h("div", { class: "field" }, h("span", { class: "sub small" }, "What you log"), measures),
        toggle("everySession", "Do every session", "Shown at the top of Next, whatever is due (a warmup, say)."),
        toggle("pinned", "Always first", "Top of its area's list."),
        h("div", { class: "field" }, h("span", { class: "sub small" }, "Season (none = all year)"), months),
        h("label", { class: "field" }, "Instruction video link", video),
        h("div", { class: "btns" }, h("button", { class: "btn primary", onclick: () => {
          const n = name.value.trim().replace(/\s+/g, " ");
          if (!n) { name.focus(); return; }
          const clash = data.exercises.find((x) => !x.hidden && x !== e && x.name.toLowerCase() === n.toLowerCase());
          if (clash) { toast("There's already a movement called " + clash.name + "."); return; }
          Object.assign(f, { name: n, equip: equip.value.trim(), video: video.value.trim() });
          if (!f.measures.length) f.measures = WL.DEFAULT_MEASURES.slice();
          if (isNew) {
            let id = n.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "movement";
            while (data.exercises.some((x) => x.id === id)) id += "-2";
            f.id = id;
            data.exercises.push(WL.normExercise(f));
          } else Object.assign(e, WL.normExercise(Object.assign({}, f, { id: e.id })));
          save(); close(); render(); toast(isNew ? "Added " + n + "." : "Saved.");
        } }, "Save")),
        delRow,
      ];
    });
  }

  function backupCard() {
    const file = h("input", { type: "file", accept: "application/json,.json", hidden: true, onchange: async (ev) => {
      const f = ev.target.files[0];
      ev.target.value = "";
      if (!f) return;
      try {
        const d = upgrade(WL.normData(JSON.parse(await f.text())));
        const prev = data;
        data = d; picked = []; save(); render();
        toast("Restored " + d.sessions.length + " workouts.", "Undo", () => { data = prev; save(); render(); });
      } catch (e) { toast("That file isn't a Workout Log backup."); }
    } });
    return h("section", { class: "card" },
      h("div", { class: "card-head" }, h("h2", { class: "card-title" }, "Backup")),
      h("div", { class: "sub small" }, "Your log lives only on this phone, in this app. Save a backup to Files now and then, so a new phone or a cleared browser doesn't lose it. Backups also open in the Android app."),
      h("div", { class: "btns" },
        h("button", { class: "btn primary", onclick: exportBackup }, "Save backup"),
        h("button", { class: "btn", onclick: () => file.click() }, "Restore from file"), file));
  }

  async function exportBackup() {
    const name = "workout-log-backup-" + today + ".json";
    const blob = new Blob([JSON.stringify(data, null, 1)], { type: "application/json" });
    try {
      const f = new File([blob], name, { type: "application/json" });
      if (navigator.canShare && navigator.canShare({ files: [f] })) { await navigator.share({ files: [f], title: name }); return; }
    } catch (e) { if (e && e.name === "AbortError") return; }
    const url = URL.createObjectURL(blob);
    const a = h("a", { href: url, download: name });
    document.body.append(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10000);
    toast("Backup saved to Downloads.");
  }

  // ---------- Areas ----------
  function renderAreas() {
    const st = stats();
    const out = [h("div", null, h("h1", null, "Areas"),
      h("div", { class: "sub" }, "How many credits each area should get each week (Sunday to Saturday). Each movement you log gives 1 credit to its main area and ½ to each area it also works. A new week starts every Sunday."))];
    for (const a of data.areas) {
      const s = st.areas.find((x) => x.area.id === a.id);
      const stepper = (val, fmt, dec, inc) => h("div", { class: "stepper" },
        h("button", { class: "btn", "aria-label": "Less", onclick: () => { dec(); save(); render(); } }, "−"),
        h("span", { class: "val" }, fmt(val)),
        h("button", { class: "btn", "aria-label": "More", onclick: () => { inc(); save(); render(); } }, "+"));
      out.push(h("section", { class: "card" },
        h("div", { class: "card-head" }, h("h2", { class: "card-title" }, a.label), h("span", { class: "pill s-" + s.status }, WL.WEEK_STATUS[s.status])),
        h("div", { class: "sub small" }, WL.goalText(s, today)),
        h("div", { class: "btns", style: "justify-content:space-between" }, h("span", null, "Credits per week"),
          stepper(a.perWeek, WL.num, () => (a.perWeek = Math.max(1, a.perWeek - 1)), () => (a.perWeek = Math.min(28, a.perWeek + 1))))));
    }
    return out;
  }

  // ---------- frame ----------
  function renderTabs() {
    for (const b of document.querySelectorAll("#tabs button")) {
      b.classList.toggle("on", b.dataset.tab === tab);
      if (b.dataset.tab === "log") b.replaceChildren("Log", picked.length ? h("span", { class: "badge" }, picked.length) : "");
    }
  }
  function render() {
    const y = window.scrollY;
    const screens = { next: renderNext, log: renderLog, history: renderHistory, exercises: renderExercises, areas: renderAreas };
    $("#screen").replaceChildren(...[].concat(screens[tab]()).flat().filter(Boolean));
    renderTabs();
    window.scrollTo(0, y);
    if (scrollTo) { const el = document.getElementById(scrollTo); scrollTo = null; if (el) el.scrollIntoView({ behavior: "smooth", block: "start" }); }
  }
  function refreshToday() {
    const t = WL.todayStr();
    if (t !== today) { if (logDate === today) logDate = t; today = t; render(); }
  }

  document.getElementById("tabs").addEventListener("click", (ev) => {
    const b = ev.target.closest("button");
    if (!b) return;
    if (tab !== b.dataset.tab) { tab = b.dataset.tab; render(); window.scrollTo(0, 0); }
  });
  $("#sheet").addEventListener("click", (ev) => { if (ev.target.id === "sheet") closeSheet(); });
  document.addEventListener("visibilitychange", () => { if (!document.hidden) refreshToday(); });
  setInterval(refreshToday, 60000);

  async function start() {
    try { catalog = await (await fetch("catalog.json", { cache: "no-cache" })).json(); } catch (e) { catalog = null; }
    data = loadSaved();
    if (data) {
      const before = data.catalogVersion;
      data = upgrade(data);
      if (data.catalogVersion !== before) save();
    } else if (catalog) { data = starter(); save(); }
    else { $("#screen").replaceChildren(h("p", null, "Couldn't load the exercise list. Check the connection and reopen.")); return; }
    render();
    try { if (navigator.storage && navigator.storage.persist) navigator.storage.persist(); } catch (e) {}
    if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
    setTimeout(checkVideos, 3000);
  }
  start();
})();
