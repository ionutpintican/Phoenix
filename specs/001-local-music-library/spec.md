---
spec: 001
title: Local Music Library
status: Implemented
version: 1.0.0
last-updated: 2026-07-28
owners: [ionut.pintican]
source:
  - app/src/main/java/com/phoenix/playback/MusicLibrary.kt
  - app/src/main/java/com/phoenix/playback/Track.kt
related: [002-playback-engine, 004-android-auto, 008-now-playing, 009-search, 010-shortcuts-and-settings]
---

# Local Music Library

## Overview

The single source of truth for on-device audio. It scans Android's MediaStore into a flat
set of **leaf folders** (each directory that directly contains audio, treated as a
"playlist"), and exposes sorting, folder lookup, and search. The same in-memory library is
read by both the phone Compose UI and the car `PlaybackService`.

## User scenarios

- **Given** the user has granted audio access, **when** the app starts, **then** every
  MediaStore-indexed audio file is loaded and its containing folders appear as a
  browsable, alphabetically-sorted list.
- **Given** the user copies new music onto the device, **when** they tap **Rescan**,
  **then** the folder and song lists refresh to include the new files.
- **Given** a folder named "Audiobooks" that contains only sub-folders of audiobook files,
  **when** the user plays it, **then** every track beneath it plays (not nothing).
- **Given** the same folder name exists on internal storage and on an SD card, **when** a
  shortcut resolves that name, **then** the SD-card copy under `Music/` is preferred.

## Functional requirements

- **FR-001-1** The library SHALL query MediaStore audio and include a file when
  `IS_MUSIC != 0` **or** (API 29+) `IS_AUDIOBOOK != 0`, so audiobook-flagged files are not
  silently excluded.
- **FR-001-2** On API 29+ the library SHALL read `RELATIVE_PATH` and `VOLUME_NAME`; on
  older devices it SHALL derive the relative path from the file `DATA` column.
- **FR-001-3** The library SHALL group tracks into **leaf folders** keyed by their
  trimmed relative path, one `Folder` per directory that directly holds audio, and expose
  them sorted case-insensitively by folder name.
- **FR-001-4** Each `Track` SHALL carry id, title (defaulting to "Unknown" when absent),
  artist, album, playable content URI, album-art URI, relative path, date-modified, and
  duration. Its media id SHALL be `song:<id>`.
- **FR-001-5** The library SHALL expose a global **sort mode** — one of
  `NAME_ASC`, `NAME_DESC`, `DATE_ASC`, `DATE_DESC` — as observable state, applied to every
  folder/song listing. Changing it SHALL notify observers.
- **FR-001-6** The library SHALL expose a **revision** counter that increments on every
  (re)scan so observers (phone UI, slideshow) can refresh.
- **FR-001-7** `Rescan` SHALL re-read MediaStore and rebuild folders and tracks; the app
  SHALL never gate its content on a single permission grant — it loads whatever is readable.
- **FR-001-8** `playableTracksFor(folderId)` SHALL return the folder's direct tracks, or —
  when the folder has no direct tracks (a folder of sub-folders) — every track beneath it,
  sorted.
- **FR-001-9** `folderIdByName(name)` SHALL resolve a folder by case-insensitive last-segment
  name, preferring a path containing `Music/` and a non-primary (SD) volume; failing a leaf
  match it SHALL resolve the name as an ancestor path segment; it SHALL return null when
  nothing matches.
- **FR-001-10** Loading SHALL be safe to call from a background thread and idempotent
  (`ensureLoaded` loads only once; `load`/`rescan` force a re-read).

## Key entities

- **Track** — one playable local file (see FR-001-4). Media id `song:<id>`.
- **Folder** — `id` (normalized relative path, e.g. `Music/Leni`), `name` (last segment),
  `path` (`id/` prefix), `volume` (MediaStore volume name).
- **SortMode** — `NAME_ASC | NAME_DESC | DATE_ASC | DATE_DESC`.

## Edge cases & rules

- A `.nomedia` file legitimately hides a folder from MediaStore; Rescan cannot reveal it.
- The MediaStore volume of each track is threaded through a sidecar map (`TrackVolumes`) so
  `Track` stays a clean data class; this backs the SD-card preference in FR-001-9.
- Folder-preference scoring: `+2` for a `Music/` path, `+1` for a non-primary volume.

## Non-goals

- No filesystem traversal outside MediaStore (constitution **P4**).
- No editing of tags, no playlists-as-files, no on-device transcoding.
- Search behaviour is specified separately (spec 009).

## Open questions

_None — reverse-engineered from shipping code._
