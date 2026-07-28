---
spec: 005
title: Internet Radio
status: Implemented
version: 1.0.0
last-updated: 2026-07-28
owners: [ionut.pintican]
source:
  - app/src/main/java/com/phoenix/radio/RadioBrowser.kt
  - app/src/main/java/com/phoenix/radio/RadioFavorites.kt
  - app/src/main/java/com/phoenix/radio/RadioRecents.kt
  - app/src/main/java/com/phoenix/radio/RadioStation.kt
  - app/src/main/java/com/phoenix/ui/RadioScreen.kt
related: [002-playback-engine, 004-android-auto, 008-now-playing, 009-search]
---

# Internet Radio

## Overview

Internet radio backed by the **radio-browser.info** open directory: top stations, name
search, persisted favorites (seeded on first launch), and a recently-played list. The
browse list is grouped Favorites → Recently played → All stations, de-duplicated across
groups, and enriched with fresh reachability/vote data. Favorites and recents survive
offline and process trims because the **full station** is persisted, not just an id.

## User scenarios

- **Given** the first-ever launch, **when** the Radio list opens, **then** a small set of
  reliable default favorites is present (so the list isn't empty), persisted thereafter.
- **Given** the Radio screen, **when** it loads, **then** stations appear grouped Favorites,
  Recently played, then All stations, each row showing country, an "OK" badge when the stream
  was reachable at last check, and a vote count.
- **Given** any station row, **when** its heart is tapped, **then** it toggles favorite and
  the change is reflected live on the phone and the car.
- **Given** a station, **when** the user plays it, **then** it is recorded to the top of
  recently-played (de-duped by stream URL, capped at 10) and queued alongside the list it
  came from.
- **Given** the user searches stations, **when** results return, **then** matching favorites
  are surfaced first, followed by directory hits, de-duplicated.

## Functional requirements

- **FR-005-1** The client SHALL fetch top stations (`/stations/topvote/<limit>`, default 50)
  and search by name (`/stations/byname/<q>?limit=50&order=votes&reverse=true`) from
  radio-browser.info with an app User-Agent and 8 s connect/read timeouts.
- **FR-005-2** Any network failure SHALL yield an empty list, never a crash; top and search
  results SHALL be lightly cached (search cache ~12 queries; Auto issues two calls per query).
- **FR-005-3** A `RadioStation` SHALL carry uuid, name, stream URL, favicon, votes, country,
  and an optional `lastCheckOk` (null = unknown). Its media id SHALL be `radio:<uuid>`.
  It SHALL parse from both the API shape (`fromApi`, preferring `url_resolved`) and the
  persisted JSON shape (`fromJson`), round-tripping `lastCheckOk` only when known.
- **FR-005-4** The browse list SHALL be grouped into sections — Favorites, Recently played
  (excluding favorites), All stations — **de-duplicated by stream URL** (not uuid), keeping a
  station in the highest section it appears in.
- **FR-005-5** Favorites and recents SHALL be **enriched** from their fresh top-list twin so
  they show an up-to-date reachability badge and vote count.
- **FR-005-6** Favorites SHALL be persisted as full-station JSON in SharedPreferences and
  exposed as observable state; toggling SHALL update phone and car live. First launch SHALL
  seed `DEFAULT_FAVORITES` (SomaFM / Radio Paradise / Radio Swiss Jazz); once edited, defaults
  never re-seed (removed defaults stay removed).
- **FR-005-7** Recently-played SHALL be persisted the same way, most-recent-first, de-duped by
  stream URL, capped at 10, exposed as observable state.
- **FR-005-8** A played station SHALL be recorded the moment it becomes the current media item
  — on phone or car, whether reached by tapping, skipping the queue, or the now-playing Radio
  button.
- **FR-005-9** `stationByMediaId` SHALL resolve a `radio:<uuid>` id against favorites + recents
  + the volatile browse/search/top caches, so a station stays resolvable after a process trim.
- **FR-005-10** `queueContaining(station)` SHALL return whichever remembered list (search,
  browse, top, favorites, recents) contains the station, else just the station itself.
- **FR-005-11** Radio row/subtitle rendering SHALL show country · "OK" (only when
  `lastCheckOk == true`) · "▲ <votes>" with compact vote formatting (`1234 → 1.2k`,
  `15300 → 15k`); missing pieces are omitted.

## Key entities

- **RadioStation** — see FR-005-3.
- **RadioSection** — a titled group (`Favorites` / `Recently played` / `All stations`).
- **RadioFavorites** / **RadioRecents** — persisted `StateFlow`-backed stores.

## Edge cases & rules

- De-dup key is `streamUrl.ifBlank { uuid }`, so a seeded favorite and its top-list twin
  (different ids, same stream) collapse to one entry.
- Radio has no real duration, so it never crossfades (spec 003) and shows no seek bar (008).
- Blanket cleartext HTTP is deliberate — many stations stream over plain HTTP from
  unpredictable hosts (see spec 011).

## Non-goals

- No station editing/creation, no genre/country directory browsing beyond top + name search.
- No song-title metadata scraping from the stream.

## Open questions

_None — reverse-engineered from shipping code._
