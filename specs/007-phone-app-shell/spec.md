---
spec: 007
title: Phone App Shell & Navigation
status: Implemented
version: 1.0.0
last-updated: 2026-07-28
owners: [ionut.pintican]
source:
  - app/src/main/java/com/phoenix/MainActivity.kt
  - app/src/main/java/com/phoenix/PhoenixApp.kt
  - app/src/main/java/com/phoenix/ui/BrowseScreen.kt
  - app/src/main/java/com/phoenix/ui/BrowseNav.kt
  - app/src/main/java/com/phoenix/ui/theme/Theme.kt
  - app/src/main/java/com/phoenix/ui/UiExt.kt
related: [001-local-music-library, 002-playback-engine, 005-internet-radio, 006-youtube, 008-now-playing, 009-search, 010-shortcuts-and-settings]
---

# Phone App Shell & Navigation

## Overview

The phone-side Compose application: a single `MainActivity` hosting one `PlayerViewModel`
and a hand-rolled screen switcher across six screens (Browse, Radio, YouTube, NowPlaying,
Search, Settings). It defines app-wide chrome — edge-to-edge, theme, the shared shortcut bar,
the persistent mini now-playing bar, folder browsing, the sort rail, and Rescan.

## User scenarios

- **Given** the app launches, **when** `MainActivity` starts, **then** it requests media
  permissions, enables edge-to-edge, and shows the Browse screen at the root folder list.
- **Given** the Browse root, **when** a folder is tapped, **then** its songs show; **when**
  system back is pressed inside a folder, **then** it returns to the folder list.
- **Given** a song row is tapped, **when** selected, **then** the folder plays as a queue from
  that song and the app switches to the now-playing screen.
- **Given** a letter/icon shortcut in the bar, **when** tapped, **then** its folder
  shuffle-plays (from Browse/Radio it opens the folder; from now-playing it stays put).
- **Given** anything is playing, **when** on Browse/Radio/YouTube/Search, **then** a mini
  now-playing bar sits above the system nav buttons and opens the full player when tapped.

## Functional requirements

- **FR-007-1** `PhoenixApp` (the `Application`) SHALL initialize persisted stores on startup
  as needed so favorites/recents/playlists/settings are ready. (Service-side init is also
  performed in `PlaybackService.onCreate`.)
- **FR-007-2** `MainActivity` SHALL own a single `PlayerViewModel` shared by all screens and
  render inside the app theme with edge-to-edge enabled.
- **FR-007-3** Navigation SHALL be a simple in-memory screen enum
  (`Browse | Radio | YouTube | NowPlaying | Search | Settings`) plus an `openFolder` id;
  there is no nav-graph library.
- **FR-007-4** Selecting a library song (from Browse or Search) SHALL switch both the browsed
  folder and the play queue to that song's folder, positioned at the song, then show
  now-playing.
- **FR-007-5** A letter shortcut SHALL resolve its folder via `folderIdByName`; from Browse it
  opens the folder and shuffle-plays it; from now-playing it shuffle-plays without leaving the
  screen.
- **FR-007-6** The Browse screen SHALL show, top to bottom: the shortcut bar (with YouTube +
  Radio buttons), the four-way sort rail, a header row (Search / Shuffle-toggle / Rescan /
  Settings actions), and either the folder list (root) or the folder's song list.
- **FR-007-7** The folder song list SHALL re-sort live when the global sort mode changes, and
  refresh on library revision changes.
- **FR-007-8** Rescan SHALL re-scan MediaStore on a background thread (spec 001).
- **FR-007-9** The shared `LetterShortcutBar` SHALL render each shortcut as a letter or icon
  per `Settings.shortcuts`, disable a shortcut whose folder isn't present, and wrap to a second
  line on narrow screens; it SHALL optionally show YouTube and Radio buttons.
- **FR-007-10** The mini `NowPlayingBar` SHALL be hidden when nothing is playing, show
  title/artist and a play/pause toggle, be pushed above the system nav bar via
  `navigationBarsPadding` while its background fills to the edge, and open the full player on
  tap. The car never uses this composable.
- **FR-007-11** Album-art thumbnails SHALL fall back to a music-note icon when art can't load.

## Key entities

- **Screen** — the navigation enum.
- **PlayerViewModel** — the shared session bridge (spec 002).
- **LetterShortcutBar** / **NowPlayingBar** — shared composables used across screens.

## Edge cases & rules

- Back handling is per-screen (`BackHandler`): inside a folder returns to root; sub-screens
  return to their origin.
- The Browse shuffle toggle is the shared player shuffle state, so it lets the user choose
  order before playing.

## Non-goals

- Individual screen contracts for Radio (005), YouTube (006), Search (009), Settings (010),
  and NowPlaying (008) live in their own specs.
- No tablet/landscape-specific layout, no bottom-nav component.

## Open questions

_None — reverse-engineered from shipping code._
