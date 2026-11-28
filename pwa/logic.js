/*
 * Workout Log for the web: the rules. A straight port of the Android app's logic
 * (app/src/main/java/com/laurio/workoutlog/logic/Recommend.kt and Format.kt); keep the two in step.
 * Data has the same shape as the Android app's AppData, so backups move between the two.
 */
(function (root) {
  "use strict";

  const MAIN_CREDIT = 1.0;
  const ALSO_CREDIT = 0.5;
  const DEFAULT_CREDITS = 2.0;
  const MEASURES = ["sets", "reps", "weight", "time", "band", "resistance", "speed", "incline"];
  const DEFAULT_MEASURES = ["sets", "reps", "weight"];
  const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  const STATUS = { FRESH: "Fresh", SOON: "Soon", DUE: "Due", OVERDUE: "Overdue" };
  /** Labels when goals are per calendar week (Sun-Sat). */
  const WEEK_STATUS = { FRESH: "Done", SOON: "On track", DUE: "To do", OVERDUE: "Behind" };

  // ---------- dates (always local calendar days, as "yyyy-mm-dd") ----------

  function pad(n) { return (n < 10 ? "0" : "") + n; }
  function todayStr(d) { d = d || new Date(); return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()); }
  function epochDay(s) { const [y, m, d] = s.split("-").map(Number); return Math.round(Date.UTC(y, m - 1, d) / 86400000); }
  function fromEpoch(n) { const d = new Date(n * 86400000); return d.getUTCFullYear() + "-" + pad(d.getUTCMonth() + 1) + "-" + pad(d.getUTCDate()); }
  function addDays(s, n) { return fromEpoch(epochDay(s) + n); }
  function weekday(s) { return WEEKDAYS[new Date(epochDay(s) * 86400000).getUTCDay()]; }
  function monthOf(s) { return Number(s.split("-")[1]); }

  // ---------- data shape (same as the Android app; missing fields get defaults) ----------

  function normArea(a) {
    const credits = Number(a.credits);
    return {
      id: String(a.id), label: a.label || String(a.id), target: Number(a.target) || 4, kind: a.kind || "strength",
      credits: credits > 0 ? credits : DEFAULT_CREDITS, restUntil: a.restUntil || "",
      perWeek: Number(a.perWeek) > 0 ? Number(a.perWeek) : defaultPerWeek(credits > 0 ? credits : DEFAULT_CREDITS, Number(a.target) || 4),
    };
  }
  /** Credits per week matching "N credits every T days" (2 every 4 days -> 4 a week). Web app only. */
  function defaultPerWeek(credits, target) { return Math.max(1, Math.round((credits * 7) / Math.max(1, target))); }
  function normExercise(e) {
    const measures = (e.measures || []).filter((m) => MEASURES.includes(m));
    return {
      id: String(e.id), name: e.name || String(e.id), areas: (e.areas || []).map(String), equip: e.equip || "",
      hidden: !!e.hidden, measures: measures.length ? measures : DEFAULT_MEASURES.slice(),
      everySession: !!e.everySession, pinned: !!e.pinned,
      months: (e.months || []).map(Number).filter((m) => m >= 1 && m <= 12), video: (e.video || "").trim(),
      edited: !!e.edited, // web app: they changed this built-in movement, so list updates leave its details alone
    };
  }
  function normGroup(g) {
    const out = {};
    for (const k of ["sets", "reps", "time"]) if (g[k] != null && g[k] !== "") out[k] = Math.round(Number(g[k]));
    for (const k of ["weight", "resistance", "speed", "incline"]) if (g[k] != null && g[k] !== "") out[k] = Number(g[k]);
    if (g.band) out.band = String(g.band);
    return out;
  }
  function normItem(i) { return { ex: String(i.ex), groups: (i.groups || []).map(normGroup), note: i.note || i.detail || "" }; }
  function normSession(s) {
    return { date: s.date, items: (s.items || []).map(normItem), areas: (s.areas || []).map(String), raw: s.raw || "", tag: s.tag || "", note: s.note || "" };
  }
  /** Reads saved data or a backup (Android or web). Throws if it isn't Workout Log data. */
  function normData(o) {
    if (!o || !Array.isArray(o.areas) || !o.areas.length) throw new Error("No body areas in this file");
    // The web app's gym list has its own version number, separate from the Android app's home-gym list
    // (catalogVersion). 0 = a log from the Android app: the gym list is never merged into it. Web logs saved
    // before the split have no webCatalogVersion and are recognised by perWeek on their areas.
    const webVersion = o.webCatalogVersion != null ? Number(o.webCatalogVersion) || 0
      : o.areas.some((a) => a && a.perWeek != null) ? Number(o.catalogVersion) || 1 : 0;
    return {
      version: 1,
      catalogVersion: Number(o.catalogVersion) || 1,
      webCatalogVersion: webVersion,
      restDays: Array.from(new Set(o.restDays || [])).sort(),
      areas: o.areas.map(normArea),
      exercises: (o.exercises || []).map(normExercise),
      sessions: (o.sessions || []).map(normSession).sort((a, b) => (a.date < b.date ? 1 : -1)),
    };
  }

  function groupEmpty(g) {
    return g.sets == null && g.reps == null && g.weight == null && g.time == null && !g.band && g.resistance == null && g.speed == null && g.incline == null;
  }
  function inSeason(e, month) { return !e.months.length || e.months.includes(month); }
  function isResting(a, today) { return !!a.restUntil && a.restUntil >= today; }
  /** Cardio-type time is typed in minutes; holds and hangs in seconds. */
  function timeInMinutes(e) {
    return e.areas[0] === "conditioning" || e.measures.some((m) => m === "speed" || m === "incline" || m === "resistance");
  }

  // ---------- Stats: how due each area is, and what was done last ----------

  /**
   * [opts.weekly]: goals are "X credits per week" (Area.perWeek), counted Sunday through Saturday, instead of
   * the Android app's "credits every N days". The web app uses weekly; the N-day rules stay a port of Recommend.kt.
   */
  function Stats(data, today, opts) {
    this.data = data;
    this.weekly = !!(opts && opts.weekly);
    this.today = today || todayStr();
    this.exById = {};
    for (const e of data.exercises) this.exById[e.id] = e;
    this.lastEx = {};
    this.countEx = {};
    this.restEpochs = data.restDays.map(epochDay).filter((n) => !isNaN(n)).sort((a, b) => a - b);
    this.needed = {};
    // An area-only log ("upper") counts as a full set: the period's credits, or the whole week's goal.
    for (const a of data.areas) this.needed[a.id] = this.weekly ? a.perWeek : a.credits;
    this.byArea = {};

    const sessions = data.sessions.slice().sort((a, b) => (a.date < b.date ? -1 : 1));
    for (const s of sessions) {
      for (const item of s.items) {
        this.countEx[item.ex] = (this.countEx[item.ex] || 0) + 1;
        const prev = this.lastEx[item.ex];
        if (!prev || prev.date < s.date) this.lastEx[item.ex] = { date: s.date, groups: item.groups, note: item.note };
      }
      const day = epochDay(s.date);
      // Workouts logged on a rest day are kept in History but give no credits. Weekly goals have no rest
      // days (the web app has no Rest day button; ones from an Android backup are kept but ignored).
      if (!this.weekly && this.isRestDay(day)) continue;
      const credits = this.creditsFor(s);
      for (const a in credits) if (credits[a] > 0) (this.byArea[a] = this.byArea[a] || []).push([day, credits[a]]);
    }

    const todayE = epochDay(this.today);
    if (this.weekly) { this.weekAreas(todayE); return; }
    const todayS = this.scheduleDay(todayE);
    const isRest = this.isRestDay(todayE);
    this.restToday = isRest;
    this.areas = data.areas.map((a) => {
      const target = Math.max(1, a.target);
      const w = this.walk(a);
      const reset = w.resets.length ? w.resets[w.resets.length - 1] : null;
      let open = w.open;
      // On a rest day, credits whose last chance was the day before can't be completed any more.
      const cutoff = todayS - target + (isRest ? 1 : 0);
      while (open.length && this.scheduleDay(open[0][0]) <= cutoff) open.shift();
      const days = reset == null ? null : todayS - this.scheduleDay(reset);
      // Fixed rhythm (as Recommend.kt): due dates count from the period's anchor, not the day he finished.
      const scheduled = w.anchors.length ? todayS - w.anchors[w.anchors.length - 1] : null;
      const partialCredits = open.reduce((t, x) => t + x[1], 0);
      const partialDays = open.length ? todayS - this.scheduleDay(open[0][0]) : null;
      const ratio = scheduled == null ? 9 : scheduled / target;
      const status = days == null || ratio >= 2 ? "OVERDUE" : ratio >= 1 ? "DUE" : ratio >= 0.75 ? "SOON" : "FRESH";
      return {
        area: a, days, partialDays, partialCredits, ratio, status, restToday: isRest,
        creditsShort: Math.max(0, a.credits - partialCredits),
        daysLeft: partialDays == null ? null : target - partialDays,
        dueIn: scheduled == null ? null : target - scheduled, scheduled,
      };
    });
  }

  /** Per-week goals: credits since Sunday against Area.perWeek; days = days since the area last got a credit. */
  Stats.prototype.weekAreas = function (todayE) {
    const dow = new Date(todayE * 86400000).getUTCDay(); // 0 = Sunday
    const weekStart = todayE - dow;
    const daysLeft = 7 - dow; // today included; Saturday = 1
    this.restToday = this.isRestDay(todayE);
    this.weekEnd = fromEpoch(weekStart + 6);
    this.areas = this.data.areas.map((a) => {
      const list = (this.byArea[a.id] || []).filter((x) => x[0] <= todayE);
      const have = list.filter((x) => x[0] >= weekStart).reduce((t, x) => t + x[1], 0);
      const last = list.length ? list[list.length - 1][0] : null;
      const need = a.perWeek;
      const short = Math.max(0, need - have);
      // How hard the rest of the week is compared with an even pace: 1 = keeping pace from zero, 2 = twice that.
      const ratio = short <= 1e-9 ? 0 : (short / daysLeft) / (need / 7);
      const status = short <= 1e-9 ? "FRESH" : ratio >= 2 ? "OVERDUE" : ratio >= 1 ? "DUE" : "SOON";
      return { area: a, weekly: true, days: last == null ? null : todayE - last, have, need, short, daysLeft, ratio, status,
        partialCredits: have, creditsShort: short, partialDays: null, dueIn: null, restToday: this.restToday };
    });
  };

  Stats.prototype.isRestDay = function (e) { return bsearch(this.restEpochs, e) >= 0; };
  /** Day number on the schedule: the calendar day minus rest days up to and including it. */
  Stats.prototype.scheduleDay = function (e) {
    const i = bsearch(this.restEpochs, e);
    const upTo = i >= 0 ? i + 1 : -i - 1;
    return e - upTo;
  };
  /** Every day the area's credits reached its credits needed, and the credits still open after the last day. */
  Stats.prototype.walk = function (a) {
    const target = Math.max(1, a.target);
    const need = a.credits - 1e-9;
    const resets = [];
    const anchors = [];
    let anchor = null;
    let open = [];
    for (const [day, credit] of this.byArea[a.id] || []) {
      const dayS = this.scheduleDay(day);
      while (open.length && this.scheduleDay(open[0][0]) <= dayS - target) open.shift();
      open.push([day, credit]);
      if (open.reduce((t, x) => t + x[1], 0) >= need) {
        // Fixed rhythm: early keeps the old due date as the start of the next period; on time or late starts it today.
        const due = anchor == null ? null : anchor + target;
        anchor = due != null && dayS < due ? due : dayS;
        resets.push(day); anchors.push(anchor); open = [];
      }
    }
    return { resets, anchors, open };
  };
  /** Credits per area for one day. The same movement logged twice that day counts once. */
  Stats.prototype.creditsFor = function (s) {
    const credits = {};
    for (const id of Array.from(new Set(s.items.map((i) => i.ex)))) {
      const e = this.exById[id];
      if (!e) continue;
      Array.from(new Set(e.areas)).forEach((a, i) => { credits[a] = (credits[a] || 0) + (i === 0 ? MAIN_CREDIT : ALSO_CREDIT); });
    }
    for (const a of s.areas) credits[a] = Math.max(credits[a] || 0, this.needed[a] != null ? this.needed[a] : DEFAULT_CREDITS);
    return credits;
  };
  Stats.prototype.daysAgo = function (date) { return epochDay(this.today) - epochDay(date); };
  Stats.prototype.active = function () { return this.areas.filter((s) => !isResting(s.area, this.today)); };
  Stats.prototype.resting = function () {
    return this.areas.filter((s) => isResting(s.area, this.today)).sort((a, b) => (a.area.restUntil < b.area.restUntil ? -1 : 1));
  };
  /** Up to 3 strength areas due (not worked yesterday/today), topped up to 2 with ones coming due, plus cardio/mobility when due. */
  Stats.prototype.focus = function () {
    const rested = (s) => (s.days == null ? 99 : s.days) >= 2;
    const longest = (a, b) => (b.days == null ? 1e9 : b.days) - (a.days == null ? 1e9 : a.days);
    const strength = this.active().filter((s) => s.area.kind === "strength")
      .sort((a, b) => b.ratio - a.ratio || (this.weekly ? longest(a, b) : 0));
    const picked = strength.filter((s) => s.ratio >= 1 && rested(s)).slice(0, 3);
    for (const s of strength) if (picked.length < 2 && !picked.includes(s) && s.ratio >= 0.75 && rested(s)) picked.push(s);
    const extras = this.active().filter((s) => s.area.kind !== "strength" && s.ratio >= 1)
      .sort((a, b) => b.ratio - a.ratio || (this.weekly ? longest(a, b) : 0));
    // Every N days (as Recommend.kt): the cards are shown soonest deadline first. Weekly goals all end Saturday,
    // so there the order stays most behind first.
    if (!this.weekly) return this.byDeadline(picked).concat(this.byDeadline(extras));
    return picked.concat(extras);
  };
  /** Soonest deadline first (the day open credits run out, else days until due; never done first), then most overdue. */
  Stats.prototype.byDeadline = function (list) {
    return list.slice().sort((a, b) => deadlineDays(a) - deadlineDays(b) || b.ratio - a.ratio);
  };
  /** Days to the deadline a card shows: open credits' last day, else days until due; never done = most urgent. */
  function deadlineDays(s) {
    if (s.partialCredits > 0 && s.daysLeft != null) return s.daysLeft;
    return s.dueIn == null ? -Infinity : s.dueIn;
  }
  /** When nothing is due: every area, longest since worked first. */
  Stats.prototype.allByRest = function () {
    return this.active().sort((a, b) => {
      const da = a.days == null ? Infinity : a.days, db = b.days == null ? Infinity : b.days;
      return db - da || deadlineDays(a) - deadlineDays(b) || b.ratio - a.ratio; // same day: due sooner first
    });
  };
  Stats.prototype.everySession = function () {
    const m = monthOf(this.today);
    return this.data.exercises.filter((e) => !e.hidden && e.everySession && inSeason(e, m)).map((e) => ({ exercise: e, last: this.lastEx[e.id] || null }));
  };
  /** Movements whose main area is [areaId]: pinned first, then never-logged, then longest ago. */
  Stats.prototype.movements = function (areaId) {
    const m = monthOf(this.today);
    return this.data.exercises
      .filter((e) => !e.hidden && !e.everySession && e.areas[0] === areaId && inSeason(e, m))
      .map((e) => ({ exercise: e, last: this.lastEx[e.id] || null }))
      .sort((a, b) =>
        (b.exercise.pinned - a.exercise.pinned) ||
        ((a.last != null) - (b.last != null)) ||
        cmp(a.last ? a.last.date : "", b.last ? b.last.date : "") ||
        cmp(a.exercise.name.toLowerCase(), b.exercise.name.toLowerCase()));
  };

  function cmp(a, b) { return a < b ? -1 : a > b ? 1 : 0; }
  function bsearch(arr, x) {
    let lo = 0, hi = arr.length - 1;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (arr[mid] === x) return mid;
      if (arr[mid] < x) lo = mid + 1; else hi = mid - 1;
    }
    return -(lo + 1);
  }

  // ---------- formatting ----------

  function num(d) { return Number.isInteger(d) ? String(d) : String(Math.round(d * 100) / 100); }
  function prettyDate(s, today) {
    const [y, m, d] = s.split("-").map(Number);
    const base = weekday(s) + ", " + MONTHS[m - 1] + " " + d;
    return y === Number((today || todayStr()).slice(0, 4)) ? base : base + ", " + y;
  }
  function ago(days) { return days <= 0 ? "today" : days === 1 ? "yesterday" : days + " days ago"; }
  function formatTime(sec) {
    if (sec < 60) return sec + "s";
    if (sec % 60 === 0) return sec / 60 + " min";
    return Math.floor(sec / 60) + ":" + pad(sec % 60);
  }
  function parseTime(text, minutes) {
    const t = String(text).trim().replace(",", ".");
    if (!t) return null;
    if (t.includes(":")) {
      const [m, s] = t.split(":");
      const mm = parseInt(m, 10) || 0, ss = parseInt(s, 10);
      return isNaN(ss) ? null : mm * 60 + ss;
    }
    const n = Number(t);
    if (isNaN(n)) return null;
    return minutes ? Math.round(n * 60) : Math.round(n);
  }
  function timeForField(sec, minutes) {
    if (!minutes) return String(sec);
    return sec % 60 === 0 ? String(sec / 60) : Math.floor(sec / 60) + ":" + pad(sec % 60);
  }
  function formatMonths(months) {
    const set = new Set(months.filter((m) => m >= 1 && m <= 12));
    if (!set.size || set.size === 12) return "";
    const starts = [];
    for (let m = 1; m <= 12; m++) if (set.has(m) && !set.has(((m + 10) % 12) + 1)) starts.push(m);
    return starts.map((start) => {
      let end = start;
      while (set.has((end % 12) + 1) && (end % 12) + 1 !== start) end = (end % 12) + 1;
      return end === start ? MONTHS[start - 1] : MONTHS[start - 1] + "–" + MONTHS[end - 1];
    }).join(", ");
  }
  function formatGroup(g) {
    const sr = g.sets != null && g.reps != null ? g.sets + "×" + g.reps : g.sets != null ? g.sets + " sets" : g.reps != null ? g.reps + " reps" : "";
    const head = [sr, g.weight != null ? "@ " + num(g.weight) + " lb" : ""].filter(Boolean).join(" ");
    return [
      head || null,
      g.speed != null ? num(g.speed) + " mph" : null,
      g.incline != null ? num(g.incline) + "% incline" : null,
      g.time != null ? formatTime(g.time) : null,
      g.resistance != null ? "level " + num(g.resistance) : null,
      g.band ? "band: " + g.band : null,
    ].filter(Boolean).join(" · ");
  }
  function formatDetail(groups, note) {
    return [groups.filter((g) => !groupEmpty(g)).map(formatGroup).join(", "), (note || "").trim()].filter(Boolean).join(" · ");
  }
  function deadline(left, today) {
    const offset = left - 1;
    if (offset === 0) return "today";
    if (offset === 1) return "tomorrow";
    return "by " + weekday(addDays(today, offset));
  }
  function creditWord(c) { return num(c) + " credit" + (c === 1 ? "" : "s"); }
  function weekBy(left) { return left <= 1 ? "today" : left === 2 ? "tomorrow" : "by Sat"; }
  function goalText(s, today) {
    if (s.weekly) {
      if (s.short <= 1e-9) return "Done for the week (" + num(s.have) + " of " + num(s.need) + " credits)";
      return "Need " + creditWord(s.short).replace(" credit", s.have > 0 ? " more credit" : " credit") + " " + weekBy(s.daysLeft) +
        " (" + num(s.have) + " of " + num(s.need) + " this week)";
    }
    if (s.daysLeft != null && s.partialCredits > 0) {
      return "Need " + creditWord(s.creditsShort).replace(" credit", " more credit") + " " + deadline(s.daysLeft, today) +
        " (" + num(s.partialCredits) + " of " + num(s.area.credits) + " so far)";
    }
    const due = s.dueIn;
    const when = due == null ? "never done" : due > 1 ? "due in " + due + " days" : due === 1 ? "due tomorrow" : due === 0 ? "due today" :
      "overdue " + -due + " day" + (due === -1 ? "" : "s");
    return "Need " + creditWord(s.area.credits) + " · " + when;
  }
  function goalShort(s, today) {
    if (s.weekly) return s.short <= 1e-9 ? "done" : num(s.short) + " short · " + (s.daysLeft <= 1 ? "today" : s.daysLeft + " d left");
    if (s.daysLeft != null && s.partialCredits > 0) return num(s.creditsShort) + " short · " + deadline(s.daysLeft, today);
    const due = s.dueIn;
    return num(s.area.credits) + " short · " + (due == null ? "never" : due === 1 ? "tomorrow" : due > 0 ? "in " + due + " d" : due === 0 ? "today" : -due + " d late");
  }
  function whyText(s) {
    if (s.weekly) return [s.days == null ? "not worked yet" : "last worked " + ago(s.days), "goal " + creditWord(s.need) + " a week, Sun–Sat"].join(" · ");
    return [s.days == null ? "no full session yet" : "full session " + ago(s.days), "target every " + s.area.target + " days"].join(" · ");
  }
  function measureLabel(m, minutes) {
    return { sets: "Sets", reps: "Reps", weight: "Weight (lb)", time: minutes ? "Min" : "Sec", band: "Band", resistance: "Level", speed: "Speed (mph)", incline: "Incline (%)" }[m] || m;
  }
  function measureName(m) {
    return { sets: "Sets", reps: "Reps", weight: "Weight", time: "Time", band: "Band", resistance: "Resistance", speed: "Speed", incline: "Incline" }[m] || m;
  }

  const api = {
    MAIN_CREDIT, ALSO_CREDIT, DEFAULT_CREDITS, MEASURES, DEFAULT_MEASURES, MONTHS, STATUS, WEEK_STATUS, defaultPerWeek,
    todayStr, epochDay, addDays, weekday, monthOf, normData, normExercise, groupEmpty, inSeason, isResting, timeInMinutes,
    Stats, prettyDate, ago, formatTime, parseTime, timeForField, formatMonths, formatGroup, formatDetail, goalText, goalShort, whyText,
    measureLabel, measureName, num,
  };
  root.WL = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(typeof window !== "undefined" ? window : globalThis);
