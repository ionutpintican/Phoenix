---
spec: 002
title: Playback Engine & Media Session
status: Implemented
version: 1.0.0
last-updated: 2026-07-28
owners: [ionut.pintican]
source:
  - app/src/main/java/com/phoenix/playback/PlaybackService.kt
  - app/src/main/java/com/phoenix/playback/PlayerViewModel.kt
related: [001-local-music-library, 003-gapless-crossfade, 004-android-auto, 005-internet-radio, 006-youtube, 007-phone-app-shell]
---

# Playback Engine & Media Session

## Overview

The one audio pipeline for the whole app: a Media3 `MediaLibraryService`
(`PlaybackService`) hosting a single `MediaLibrarySession` backed by an `ExoPlayer`. The
phone UI drives playback as a `MediaController` client of that session; Android Auto renders
the same session. This spec covers session lifecycle, queue building/resolution, transport,
audio focus, and the phone↔session bridge. (Browse tree: spec 004. Crossfade: spec 003.)

## User scenarios

- **Given** any surface, **when** a track/station is selected, **then** it plays through the
  same session, and the now-playing state (title, artist, artwork, position, shuffle) is
  identical on phone and car.
- **Given** a queue is playing, **when** the user taps next/previous or scrubs, **then** the
  player advances/seeks and every observing surface updates.
- **Given** the app is backgrounded during playback, **when** audio is playing, **then** it
  continues as a foreground media-playback service with a media notification.
- **Given** another app takes audio focus (a call), **when** focus is lost, **then**
  playback pauses per Media3 focus handling; unplugging headphones pauses ("becoming noisy").

## Functional requirements

- **FR-002-1** The service SHALL expose exactly one `MediaLibrarySession`; `onGetSession`
  SHALL return it for every controller. No other component may own the queue or audio focus.
- **FR-002-2** The main `ExoPlayer` SHALL be built with `USAGE_MEDIA` /
  `AUDIO_CONTENT_TYPE_MUSIC` audio attributes, **with** audio-focus handling enabled and
  handle-audio-becoming-noisy enabled.
- **FR-002-3** The session SHALL declare a session activity that launches `MainActivity`, so
  tapping the media notification opens the app.
- **FR-002-4** The service SHALL run as a foreground service of type `mediaPlayback` and
  register the media-session + legacy media-browser intent filters so Android Auto can bind.
- **FR-002-5** When a controller sends a **multi-item** queue (the phone UI), the session
  SHALL re-attach each item's playable URI from its media id while preserving the caller's
  exact order and start index (never collapsing to the first item's folder).
- **FR-002-6** When a controller/car sends a **single** tapped item, the session SHALL
  expand it into its full play queue positioned at that item:
  - `cmd:shuffle_all` → all tracks, shuffled;
  - `cmd:shortcut|<i>` → that shortcut's folder, shuffled (spec 010);
  - `folder:<id>` → the folder's playable tracks;
  - `song:<id>` → the song's folder as a queue, positioned at the song;
  - `radio:<uuid>` → the station's list, positioned at it (spec 005);
  - `ytpl:<id>` → the whole playlist; `yt:<videoId>` → its playlist queue positioned at it
    (spec 006).
- **FR-002-7** A controller cannot carry a local `content://` URI across the session, so the
  session SHALL reconstruct each item's URI from its media id on add/set; an item whose id
  can't be resolved SHALL fall back to any carried URI (radio `requestMetadata.mediaUri`, or
  a yt URI derivable from the id) rather than becoming a silent, URI-less item.
- **FR-002-8** The phone `PlayerViewModel` SHALL connect to the session via a
  `MediaController` and expose observable state: `isPlaying`, `currentMediaId`, `title`,
  `artist`, `artwork`, `shuffle`, `position`, `duration`.
- **FR-002-9** `duration` SHALL be reported as `0` when unknown (e.g. live radio); position
  SHALL be polled ~every 500 ms to drive the seek bar since the controller does not push it
  continuously.
- **FR-002-10** The ViewModel SHALL provide commands: play a track list at an index, play a
  folder shuffled, play a station queue, play a YouTube queue, toggle play/pause, next,
  previous, seek, toggle shuffle, and set sort mode.
- **FR-002-11** Shuffle SHALL be a single shared player state; toggling it anywhere (phone
  browse, phone now-playing, car button) SHALL update the player and every observer.
- **FR-002-12** A playback error (notably an unresolvable YouTube stream) SHALL be logged
  under tag `PlaybackService` and surfaced as a normal player error, never a crash.
- **FR-002-13** On destroy, the service SHALL cancel its scopes, shut down the browse
  executor, and release the session and both players.

## Key entities

- **MediaLibrarySession** — the single session (queue + transport + browse + search).
- **PlayerViewModel** — phone-side `MediaController` bridge exposing playback `StateFlow`s.
- **Media id schemes** — `song:` local, `radio:` station, `yt:` video, `ytpl:` playlist,
  `folder:` browse folder, and `cmd:*` virtual tiles.

## Edge cases & rules

- The browse/resolution callbacks run on a single-thread executor; player mutations run on
  the main thread (session callbacks are main-thread).
- The library is `ensureLoaded` at the top of each browse/resolution callback so a cold
  car connection still resolves ids.
- A queue restored after a process trim may reference ids absent from in-memory caches;
  FR-002-7 keeps such items playable.

## Non-goals

- Browse-tree shape and car UI (spec 004); crossfade (spec 003); the specific remote
  sources (specs 005, 006).
- No gapless-for-radio, no equalizer, no playback-speed control.

## Open questions

_None — reverse-engineered from shipping code._
