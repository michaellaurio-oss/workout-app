# CLAUDE.md: Workout Log

Michael's personal Android app for his home-gym workouts. He is the only user. It tells him which
body areas are due and, for each, which movements he hasn't done lately, and logs each workout.
Explain build and install steps plainly; he is not a day-to-day Android developer.

## What matters most

- **Next tab answers two questions:** which body areas to work today, and within each, which movements
  he hasn't done recently (never-logged first, then oldest). Keep that front and center.
- Areas (Chest, Back, Shoulders, Arms, Legs, Glutes & Hamstrings, Core, Conditioning, Mobility) each have a
  target "every N days" (`Area.target`) and **credits needed** (`Area.credits`, default 2, 0.5 steps, 0.5–10),
  both set on the **Areas tab** (`ui/AreasScreen.kt`). Catalog upgrades keep his values.
- A movement's `areas` list = main area first, then areas it also works. **Credits** (his rules, 2026-11-07):
  each movement logged on a day gives 1 credit to its main area and 0.5 to each area it also works (same
  movement twice in a day counts once; `MAIN_CREDIT`, `ALSO_CREDIT`, `Stats.creditsFor`). Credits **add up
  across days within the area's every-N-days period**; the day they reach the area's credits needed resets
  its clock, and counting restarts from zero (credits never count toward two resets). An area-only log
  ("upper") is a full reset. `AreaStatus.creditsShort` / `daysLeft` / `dueIn` feed the Next tab's
  "Need 1 more credit by Thu (1 of 2 so far)" / "Need 2 credits · due in 2 days" (`goalText`, `goalShort`
  in `logic/Format.kt`).
- Recommendation (`Stats.focus`): up to 3 strength areas at/over target and not worked in the last day,
  topped up to 2 with ones at >= 75% of target, plus Conditioning/Mobility when due.
  When nothing is due, the Next tab shows every area instead (`Stats.allByRest`), longest since worked
  first, so the one he did most recently is at the bottom.
- Each movement has `measures` (from `Measure.ALL`: sets, reps, weight, time, band, resistance, speed,
  incline); the Log tab shows only those fields. A logged movement has one or more lines (`SetGroup`;
  weight in lb, time stored in seconds) plus a free note. Time is typed in minutes for cardio
  (`Exercise.timeInMinutes`: Conditioning, or measures speed/incline/resistance), seconds otherwise; "1:30" works in both.
  New picks are pre-filled with what he did last time.
- **Every session** card at the top of Next: movements with `Exercise.everySession` (his routine; Asian squats
  is his warmup). Always shown whatever is due, and left out of the area cards. Set per movement on the Exercises
  tab ("Do every session"), or column F in the LIST tab.
  Catalog upgrades keep it on if either the list or his own setting has it on.
- `Exercise.pinned`: always first in its area's list (slam balls, his main cardio). `Exercise.months`: months it's
  offered (paddleboarding Jun–Sep); out of season it's left off Next and the Log search but stays in History and
  the Exercises tab. Both editable in the movement dialog, or columns G and H in the LIST tab.
- `Exercise.video`: instruction-video link; a ▶ button (`VideoButton` in `ui/Components.kt`) opens it on Next,
  in the Log card and on the Exercises tab. Editable in the movement dialog; kept from his data on upgrade if the
  list has none.
- **Move down** on each Next area card (`MoveDownButton`): rest an area for Today / 2 / 3 days (`Area.restUntil`,
  last rest day; e.g. a strain). Resting areas are left out of `Stats.focus`/`allByRest` (so another area is
  suggested), shown dimmed under "Moved down" with "Bring back" (`RestingCard`), and return on their own after.
- Next tab's **All areas** list: tapping an area jumps to its card above (and expands it), or opens its card
  right under the row if it isn't one of the cards (`openArea` in `NextScreen`).
- Exercises tab has a **search** box (name or equipment); areas with no match are hidden while searching.
- Area-only logs (`Session.areas`) exist for days he just writes "upper"/"lower".
- Deleting a movement that history uses only hides it (`Exercise.hidden`), so old workouts keep its name.

