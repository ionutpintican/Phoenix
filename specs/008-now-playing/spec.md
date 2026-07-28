---
spec: 008
title: Now-Playing & Artwork Slideshow
status: Implemented
version: 1.0.0
last-updated: 2026-07-28
owners: [ionut.pintican]
source:
  - app/src/main/java/com/phoenix/ui/NowPlayingScreen.kt
  - app/src/main/java/com/phoenix/ui/Artwork.kt
  - app/src/main/java/com/phoenix/playback/GallerySlideshow.kt
  - app/src/main/java/com/phoenix/playback/PlaybackService.kt
related: [002-playback-engine, 004-android-auto, 005-internet-radio, 007-phone-app-shell, 011-platform-integration]
---

# Now-Playing & Artwork Slideshow

## Overview

The now-playing experience on both surfaces, plus the car artwork slideshow. The phone shows
a full player (art, title/artist, seek bar for songs, transport, shuffle, search, and a radio
heart). The car now-playing artwork cycles a track's own art and then random gallery photos.
A hand-rolled artwork loader decodes album art, radio favicons, and gallery photos (this build
ships no image-loading library).

## User scenarios

- **Given** a song is playing, **when** the now-playing screen is shown, **then** it shows
  album art, title, artist, a working seek bar, and transport controls (previous, play/pause,
  next, shuffle) plus search.
- **Given** a radio station is playing, **when** on now-playing, **then** no seek bar is shown
  (no duration) and a favorite **heart** appears reflecting/toggling favorite state.
- **Given** the seek bar, **when** the user drags the thumb, **then** the dragged position is
  shown while held and the seek commits on release (the 500 ms poll doesn't yank it back).
- **Given** a track plays in the car and gallery photos are available, **when** on the car
  now-playing screen, **then** the artwork leads with the track's own art for one interval,
  then rotates through random gallery photos (~12 s each), restoring the real art on track
  change.
- **Given** no gallery photos (or no Images permission), **when** a track plays in the car,
  **then** the track's own artwork simply stays up.

## Functional requirements

- **FR-008-1** The phone now-playing screen SHALL show the shortcut bar, the current
  artwork (album art / radio favicon, following the service's slideshow), title, artist, and
  a transport row: search, shuffle (tinted when on), previous, play/pause, next.
- **FR-008-2** A **seek bar** SHALL be shown only when duration > 0 (songs); it SHALL display
  current/total time and use held-drag-then-commit-on-release scrubbing.
- **FR-008-3** A favorite **heart** SHALL be shown only while a `radio:` item is playing, its
  filled/outline state tracking favorites, tapping toggling favorite (spec 005).
- **FR-008-4** The car now-playing artwork SHALL cycle on a ~12 s interval only while playing:
  on a track change it SHALL first restore the track's own artwork (album art for a song,
  favicon for radio, thumbnail for YouTube) and hold it one interval; subsequent ticks SHALL
  show random gallery photos.
- **FR-008-5** Artwork swaps SHALL replace only the current item's artwork URI (same media
  URI, no audio interruption).
- **FR-008-6** The gallery slideshow SHALL read all gallery image URIs from MediaStore once,
  hand them out in a fresh shuffled order each pass, and tolerate a missing/partial Images
  grant by leaving the pool empty (a later grant triggers a reload — see FR-008-7). It SHALL
  never crash on a `SecurityException`.
- **FR-008-7** The slideshow pool SHALL reload whenever the library revision bumps, so a
  late-granted Images permission (the car load races the phone's permission dialog) actually
  populates the slideshow.
- **FR-008-8** The phone artwork loader SHALL decode `content://` (album art, gallery) and
  `http(s)` (radio favicon) images on a background thread with a two-pass downsample (target
  ~1024 px) and an in-memory LRU cache (64), re-decoding only on URI change, returning null
  (→ placeholder) while loading or on failure. The car uses Media3's own BitmapLoader, not
  this.

## Key entities

- **GallerySlideshow** — shuffled gallery-photo supplier (car only).
- **rememberArtwork / artCache** — phone-side decoder + LRU cache.
- **slideshowMediaId** — tracks the item the car slideshow is cycling for.

## Edge cases & rules

- The slideshow and crossfade both poll the main player; only mutate the player on the main
  thread.
- Radio duration is 0, so the seek bar is intentionally absent for stations.

## Non-goals

- No lyrics, no visualizer, no full-screen artwork gestures.
- No image-loading third-party library (deliberate — keeps the APK small).

## Open questions

_None — reverse-engineered from shipping code._
