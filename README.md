# Phoenix

An Android local-music + internet-radio player with first-class **Android Auto** support,
reconstructed from the development transcript (`Reconstruct Phoenix then compare with PRD
file.docx`). Originally prototyped under the name *Muzicuta*.

- **Playback engine:** Media3 / ExoPlayer 1.6.1 (`MediaLibraryService`)
- **Phone UI:** Jetpack Compose (Material 3), edge-to-edge
- **Min / target / compile SDK:** 26 / 35 / 35 · Kotlin 2.1.0 · AGP 8.9.1
- Plays MP3 (+ M4A/AAC, FLAC, OGG, WAV) from files Android's MediaStore has indexed.

See **[BUILD.md](BUILD.md)** to build the APK.

## What's implemented (traced to the transcript)

| Feature | Phone | Android Auto |
|---|---|---|
| Local folder/playlist browse from MediaStore | ✓ | ✓ (Playlists tab) |
| Now-playing (art, title/artist, transport, shuffle) | ✓ | ✓ (template) |
| Now-playing shortcut row (L/A/H/Au + Radio) | ✓ | ✓ (custom buttons) |
| Shared mini now-playing bar (nav-bar inset fix) | ✓ | — |
| Shuffle-all | ✓ | ✓ (browse entry) |
| Sort: date ↑/↓, name A–Z/Z–A (global, live-refreshes Auto) | ✓ (icon rail) | ✓ (sort tiles) |
| Letter shortcuts L / A / H / Au → Leni / Action / Hideout / Audiobooks (shuffle-play) | ✓ (bar) | ✓ (root + top of Playlists, as text tiles + now-playing buttons) |
| L/A/H/Au + Radio shortcut row (root tabs: **Playlists / Radio**; All Songs removed) | ✓ (bar) | ✓ |
| Audiobooks fix — include `IS_AUDIOBOOK` files, prefer SD `Music/` copy | ✓ | ✓ |
| Internet radio: top stations + search (radio-browser.info) | ✓ | ✓ (Radio tab) |
| Radio favorites (heart), favorites-first, persisted, live-synced | ✓ | ✓ (now-playing heart) |
| Context-scoped search (folder songs / radio only — never mixed) | ✓ | ✓ |
| Car now-playing artwork slideshow from gallery (random, ~12s) | — | ✓ |
| Future-proofing: predictive back, appCategory, backup rules, FGS types | ✓ | ✓ |

Platform truth from the transcript: on Android Auto no media app draws custom screens —
everything feeds Auto's standard template. So the phone's Compose screens are phone-only;
the car surfaces are the browse tree + now-playing template that `PlaybackService` serves.

## Source map

```
app/src/main/java/com/phoenix/
├─ MainActivity.kt              Compose host, permissions, edge-to-edge, folder jump
├─ PhoenixApp.kt                Application (init favorites)
├─ playback/
│  ├─ PlaybackService.kt        MediaLibraryService: tabs, sort/letter tiles, shuffle,
│  │                            radio tab, favorites-first, heart button, context search,
│  │                            gallery slideshow, sort→Auto live refresh
│  ├─ MusicLibrary.kt           MediaStore scan, folder tree, sort, folderIdByName,
│  │                            playableTracksFor, content-style hints
│  ├─ PlayerViewModel.kt        MediaController bridge for the phone UI
│  ├─ GallerySlideshow.kt       random gallery-photo supplier (car artwork)
│  └─ Track.kt
├─ radio/
│  ├─ RadioBrowser.kt           radio-browser.info client, search cache, queueContaining
│  ├─ RadioFavorites.kt         SharedPreferences JSON store, StateFlow
│  └─ RadioStation.kt
└─ ui/
   ├─ BrowseScreen.kt           folder/track lists, sort rail, rescan, shared NowPlayingBar
   ├─ BrowseNav.kt              LetterShortcutBar
   ├─ RadioScreen.kt            stations, search, per-row heart
   ├─ NowPlayingScreen.kt       full player + radio heart
   └─ theme/
```

## Build status — verified ✓

`./gradlew.bat assembleDebug` **succeeds** (Gradle 8.13, JDK 17, compileSdk 35) →
`app/build/outputs/apk/debug/app-debug.apk` (~18 MB, installable). Only benign warnings
remain (deprecated `CommandButton.Builder`/`setIconResId`; a no-op UnstableApi opt-in note).

Notes:
- **Car browse tiles are text-only** (L/A/H/Au, Radio, the four sort options, Shuffle all) so
  they render in the head unit's template font. The only bitmap car icons left are the
  now-playing letter buttons (`ic_letter_*`) and the radio heart (`ic_favorite*`).
- The Media3 `CommandButton` deprecation warnings are cosmetic; if you later want them gone,
  switch to `CommandButton.Builder(CommandButton.ICON_UNDEFINED).setCustomIconResId(res)`.

## PRD comparison

The document is titled "Reconstruct Phoenix **then compare with PRD file**." No PRD file is
present in this folder. Provide the PRD (or point me at it) and I'll produce a
requirement-by-requirement conformance table against this reconstruction.
