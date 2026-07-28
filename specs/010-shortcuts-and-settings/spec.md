---
spec: 010
title: Shortcuts & Settings
status: Implemented
version: 1.0.0
last-updated: 2026-07-28
owners: [ionut.pintican]
source:
  - app/src/main/java/com/phoenix/settings/Settings.kt
  - app/src/main/java/com/phoenix/settings/ShortcutIcons.kt
  - app/src/main/java/com/phoenix/ui/SettingsScreen.kt
  - app/src/main/java/com/phoenix/ui/BrowseNav.kt
related: [002-playback-engine, 003-gapless-crossfade, 004-android-auto, 007-phone-app-shell]
---

# Shortcuts & Settings

## Overview

User-editable configuration, phone-authored and live-mirrored to the car. Two settings today:
a fixed set of **four folder shortcuts** (each shown as a capital letter *or* a built-in icon,
each shuffle-plays its folder) and the **crossfade** length. Settings are staged in the phone
Settings screen and applied on Save; both the phone shortcut bar and the car read the same
persisted config reactively.

## User scenarios

- **Given** the Settings screen, **when** the user picks a folder for a shortcut and chooses
  Letter or Icon, **then** on Save the shortcut updates on the phone bar and in the car tiles.
- **Given** an unedited install, **when** first used, **then** the shortcuts default to
  L→Leni, A→Action, H→Hideout (letters) and Audiobooks (headphones icon).
- **Given** the crossfade slider, **when** set to a value 0–12 and saved, **then** songs
  dissolve over that many seconds (0 = off) — see spec 003.
- **Given** unsaved edits, **when** the user leaves without Save, **then** the edits are
  discarded; the Save button is enabled only when the draft is dirty and valid.
- **Given** a shortcut whose folder isn't on the device, **when** shown, **then** the phone
  button is disabled and the car tile is hidden.

## Functional requirements

- **FR-010-1** There SHALL be exactly **4** shortcuts (`SHORTCUT_COUNT`). Each
  `ShortcutSetting` has a `folderName` and **either** a single capital `letter` **or** an
  `iconKey` — never both.
- **FR-010-2** Defaults SHALL reproduce the app's original hardcoded shortcuts: `Leni`→"L",
  `Action`→"A", `Hideout`→"H" (letters), `Audiobooks`→headphones icon.
- **FR-010-3** Shortcuts SHALL persist as JSON in SharedPreferences and be exposed as
  observable state; a stored list whose size ≠ `SHORTCUT_COUNT` SHALL fall back to defaults
  (forward/back compat).
- **FR-010-4** Crossfade SHALL persist as an integer seconds value, clamped to `0..12`,
  default `7`, exposed as observable state; `crossfadeMs` SHALL derive from it (0 = off).
- **FR-010-5** The Settings screen SHALL stage edits in local draft state keyed on the saved
  values and write to `Settings` only on **Save**; Save SHALL be enabled only when dirty and
  valid (a letter-mode row is invalid until it has a letter).
- **FR-010-6** The folder picker SHALL offer the scanned folder names (deduped, sorted) plus
  the current value even if not currently scanned.
- **FR-010-7** A shortcut's appearance SHALL resolve to concrete assets via `ShortcutIcons`:
  a letter → the bundled `ic_letter_*` drawable and the letter glyph on the phone; an icon key
  → a curated drawable (for the car command button) + matching Compose vector (phone), from a
  registry of curated icons (music note, headphones, star, heart, radio, album, book).
- **FR-010-8** The shortcut bar (phone) and the car tiles SHALL both render from
  `Settings.shortcuts` so they mirror the same config; each shortcut **shuffle-plays** its
  resolved folder (`playableTracksFor(folder).shuffled()`).
- **FR-010-9** Settings SHALL be **phone-authored only**; the car reads current values and
  never edits them. Changes propagate live via the settings flows to both surfaces.

## Key entities

- **ShortcutSetting** — `{folderName, letter?, iconKey?}`.
- **CuratedIcon** — `{key, label, drawableRes, vector}` registry entry.
- **Settings** — persisted `StateFlow`-backed store (`shortcuts`, `crossfadeSeconds`).
- **DraftShortcut** — the Settings screen's staged, per-row editable copy.

## Edge cases & rules

- Letter mode is an explicit flag independent of whether the letter field is currently filled,
  so the field can be cleared and retyped without flipping to icon mode.
- `Settings` is designed to grow: "add a key + a flow + accessors and both consumers pick it
  up." A new setting is a **minor** spec bump here (or its own spec if substantial).

## Non-goals

- No reordering or variable count of shortcuts (fixed at 4).
- No import/export of settings, no per-device sync.

## Open questions

_None — reverse-engineered from shipping code._
