# Graph Report - .  (2026-08-28)

## Corpus Check
- 60 files · ~62,341 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 258 nodes · 318 edges · 18 communities detected
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output
- Edge kinds: method: 141 · MODIFIES: 77 · contains: 67 · ON_BRANCH: 17 · PARENT_OF: 16


## Input Scope
- Requested: tracked
- Resolved: tracked (source: cli)
- Included files: 60 · Candidates: 125
- Excluded: 0 untracked · 23310 ignored · 0 sensitive · 0 missing committed
- Recommendation: Use --scope all or graphify.yaml inputs.corpus for a knowledge-base folder.

## Graph Freshness
- Built from Git commit: `fe8eb34`
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
  git → git  _Bridges community 0 → community 7_
- `0f1f0d4 Fix controller queue resolution and smooth crossfade start` --PARENT_OF--> `310832d Seed default radio favorites on first launch`  [EXTRACTED]
  git → git  _Bridges community 0 → community 3_
- `310832d Seed default radio favorites on first launch` --ON_BRANCH--> `claude/graphify-ai-graph-setup-8lwvr0`  [EXTRACTED]
  git → git  _Bridges community 3 → community 7_
- `310832d Seed default radio favorites on first launch` --PARENT_OF--> `fdb78c3 Add Settings screen for editable shortcuts and crossfade`  [EXTRACTED]
  git → git  _Bridges community 3 → community 4_
- `fdb78c3 Add Settings screen for editable shortcuts and crossfade` --ON_BRANCH--> `claude/graphify-ai-graph-setup-8lwvr0`  [EXTRACTED]
  git → git  _Bridges community 4 → community 7_

## Communities

### Community 0 - "Community 0"
Cohesion: 0.07
Nodes (10): 0f1f0d4 Fix controller queue resolution and smooth crossfade start, 1175a7d Add crossfade, artwork display, and car/phone control fixes, 16777d1 Refactor car browse tree, add search screen, and refine crossfade, 362a60f Initial commit: Phoenix Android app, 522f44b Radio: reachability/vote badges, recently-played, robust playback, 775bb21 YouTube: search, relocate icon, bump extractor, f0d0e8d Add YouTube Music playlist playback, Screen (+2 more)

### Community 1 - "Community 1"
Cohesion: 0.05
Nodes (1): PlaybackService

### Community 2 - "Community 2"
Cohesion: 0.08
Nodes (4): Folder, MusicLibrary, SortMode, TrackVolumes

### Community 3 - "Community 3"
Cohesion: 0.11
Nodes (4): 310832d Seed default radio favorites on first launch, RadioBrowser, RadioSection, RadioFavorites

### Community 4 - "Community 4"
Cohesion: 0.11
Nodes (5): fdb78c3 Add Settings screen for editable shortcuts and crossfade, PhoenixApp, CuratedIcon, ShortcutIcons, DraftShortcut

### Community 5 - "Community 5"
Cohesion: 0.13
Nodes (1): YouTubeBrowser

### Community 6 - "Community 6"
Cohesion: 0.14
Nodes (1): PlayerViewModel

### Community 7 - "Community 7"
Cohesion: 0.27
Nodes (9): claude/graphify-ai-graph-setup-8lwvr0, 2b8eaa9 Refresh README to match current code + specs, 861130b Make specs/ an Obsidian vault; gitignore vault config, 9c25f19 Revert "Give the debug build a distinct package id and name", a2dccdb Settings: stage edits in a draft with an explicit Save, ce8d272 Give the debug build a distinct package id and name, e43f8d5 Car now-playing: only standard controls + shuffle, ea1d5d2 Add reverse-engineered spec library (SDD) (+1 more)

### Community 8 - "Community 8"
Cohesion: 0.17
Nodes (2): YouTubePlaylistRef, YouTubePlaylists

### Community 9 - "Community 9"
Cohesion: 0.20
Nodes (1): LibraryCallback

### Community 10 - "Community 10"
Cohesion: 0.20
Nodes (2): Settings, ShortcutSetting

### Community 11 - "Community 11"
Cohesion: 0.29
Nodes (1): RadioStation

### Community 12 - "Community 12"
Cohesion: 0.33
Nodes (1): RadioRecents

### Community 13 - "Community 13"
Cohesion: 0.40
Nodes (1): PlayerListener

### Community 14 - "Community 14"
Cohesion: 0.40
Nodes (1): YouTubeTrack

### Community 15 - "Community 15"
Cohesion: 0.50
Nodes (1): GallerySlideshow

### Community 17 - "Community 17"
Cohesion: 0.50
Nodes (1): YouTubeDownloader

### Community 18 - "Community 18"
Cohesion: 0.67
Nodes (1): MainActivity

## Knowledge Gaps
- **9 isolated node(s):** `Screen`, `SortMode`, `Folder`, `TrackVolumes`, `CarShortcut` (+4 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 1`** (1 nodes): `PlaybackService`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 5`** (1 nodes): `YouTubeBrowser`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 6`** (1 nodes): `PlayerViewModel`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 8`** (2 nodes): `YouTubePlaylistRef`, `YouTubePlaylists`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 9`** (1 nodes): `LibraryCallback`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 10`** (2 nodes): `Settings`, `ShortcutSetting`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 11`** (1 nodes): `RadioStation`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 12`** (1 nodes): `RadioRecents`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 13`** (1 nodes): `PlayerListener`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 14`** (1 nodes): `YouTubeTrack`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 15`** (1 nodes): `GallerySlideshow`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 17`** (1 nodes): `YouTubeDownloader`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 18`** (1 nodes): `MainActivity`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PlaybackService` connect `Community 1` to `Community 0`?**
  _High betweenness centrality (0.288) - this node is a cross-community bridge._
- **Why does `YouTubeBrowser` connect `Community 5` to `Community 0`?**
  _High betweenness centrality (0.106) - this node is a cross-community bridge._
- **What connects `Screen`, `SortMode`, `Folder` to the rest of the system?**
  _9 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.07373737373737374 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.04878048780487805 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.08333333333333333 - nodes in this community are weakly interconnected._
- **Should `Community 3` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._