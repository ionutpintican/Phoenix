# Phoenix

An Android **local-music + internet-radio + YouTube** player with first-class **Android Auto**
support, reconstructed from the development transcript (`Reconstruct Phoenix then compare with
PRD file.docx`). Originally prototyped under the name *Muzicuta*.

- **Playback engine:** Media3 / ExoPlayer 1.6.1 (`MediaLibraryService`), one session shared by
  the phone UI and Android Auto
- **Phone UI:** Jetpack Compose (Material 3), edge-to-edge
- **Min / target / compile SDK:** 26 / 35 / 35 · Kotlin 2.1.0 · AGP 8.9.1 · JVM 17
- Plays MP3 (+ M4A/AAC, FLAC, OGG, WAV) from files Android's MediaStore has indexed, internet
  radio (radio-browser.info), and YouTube audio (NewPipeExtractor)

See **[BUILD.md](BUILD.md)** to build the APK, and **[specs/](specs/)** for the full behaviour
specification (this project uses Spec-Driven Development — see below).

## What's implemented

| Area | Phone | Android Auto |
|---|---|---|
| Local folder/playlist browse from MediaStore | ✓ | ✓ (Playlists tab) |
| Sort: date ↑/↓, name A–Z/Z–A (global, live-refreshes Auto) | ✓ (rail) | ✓ (reflected in lists) |
| Audiobooks — include `IS_AUDIOBOOK` files, prefer SD `Music/` copy, play folders-of-subfolders | ✓ | ✓ |
| Now-playing (art, title/artist, seek bar, transport, shuffle) | ✓ | ✓ (template) |
| Shared mini now-playing bar (nav-bar inset fix) | ✓ | — |
| Editable shortcut bar — 4 folder shortcuts, each a letter **or** icon, shuffle-play | ✓ (bar) | ✓ (text tiles + folder song lists) |
| Shuffle-all | ✓ | ✓ (browse entry) |
| **Gapless crossfade** between local songs (equal-power, configurable 0–12 s) | ✓ | ✓ |
| Internet radio: top stations + search (radio-browser.info) | ✓ | ✓ (Radio tab) |
| Radio favorites (heart, seeded defaults), favorites-first, persisted, live-synced | ✓ | ✓ (now-playing heart removed from car by design) |
| Radio recently-played + reachability/vote badges | ✓ | ✓ |
| **YouTube**: save playlists (link/id), video search, just-in-time stream resolution | ✓ (YouTube screen) | ✓ (YouTube tab) |
| Context-scoped search (songs *or* stations — never mixed) | ✓ | ✓ (one global box, scoped by view) |
| Phone Settings screen (shortcuts + crossfade) | ✓ | — (car reads the config) |
| Car now-playing artwork slideshow from gallery (random, ~12 s) | — | ✓ |
| Future-proofing: predictive back, appCategory, backup rules, FGS types | ✓ | ✓ |

Platform truth from the transcript: on Android Auto no media app draws custom screens —
everything feeds Auto's standard template. So the phone's Compose screens are phone-only; the
car surfaces are the browse tree + now-playing template that `PlaybackService` serves. The car
now-playing screen shows only the standard transport controls plus search and a single shuffle
toggle (no bespoke buttons — see the constitution).

## Source map

```
app/src/main/java/com/phoenix/
├─ MainActivity.kt              Compose host, permissions, edge-to-edge, screen switcher
├─ PhoenixApp.kt                Application
├─ playback/
│  ├─ PlaybackService.kt        MediaLibraryService: the one session; Auto browse tree (tabs,
│  │                            tiles, content-style), context search, shuffle button, crossfade,
│  │                            car artwork slideshow, live Auto refresh
│  ├─ MusicLibrary.kt           MediaStore scan, folder tree, sort, folderIdByName, search
│  ├─ PlayerViewModel.kt        MediaController bridge for the phone UI
│  ├─ GallerySlideshow.kt       random gallery-photo supplier (car artwork)
│  └─ Track.kt
├─ radio/
│  ├─ RadioBrowser.kt           radio-browser.info client, sections, search cache, queueContaining
│  ├─ RadioFavorites.kt         SharedPreferences JSON store (seeded defaults), StateFlow
│  ├─ RadioRecents.kt           SharedPreferences JSON store, most-recent-first, StateFlow
│  └─ RadioStation.kt           station model + subtitle/vote formatting
├─ youtube/
│  ├─ YouTubeBrowser.kt         NewPipeExtractor: playlists, search, JIT stream resolution, caches
│  ├─ YouTubeDownloader.kt      OkHttp-backed NewPipe Downloader (429 → ReCaptchaException)
│  ├─ YouTubePlaylists.kt       saved-playlist store (id+title), StateFlow
│  └─ YouTubeTrack.kt           track model + yt:// playback URI
├─ settings/
│  ├─ Settings.kt               shortcuts + crossfade store (StateFlow), phone-authored
│  └─ ShortcutIcons.kt          letter/curated-icon → drawable + Compose vector registry
└─ ui/
   ├─ BrowseScreen.kt           folder/track lists, sort rail, rescan, shared NowPlayingBar
   ├─ BrowseNav.kt              LetterShortcutBar (shortcuts + YouTube + Radio buttons)
   ├─ RadioScreen.kt            grouped stations (favorites/recents/all), per-row heart
   ├─ YouTubeScreen.kt          add playlist / search, saved-playlist + track lists
   ├─ NowPlayingScreen.kt       full player + seek bar + radio heart
   ├─ SearchScreen.kt           shared context-scoped search (songs vs stations)
   ├─ SettingsScreen.kt         edit shortcuts + crossfade (staged, Save to apply)
   ├─ Artwork.kt                hand-rolled artwork loader + LRU cache (no image library)
   └─ theme/
```

## Specifications (SDD)

Phoenix follows **Spec-Driven Development**: behaviour is specified and agreed before it's
built. The [`specs/`](specs/) library is the source of truth for *what the app does and why*.

- [`specs/README.md`](specs/README.md) — the process and spec conventions
- [`specs/constitution.md`](specs/constitution.md) — the non-negotiable principles
- [`specs/INDEX.md`](specs/INDEX.md) — the catalogue of all specs and their versions

The initial specs were reverse-engineered from `main`; new work adds/edits a spec (then
`plan.md` + `tasks.md` per feature) first. Specs are versioned via frontmatter + git.

## Build status — verified ✓

`./gradlew.bat assembleDebug` **succeeds** (Gradle 8.13, JDK 17, compileSdk 35) →
`app/build/outputs/apk/debug/app-debug.apk` (installable). Only benign warnings remain
(deprecated `CommandButton.Builder`/`setIconResId`; a no-op UnstableApi opt-in note).

Notes:
- **Car browse tiles are text-only** (shortcuts, Radio/YouTube tabs, Shuffle all) so they render
  in the head unit's template font. The only bitmap car icons are the now-playing shuffle glyph
  (Media3's predefined icon) and the radio heart (`ic_favorite*`); curated shortcut icons resolve
  to `ic_appearance_*` drawables.
- **YouTube** uses NewPipeExtractor against YouTube's private InnerTube API — fine for a personal
  device, **not distributable**, and inherently fragile (a YouTube-side change can break
  extraction until the extractor is bumped). See `YouTubeBrowser.kt` and spec 006.

## PRD comparison

The reconstruction document is titled "Reconstruct Phoenix **then compare with PRD file**." No
PRD file is present in this folder. Provide the PRD (or point me at it) and I'll produce a
requirement-by-requirement conformance table against the [`specs/`](specs/) library.
