---
spec: 009
title: Context-Scoped Search
status: Implemented
version: 1.0.0
last-updated: 2026-07-28
owners: [ionut.pintican]
source:
  - app/src/main/java/com/phoenix/ui/SearchScreen.kt
  - app/src/main/java/com/phoenix/playback/MusicLibrary.kt
  - app/src/main/java/com/phoenix/playback/PlaybackService.kt
  - app/src/main/java/com/phoenix/radio/RadioBrowser.kt
related: [001-local-music-library, 004-android-auto, 005-internet-radio, 007-phone-app-shell]
---

# Context-Scoped Search

## Overview

Search whose **corpus is decided by context**, never mixed. Searching from a music view
searches the phone's song library; searching from the Radio view searches the online station
directory. This holds on both surfaces: the phone opens a shared search screen in the right
mode; the car's single global search box is scoped by the list currently on screen.

## User scenarios

- **Given** the Browse/now-playing (song) context, **when** the user searches, **then**
  matching songs across the whole library are shown; tapping one plays its folder from it.
- **Given** the Radio context (Radio list, or now-playing while a station plays), **when** the
  user searches, **then** matching stations are shown (favorites matching first); tapping one
  plays the results as a queue.
- **Given** the car showing the Radio list while a *song* plays, **when** the user searches,
  **then** stations are returned (not songs); and vice-versa. Never a mixed dump.

## Functional requirements

- **FR-009-1** Library search SHALL match song title or artist case-insensitively; with a null
  folder scope it spans every song, else only tracks at/below the given folder. Results follow
  the global sort mode.
- **FR-009-2** The phone search screen SHALL take a `searchRadio` flag choosing the corpus:
  radio (network fetch) vs library (in-memory filter), and auto-focus the input on open.
- **FR-009-3** Radio search SHALL run on the network as the query changes, surfacing matching
  **favorites first** then directory hits, de-duplicated by uuid; a blank query clears results.
- **FR-009-4** Library search SHALL be an in-memory filter recomputed on query/revision change
  (no folder scope on the phone search screen — it spans all songs).
- **FR-009-5** Tapping a library result SHALL hand the track back to the shell so it switches
  the browsed folder + queue to that song's folder positioned at it (spec 007), then show
  now-playing; tapping a radio result SHALL play the results list from that index.
- **FR-009-6** On the car, search SHALL be scoped by the **last browsed parent**: the Radio
  tab → station directory search; anywhere in music → whole-library song search. The displayed
  list decides the corpus, not what is playing.
- **FR-009-7** The car SHALL answer Android Auto's two-call search protocol (count then
  results) from a per-query cache so the corpus is computed once, and page results correctly.

## Key entities

- **searchRadio** (phone) / **lastBrowsedParent** (car) — the context selectors.
- **MusicLibrary.search** — library filter; **RadioBrowser.search** — directory fetch.

## Edge cases & rules

- The phone library search spans all folders (matching the intent that song search isn't
  pinned to the last-opened folder), even though `MusicLibrary.search` *can* scope to a folder.
- Radio search is cached (spec 005 FR-005-2) to avoid a double round-trip per query on Auto.

## Non-goals

- No unified cross-corpus results, no history/suggestions, no fuzzy ranking.
- No YouTube results in this shared search (YouTube search is its own screen — spec 006).

## Open questions

_None — reverse-engineered from shipping code._
