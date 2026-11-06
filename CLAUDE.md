# CLAUDE.md: Workout Log

Michael's personal Android app for his home-gym workouts. He is the only user. It tells him which
body areas are due and, for each, which movements he hasn't done lately, and logs each workout.
Explain build and install steps plainly; he is not a day-to-day Android developer.

## What matters most

- **Next tab answers two questions:** which body areas to work today, and within each, which movements
  he hasn't done recently (never-logged first, then oldest). Keep that front and center.
- Areas (Chest, Back, Shoulders, Arms, Legs, Glutes & Hamstrings, Core, Conditioning, Mobility) each have a
  target "every N days" (`Area.target`, editable on the Exercises tab).
- A movement's `areas` list = main area first, then areas it also works. Direct work counts fully;
  secondary work counts as if it were half a target interval older (`Stats` in `logic/Recommend.kt`).
- Recommendation (`Stats.focus`): up to 3 strength areas at/over target and not worked in the last day,
  topped up to 2 with ones at >= 75% of target, plus Conditioning/Mobility when due.
- Each movement has `measures` (from `Measure.ALL`: sets, reps, weight, time, band, resistance, speed,
  incline); the Log tab shows only those fields. A logged movement has one or more lines (`SetGroup`;
  weight in lb, time stored in seconds) plus a free note. Time is typed in minutes for cardio
  (`Exercise.timeInMinutes`: Conditioning, or measures speed/incline/resistance), seconds otherwise; "1:30" works in both.
  New picks are pre-filled with what he did last time.
- Area-only logs (`Session.areas`) exist for days he just writes "upper"/"lower".
- Deleting a movement that history uses only hides it (`Exercise.hidden`), so old workouts keep its name.

## Architecture

Single-activity Jetpack Compose app, no DI, no database library (same pattern as Slam Log).

- `MainActivity.kt`: bottom tabs (Next, Log, History, Exercises), snackbar, back goes to Next.
- `ui/AppViewModel.kt`: holds `AppData`, the workout being logged (`picked`, `logDate`, `logNote`),
  all edits, backup export/import.
- `data/Models.kt`: `Area`, `Exercise`, `SetGroup`, `Item`, `Session`, `AppData` with org.json (de)serialization.
  New fields need defaults and fallbacks so older files and backups keep loading.
- `data/Store.kt`: `workouts.json` in app storage; first launch starts from `assets/catalog.json` + `assets/history.json`.
- `data/CatalogUpgrade.kt`: applies a newer built-in exercise list to existing data, once per catalogVersion.
- `logic/Recommend.kt` (`Stats`), `logic/Format.kt` (dates, "4×12 @ 40 lb").
- `ui/`: one file per tab plus `Components.kt`, `Theme.kt`.

## Starter data

- The exercise list's source is the **LIST tab of `exercise catalog.xlsx`** (his spreadsheet, on his PC,
  git-ignored): main area, exercise, tool, also works, measures.
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
