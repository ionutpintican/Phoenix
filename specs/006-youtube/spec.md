---
spec: 006
title: YouTube Playlists & Search
status: Implemented
version: 1.0.0
last-updated: 2026-07-28
owners: [ionut.pintican]
source:
  - app/src/main/java/com/phoenix/youtube/YouTubeBrowser.kt
  - app/src/main/java/com/phoenix/youtube/YouTubeDownloader.kt
  - app/src/main/java/com/phoenix/youtube/YouTubePlaylists.kt
  - app/src/main/java/com/phoenix/youtube/YouTubeTrack.kt
  - app/src/main/java/com/phoenix/ui/YouTubeScreen.kt
related: [002-playback-engine, 004-android-auto]
---

# YouTube Playlists & Search

## Overview

Audio playback of YouTube content via **NewPipeExtractor** (the engine NewPipe/ViMusic
use): save playlists by link/id, search videos, and play either — with audio streams
resolved **just-in-time** so queued tracks stay playable long after any transient URL would
expire (constitution **P5**). Saved playlists appear on the phone and as a car tab.

> **Caveat (documented in code):** NewPipeExtractor talks to YouTube's private InnerTube
> API — against YouTube's ToS, fine for a personal device, not distributable — and is
> inherently fragile: a YouTube-side change can break extraction until the extractor is
> bumped. Treat a version bump as routine maintenance.

## User scenarios

- **Given** the YouTube screen, **when** the user pastes a playlist link/id and taps **+**,
  **then** the playlist is saved (title fetched in the background) and appears in the list and
  on the car tab.
- **Given** the same box, **when** the user types text and taps **search**, **then** a flat
  list of playable video results appears; tapping one plays the results as a queue from it.
- **Given** a saved playlist, **when** tapped, **then** its tracks load on demand; tapping a
  track plays the whole playlist starting at it.
- **Given** a queued YouTube track whose stream URL has since expired, **when** it plays,
  **then** a fresh stream URL is resolved transparently and it still plays.
- **Given** a private or unfetchable playlist, **when** opened, **then** an explanatory empty
  state is shown, never a crash.

## Functional requirements

- **FR-006-1** A `YouTubeTrack` SHALL store only `videoId` + light metadata (title, artist,
  duration seconds, thumbnail) — **never** a stream URL. Media id `yt:<videoId>`; playback URI
  the custom scheme `yt://i/<videoId>`.
- **FR-006-2** The playback URI SHALL be resolved to a real audio-stream URL at read time by
  the service's `ResolvingDataSource`; resolution SHALL run on the player's load thread
  (blocking there is expected).
- **FR-006-3** Stream URLs SHALL be cached only briefly (TTL ~4 h, under YouTube's ~6 h
  validity) and never persisted; resolution failure SHALL return null → a normal player error
  for that item (constitution **P6**), not a crash.
- **FR-006-4** Stream selection SHALL prefer an **m4a** direct-URL stream at the highest
  bitrate, else the highest-bitrate usable audio stream.
- **FR-006-5** `playlistTracks(id)` SHALL page the playlist (capped at 400 tracks), cache the
  result by playlist id, and cache each track by video id; failure SHALL return an empty list.
- **FR-006-6** `search(query)` SHALL return first-page **video** results only (channels/
  playlists dropped), capped at 30, each cached by video id.
- **FR-006-7** `extractPlaylistId(input)` SHALL accept a full watch/playlist URL (`list=…`),
  a bare id, or a music.youtube.com link, and return null when nothing playlist-shaped is
  found.
- **FR-006-8** Saved playlists SHALL persist `{id, title}` as JSON in SharedPreferences,
  exposed as observable state; add SHALL no-op on a duplicate id, and title SHALL be updated
  once the real name is fetched. Remove SHALL be supported.
- **FR-006-9** A tapped track SHALL queue with its playlist siblings positioned at it
  (`queueContaining`); when no cached playlist contains it, just the track itself.
- **FR-006-10** `trackByMediaId` SHALL resolve a `yt:` id back to cached metadata; a
  URI-derivable minimal item SHALL be reconstructable from the id alone when metadata is
  absent, so a restored queue item never becomes silent.
- **FR-006-11** The HTTP bridge (`YouTubeDownloader`, OkHttp, 15 s timeouts) SHALL translate
  NewPipe requests/responses and surface HTTP 429 as `ReCaptchaException` (rate-limit/bot
  check), sending a desktop-Chrome User-Agent when none is set.
- **FR-006-12** All NewPipe calls SHALL be made off the main thread; `NewPipe.init` SHALL run
  once (guarded).

## Key entities

- **YouTubeTrack** — `videoId`, title, artist, `durationSec`, `thumbnailUrl`; ids as above.
- **YouTubePlaylistRef** — `{id, title}`, media id `ytpl:<id>`.
- **YouTubeBrowser** — extraction client + in-memory caches (track, playlist, stream URL).
- **YouTubeDownloader** — OkHttp-backed NewPipe `Downloader`.

## Edge cases & rules

- Track duration `0` means unknown (e.g. a live item); `formatDuration` renders `h:mm:ss` /
  `m:ss`, blank when unknown.
- Playlist track lists and per-video metadata are cached so a played track resolves by media
  id after browse caches are gone; stream URLs are the one thing never persisted.

## Non-goals

- No YouTube sign-in, no personal library/subscriptions, no video (audio only), no
  downloads-to-disk, no official Data API.

## Open questions

_None — reverse-engineered from shipping code._
