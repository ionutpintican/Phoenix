# Graph Report - .  (2026-08-28)

## Corpus Check
- Corpus is ~27,529 words - fits in a single context window. You may not need a graph.

## Summary
- 255 nodes · 314 edges · 17 communities detected
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output
- Edge kinds: method: 141 · MODIFIES: 76 · contains: 66 · ON_BRANCH: 16 · PARENT_OF: 15


## Input Scope
- Requested: tracked
- Resolved: tracked (source: cli)
- Included files: 49 · Candidates: 100
- Excluded: 24379 untracked · 1 ignored · 0 sensitive · 0 missing committed
- Recommendation: Use --scope all or graphify.yaml inputs.corpus for a knowledge-base folder.

## Graph Freshness
- Built from Git commit: `861130b`
- Compare this hash to `git rev-parse HEAD` before trusting freshness-sensitive graph output.
## God Nodes (most connected - your core abstractions)
1. `PlaybackService` - 41 edges
2. `MusicLibrary` - 19 edges
3. `YouTubeBrowser` - 15 edges
4. `PlayerViewModel` - 14 edges
5. `LibraryCallback` - 10 edges
6. `RadioBrowser` - 8 edges
7. `YouTubePlaylists` - 8 edges
8. `RadioFavorites` - 6 edges
9. `Settings` - 6 edges
10. `PlayerListener` - 5 edges

## Surprising Connections (you probably didn't know these)
- `0f1f0d4 Fix controller queue resolution and smooth crossfade start` --ON_BRANCH--> `claude/graphify-ai-graph-setup-8lwvr0`  [EXTRACTED]
  git → git  _Bridges community 0 → community 2_
- `0f1f0d4 Fix controller queue resolution and smooth crossfade start` --PARENT_OF--> `310832d Seed default radio favorites on first launch`  [EXTRACTED]
  git → git  _Bridges community 0 → community 4_
- `310832d Seed default radio favorites on first launch` --ON_BRANCH--> `claude/graphify-ai-graph-setup-8lwvr0`  [EXTRACTED]
  git → git  _Bridges community 4 → community 2_

## Communities

### Community 2 - "Docs, Specs & App Config"
Cohesion: 0.10
Nodes (13): PhoenixApp, CuratedIcon, ShortcutIcons, DraftShortcut, 2b8eaa9 Refresh README to match current code + specs, 861130b Make specs/ an Obsidian vault; gitignore vault config, 9c25f19 Revert "Give the debug build a distinct package id and name", a2dccdb Settings: stage edits in a draft with an explicit Save (+5 more)

### Community 0 - "App Shell & Build History"
Cohesion: 0.07
Nodes (10): Screen, CarShortcut, Track, 0f1f0d4 Fix controller queue resolution and smooth crossfade start, 1175a7d Add crossfade, artwork display, and car/phone control fixes, 16777d1 Refactor car browse tree, add search screen, and refine crossfade, 362a60f Initial commit: Phoenix Android app, 522f44b Radio: reachability/vote badges, recently-played, robust playback (+2 more)

### Community 17 - "Activity Startup & Permissions"
Cohesion: 0.67
Nodes (1): MainActivity

### Community 14 - "Gallery Slideshow"
Cohesion: 0.50
Nodes (1): GallerySlideshow

### Community 3 - "Local Music Library"
Cohesion: 0.08
Nodes (4): MusicLibrary, SortMode, Folder, TrackVolumes

### Community 1 - "Playback Service & Crossfade"
Cohesion: 0.05
Nodes (1): PlaybackService

### Community 8 - "Media Session Library Callback"
Cohesion: 0.20
Nodes (1): LibraryCallback

### Community 6 - "Player ViewModel & Controls"
Cohesion: 0.14
Nodes (1): PlayerViewModel

### Community 12 - "Player Event Listener"
Cohesion: 0.40
Nodes (1): PlayerListener

### Community 4 - "Radio Browsing & Favorites"
Cohesion: 0.11
Nodes (4): RadioBrowser, RadioSection, RadioFavorites, 310832d Seed default radio favorites on first launch

### Community 11 - "Recently Played Radio"
Cohesion: 0.33
Nodes (1): RadioRecents

### Community 10 - "Radio Station Model"
Cohesion: 0.29
Nodes (1): RadioStation

### Community 9 - "Settings & Shortcuts"
Cohesion: 0.20
Nodes (2): ShortcutSetting, Settings

### Community 5 - "YouTube Browser & Streams"
Cohesion: 0.13
Nodes (1): YouTubeBrowser

### Community 16 - "YouTube Audio Downloader"
Cohesion: 0.50
Nodes (1): YouTubeDownloader

### Community 7 - "YouTube Playlists Store"
Cohesion: 0.17
Nodes (2): YouTubePlaylistRef, YouTubePlaylists

### Community 13 - "YouTube Track Model"
Cohesion: 0.40
Nodes (1): YouTubeTrack

## Knowledge Gaps
- **9 isolated node(s):** `Screen`, `SortMode`, `Folder`, `TrackVolumes`, `CarShortcut` (+4 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Activity Startup & Permissions`** (1 nodes): `MainActivity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Gallery Slideshow`** (1 nodes): `GallerySlideshow`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Playback Service & Crossfade`** (1 nodes): `PlaybackService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Media Session Library Callback`** (1 nodes): `LibraryCallback`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Player ViewModel & Controls`** (1 nodes): `PlayerViewModel`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Player Event Listener`** (1 nodes): `PlayerListener`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Recently Played Radio`** (1 nodes): `RadioRecents`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Radio Station Model`** (1 nodes): `RadioStation`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Settings & Shortcuts`** (2 nodes): `ShortcutSetting`, `Settings`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `YouTube Browser & Streams`** (1 nodes): `YouTubeBrowser`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `YouTube Audio Downloader`** (1 nodes): `YouTubeDownloader`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `YouTube Playlists Store`** (2 nodes): `YouTubePlaylistRef`, `YouTubePlaylists`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `YouTube Track Model`** (1 nodes): `YouTubeTrack`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PlaybackService` connect `Playback Service & Crossfade` to `App Shell & Build History`?**
  _High betweenness centrality (0.291) - this node is a cross-community bridge._
- **Why does `YouTubeBrowser` connect `YouTube Browser & Streams` to `App Shell & Build History`?**
  _High betweenness centrality (0.107) - this node is a cross-community bridge._
- **What connects `Screen`, `SortMode`, `Folder` to the rest of the system?**
  _9 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Docs, Specs & App Config` be split into smaller, more focused modules?**
  _Cohesion score 0.09686609686609686 - nodes in this community are weakly interconnected._
- **Should `App Shell & Build History` be split into smaller, more focused modules?**
  _Cohesion score 0.07373737373737374 - nodes in this community are weakly interconnected._
- **Should `Local Music Library` be split into smaller, more focused modules?**
  _Cohesion score 0.08333333333333333 - nodes in this community are weakly interconnected._
- **Should `Playback Service & Crossfade` be split into smaller, more focused modules?**
  _Cohesion score 0.04878048780487805 - nodes in this community are weakly interconnected._