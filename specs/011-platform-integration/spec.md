---
spec: 011
title: Platform Integration (Permissions, Manifest, Lifecycle)
status: Implemented
version: 1.0.0
last-updated: 2026-07-28
owners: [ionut.pintican]
source:
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/phoenix/MainActivity.kt
  - app/src/main/res/xml/automotive_app_desc.xml
  - app/src/main/res/xml/backup_rules.xml
  - app/src/main/res/xml/data_extraction_rules.xml
  - app/build.gradle.kts
related: [001-local-music-library, 002-playback-engine, 004-android-auto, 005-internet-radio, 008-now-playing]
---

# Platform Integration (Permissions, Manifest, Lifecycle)

## Overview

The Android platform surface: permissions and their runtime request, the manifest
declarations (service, Auto, foreground-service types, cleartext), edge-to-edge and
predictive-back, and backup/data-extraction rules. This is the cross-cutting "future-proofing"
layer the other features rely on.

## User scenarios

- **Given** a fresh install on API 33+, **when** the app starts, **then** it requests
  `READ_MEDIA_AUDIO`, `READ_MEDIA_IMAGES`, and `POST_NOTIFICATIONS`; on older devices it
  requests `READ_EXTERNAL_STORAGE`.
- **Given** the user denies a permission, **when** the app runs, **then** it still loads
  whatever it can read (audio for the library, images for the car slideshow) — access is never
  gated on a single grant.
- **Given** playback in the background, **when** audio plays, **then** a foreground
  media-playback service with a media notification keeps it alive.
- **Given** a radio station streaming over plain HTTP, **when** it plays, **then** cleartext
  traffic is permitted (deliberate).
- **Given** predictive back on Android 14+, **when** the user swipes back, **then** the
  predictive-back animation is enabled.

## Functional requirements

- **FR-011-1** The manifest SHALL declare: `READ_MEDIA_AUDIO`; `READ_EXTERNAL_STORAGE`
  (`maxSdkVersion=32`); `READ_MEDIA_IMAGES` (car slideshow); `INTERNET` +
  `ACCESS_NETWORK_STATE` (radio/YouTube); `FOREGROUND_SERVICE` +
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; `POST_NOTIFICATIONS`.
- **FR-011-2** `MainActivity` SHALL request the version-appropriate permission set at startup
  and, on any result, (re)load the library on a background thread — never blocking app use on
  the outcome.
- **FR-011-3** `PlaybackService` SHALL be exported, `foregroundServiceType="mediaPlayback"`,
  and declare both the `MediaLibraryService` and legacy `MediaBrowserService` intent filters
  (Media3 bridges the latter for Android Auto).
- **FR-011-4** The app SHALL declare Android Auto support via the
  `com.google.android.gms.car.application` meta-data → `automotive_app_desc.xml`.
- **FR-011-5** The application SHALL set `appCategory="audio"`, enable
  `enableOnBackInvokedCallback` (predictive back), support RTL, and enable edge-to-edge in the
  activity.
- **FR-011-6** `usesCleartextTraffic` SHALL be `true` — deliberate, because radio streams from
  unpredictable hosts often use plain HTTP.
- **FR-011-7** The app SHALL allow backup and declare `dataExtractionRules` (Android 12+) and
  `fullBackupContent` (`backup_rules.xml`).
- **FR-011-8** Build targets SHALL be: `applicationId`/`namespace` `com.phoenix`, `minSdk 26`,
  `target`/`compileSdk 35`, Java/Kotlin JVM target 17, Compose enabled, and the project-wide
  Media3 `UnstableApi` opt-in compiler arg.
- **FR-011-9** The app SHALL depend on Media3 1.6.1 (exoplayer/session/common), Compose (BOM),
  Guava, NewPipeExtractor (JitPack, group-scoped) and OkHttp — with JitPack scoped to
  `com.github.TeamNewPipe.*` only.

## Key entities

- **Manifest** — permissions, service, Auto meta-data, application flags.
- **automotive_app_desc.xml / backup_rules.xml / data_extraction_rules.xml** — platform XML.
- **build.gradle.kts** — SDKs, JVM 17, dependencies, UnstableApi opt-in.

## Edge cases & rules

- The car slideshow's Images grant happens on the phone and may land *after* the service's
  first load — spec 008 FR-008-7 reloads the pool on the library revision bump to cover this.
- `local.properties` (SDK path) and the internal PRD `.docx` are git-ignored; the Gradle
  wrapper and a `TEMP=C:\jtmp` AV workaround are documented in `BUILD.md`.

## Non-goals

- No Play Store distribution posture (NewPipe/InnerTube use makes the YouTube feature
  non-distributable — see spec 006); no signing-config/release specifics here.
- No analytics, crash reporting, or telemetry.

## Open questions

_None — reverse-engineered from shipping code._