- **Insights tab** (`ui/InsightsScreen.kt`): this week / avg per week (last 8 full weeks; a day counts if it has movements or areas, or tag "workout" = old log day with no details) / streak (weeks in a
  row with 3+ workouts; injury weeks skipped), 17-week heatmap, then for a 4 wk / 8 wk / All window: each area's
  % of days on track with its every-N-days target, best first (same credit rules as Next; injury days left out), most-done
  movements, and movements not done in 30+ days, most recent first (plus never-done). Tapping a movement there or in History opens
  `ProgressDialog`: heaviest weight per day (else most reps, else longest time) with the best marked, plus every day logged.

## Architecture

Single-activity Jetpack Compose app, no DI, no database library (same pattern as Slam Log).

- `MainActivity.kt`: bottom tabs (Next, Log, History, Insights, Exercises, Areas), snackbar, back goes to Next.
- `ui/AppViewModel.kt`: holds `AppData`, the workout being logged (`picked`, `logDate`, `logNote`),
  all edits, backup export/import.
- `data/Models.kt`: `Area`, `Exercise`, `SetGroup`, `Item`, `Session`, `AppData` with org.json (de)serialization.
  New fields need defaults and fallbacks so older files and backups keep loading.
- `data/Store.kt`: `workouts.json` in app storage; first launch starts from `assets/catalog.json` + `assets/history.json`.
- `data/CatalogUpgrade.kt`: applies a newer built-in exercise list to existing data, once per catalogVersion.
- `logic/Recommend.kt` (`Stats`; `resetDays` = every day an area counted as worked), `logic/Insights.kt`
  (Insights tab: weekly consistency + streak, heatmap, area on-schedule %, most done, not done in 30+ days,
  `progressFor` = per-movement chart data), `logic/Format.kt` (dates, "4×12 @ 40 lb").
- `ui/`: one file per tab plus `Components.kt`, `Theme.kt`.

## Starter data

- The exercise list's source is the **LIST tab of `exercise catalog.xlsx`** (his spreadsheet, on his PC,
  git-ignored), grouped by area in the app's order. Columns: A main area, B exercise, C tool, D also works,
  E measures, F every session (x), G always first (x), H season ("Jun-Sep", blank = all year), and a
  "Video" column (found by header) with an instruction-video link.
  Never write the spreadsheet while he has it open in Excel; stage it fresh, write via a uniquely named
  output file with `expectedMtimeMs`, and check the md5 on his PC afterwards (a stale copy once overwrote it).
- `app/src/main/assets/catalog.json` (committed): body areas and the exercise list, with `catalogVersion`
  `retired` and `merged` (old id -> the movement it was folded into; history moves over on upgrade).
  Movements he dropped that history uses are kept with `hidden: true`.
- `app/src/main/assets/history.json` (git-ignored, on his PC only): his past workouts. The repo is
  public, so his history, spreadsheets and the web data files (`web/sessions.json`, `web/seed.py`) never
  get committed. Builds made without history.json start with an empty log (`Store.starterJson`).
- Regenerate both with `python web/to_seed.py "exercise catalog.xlsx" app/src/main/assets` (needs openpyxl;
  also reads `web/catalog.json` for existing ids and `web/sessions.json`). **Bump `CATALOG_VERSION` in
  to_seed.py whenever the list changes**: on the next launch `CatalogUpgrade` updates phones that already
  have the app (list wins for names/areas/measures; his targets and in-app additions stay; untouched
  starter-history days are refreshed). A copy of the old data is saved as `workouts-before-catalog-N.json`. Data decisions about his history are in `CLAUDE.local.md` (not committed).
- `web/workout-app.html` is the earlier web version (published as a claude.ai artifact); the phone app replaces it.

## Build and install

Same portable toolchain as Slam Log (no Android Studio). Paths: `D:\Users\<username>\Android\jdk-17` and
`...\Android\sdk`; `local.properties` (not committed) points at the SDK.
- Double-click `install.bat` (builds `assembleDebug`, installs with `adb install -r`, keeps data).
- Signing: `app/debug.keystore` is the same key as Slam Log's. Never replace it, or updates need an
  uninstall, which deletes his log.
- Versions match Slam Log / Range Log so cached dependencies are reused: AGP 8.7.3, Kotlin 2.0.21,
  Compose BOM 2024.12.01, core-ktx 1.15.0, Gradle 8.11.1.

## Conventions

- Data lives only on the phone; the Backup card on the Exercises tab saves/restores a JSON file.
- Don't uninstall the app to fix install problems: that deletes his history. Use `install -r`.
- Plain wording in the UI. Weights are pounds.
