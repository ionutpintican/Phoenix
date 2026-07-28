---
spec: 004
title: Android Auto Browse & Now-Playing
status: Implemented
version: 1.0.0
last-updated: 2026-07-28
owners: [ionut.pintican]
source:
  - app/src/main/java/com/phoenix/playback/PlaybackService.kt
  - app/src/main/java/com/phoenix/playback/MusicLibrary.kt
  - app/src/main/res/xml/automotive_app_desc.xml
related: [002-playback-engine, 005-internet-radio, 006-youtube, 008-now-playing, 009-search, 010-shortcuts-and-settings]
---

# Android Auto Browse & Now-Playing

## Overview

Everything Phoenix presents in the car. On Android Auto no app draws its own screens
(constitution **P2**); the app feeds a browse tree, content-style hints, media metadata,
and session commands into Auto's standard media template. This spec defines the tab
structure, tiles, content style, the now-playing custom layout, and the car-side commands.

## User scenarios

- **Given** a head unit connected to Phoenix, **when** the root opens, **then** three
  browsable tabs appear: **Playlists**, **YouTube**, **Radio** — as a vertical list.
- **Given** the Playlists tab, **when** opened, **then** it lists the music folders; tapping
  one shows that folder's songs, preceded by the letter/icon shortcut tiles.
- **Given** any folder's song list, **when** a shortcut tile is tapped, **then** its folder
  shuffle-plays; **when** a song is tapped, **then** the folder plays as a queue from it.
- **Given** the now-playing screen in the car, **when** shown, **then** it displays only the
  standard transport controls plus search and a single shuffle toggle whose icon reflects the
  current shuffle state.
- **Given** the sort mode or favorites/recents/playlists change on the phone, **when** a car
  is connected, **then** the affected car lists refresh live.

## Functional requirements

- **FR-004-1** The app SHALL declare Android Auto support via `automotive_app_desc.xml`
  (`androidx.media3.session.MediaLibraryService`) and the media/browser intent filters.
- **FR-004-2** The library root SHALL expose exactly three tabs in order:
  `tab_playlists` ("Playlists"), `tab_youtube` ("YouTube"), `tab_radio` ("Radio"). There
  SHALL be no root-level letter tiles (which is what keeps Auto's overflow "More" tab away).
- **FR-004-3** The Playlists tab SHALL list `folder:<id>` browsable rows, one per music
  folder, matching the phone's folder list.
- **FR-004-4** A folder's children SHALL be the shortcut tiles (spec 010) followed by the
  folder's songs in the current sort order.
- **FR-004-5** Content style SHALL be **list** for both browsable and playable nodes
  everywhere (no grid tiles), so rows render in the template font and no broken-artwork "!"
  placeholder appears for items without loadable art.
- **FR-004-6** Shortcut and shuffle-all tiles SHALL be **text tiles** (title/subtitle, no
  artwork) so they render in the head unit's template font.
- **FR-004-7** The now-playing custom layout SHALL contain exactly one custom button — a
  **shuffle toggle** using Media3's predefined shuffle-on/off icons — and no letter, radio,
  or favorite buttons (constitution **P7**). Its icon SHALL track player shuffle state, the
  layout rebuilt on `onShuffleModeEnabledChanged`.
- **FR-004-8** The session SHALL accept these custom commands: per-shortcut play
  (`com.phoenix.SHORTCUT.<i>`), toggle favorite (`com.phoenix.TOGGLE_FAVORITE`), play radio
  (`com.phoenix.PLAY_RADIO`), toggle shuffle (`com.phoenix.TOGGLE_SHUFFLE`).
- **FR-004-9** `onGetItem` SHALL resolve every id scheme to a valid item and fall back to a
  benign "Unavailable" browsable node for an unknown id (constitution **P6**).
- **FR-004-10** Opening the root SHALL invalidate the three tab subtrees
  (`notifyChildrenChanged`) so re-entering a tab re-registers the search context and avoids
  Auto serving a stale cached tab.
- **FR-004-11** Live refresh SHALL be wired for: sort-mode change → every `folder:` node;
  favorites change → Radio tab + custom layout; recents change → Radio tab; YouTube
  playlists change → YouTube tab.
- **FR-004-12** The car SHALL track the **last browsed parent** so the single global search
  box is scoped correctly (spec 009), based on the *displayed* list, not what is playing.

## Key entities

- **Tabs** — `tab_playlists`, `tab_youtube`, `tab_radio`.
- **Virtual tiles** — `cmd:shuffle_all` (Shuffle all), `cmd:shortcut|<i>` (a shortcut).
- **CarShortcut** — resolved from `Settings.shortcuts`: index, caption text, folder, icon.
- **Content-style hints** — browsable/playable = LIST.

## Edge cases & rules

- A shortcut whose folder isn't on the device is **hidden** from the car tiles (and disabled
  on the phone bar).
- Browse/search callbacks run off the main thread (single-thread `browseExecutor`), so
  network-touching resolution (radio/YouTube) is safe there.
- `Shuffle all` exists as a browse entry that plays all tracks shuffled.

## Non-goals

- No custom car screens or grid art (P2, FR-004-5).
- Now-playing artwork slideshow is specified in spec 008.

## Open questions

_None — reverse-engineered from shipping code._
