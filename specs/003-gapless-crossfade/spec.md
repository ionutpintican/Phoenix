---
spec: 003
title: Gapless Crossfade
status: Implemented
version: 1.0.0
last-updated: 2026-07-28
owners: [ionut.pintican]
source:
  - app/src/main/java/com/phoenix/playback/PlaybackService.kt
related: [002-playback-engine, 010-shortcuts-and-settings]
---

# Gapless Crossfade

## Overview

An equal-power crossfade between consecutive **local** songs. A second, subordinate
`ExoPlayer` ("P2") plays the outgoing song's tail so its fade-out overlaps the incoming
song's fade-in. The main player ("P1") always remains the single source of truth for the
queue and audio focus. The dissolve length is user-configurable (spec 010); `0` disables it.

## User scenarios

- **Given** crossfade is set to N seconds and two local songs play back-to-back, **when**
  the first nears its end, **then** it fades out while the next fades in over ~N seconds at a
  constant perceived loudness (no mid-point dip, no click).
- **Given** crossfade is set to Off (0 s), **when** a song ends, **then** the next starts
  instantly with no fade.
- **Given** a crossfade is mid-dissolve, **when** the user taps next/previous or pauses,
  **then** the fade is abandoned cleanly (no half-faded volume, no stale tail keeps playing).
- **Given** the current or next item is radio or YouTube, **when** the song ends, **then**
  no crossfade runs (fades only ever occur between two local files).

## Functional requirements

- **FR-003-1** A crossfade SHALL run only when: crossfade length > 0, the player is playing,
  there is a next item, **and** both the current and next media ids start with `song:`.
- **FR-003-2** The crossfade SHALL use an **equal-power** curve — incoming volume
  `sin(angle)`, outgoing `cos(angle)` across the dissolve — so the two songs sum to a steady
  perceived level.
- **FR-003-3** P2 (the crossfade engine) SHALL **not** handle audio focus or the
  becoming-noisy event; only P1 does (constitution P1).
- **FR-003-4** The outgoing tail SHALL be **pre-armed** into P2 a few seconds ahead of the
  fade (parked paused, silent, seeked a lead-in before the fade-start position) so firing is
  instant with no load/seek stall; the arm SHALL be discarded if the user seeks out of range,
  skips, or pauses out of range.
- **FR-003-5** At fire time, P2 SHALL pick up the outgoing song at P1's exact position at
  full volume and fade down, while P1 advances to the next item (a self-flagged transition),
  silent, and fades up — keeping the advance gapless.
- **FR-003-6** A self-initiated crossfade transition SHALL be distinguished from a user skip
  so the transition listener does not tear down a legitimately running fade.
- **FR-003-7** Any non-self transition (user skip, or natural advance with no fade running)
  SHALL cancel any in-flight crossfade and disarm any preload.
- **FR-003-8** Pausing/resuming mid-crossfade SHALL keep P2's playback in lock-step with P1.
- **FR-003-9** On finish or cancel, P1 volume SHALL be restored to full and P2 stopped and
  cleared. This SHALL be safe to call whether or not a fade is running.
- **FR-003-10** A song shorter than the crossfade window SHALL still crossfade once near its
  end.

## Key entities

- **crossfadePlayer (P2)** — subordinate `ExoPlayer`, tail-only, no focus/queue ownership.
- **Crossfade state** — `crossfading`, `crossfadeArmed`, `selfCrossfadeTransition`,
  `crossfadeJob` (main-thread-only).

## Edge cases & rules

- Timing constants (as built): poll 100 ms, fade tick 50 ms, preload lead 4000 ms, park
  lead-in 700 ms. These are tuning parameters, not contract; the *behaviour* above is.
- Parking a lead-in **before** the fade-start (not exactly on it) keeps the fire-time
  alignment seek mid-buffer, which is what makes the hand-off click-free.

## Non-goals

- No crossfade across radio or YouTube (their tails can't be re-buffered locally).
- No configurable curve shape; equal-power only.

## Open questions

_None — reverse-engineered from shipping code._
