# Spec Index

The catalogue of all Phoenix specifications and their current versions. Update the relevant
row whenever a spec's version or status changes (same commit as the spec change).

- **Process & conventions:** [README.md](README.md)
- **Governing principles:** [constitution.md](constitution.md) — v1.0.0

| # | Spec | Version | Status | Summary |
|---|------|---------|--------|---------|
| 001 | [Local Music Library](001-local-music-library/spec.md) | 1.0.0 | Implemented | MediaStore scan into leaf folders; sort; audiobook inclusion; SD preference; folder resolution; rescan |
| 002 | [Playback Engine & Media Session](002-playback-engine/spec.md) | 1.0.0 | Implemented | One Media3 session for phone + car; queue resolution; audio focus; phone controller bridge |
| 003 | [Gapless Crossfade](003-gapless-crossfade/spec.md) | 1.0.0 | Implemented | Equal-power dual-player crossfade, local songs only, configurable 0–12 s |
| 004 | [Android Auto Browse & Now-Playing](004-android-auto/spec.md) | 1.0.0 | Implemented | Playlists/YouTube/Radio tabs; list content-style; text tiles; shuffle-only now-playing; live refresh |
| 005 | [Internet Radio](005-internet-radio/spec.md) | 1.0.0 | Implemented | radio-browser.info client; seeded favorites; recents; reachability/vote badges; grouped sections |
| 006 | [YouTube Playlists & Search](006-youtube/spec.md) | 1.0.0 | Implemented | NewPipeExtractor; saved playlists; video search; just-in-time stream resolution |
| 007 | [Phone App Shell & Navigation](007-phone-app-shell/spec.md) | 1.0.0 | Implemented | MainActivity screen switcher; shared shortcut bar; mini now-playing bar; browse + sort + rescan |
| 008 | [Now-Playing & Artwork Slideshow](008-now-playing/spec.md) | 1.0.0 | Implemented | Phone full player + seek/heart; car gallery-photo slideshow; hand-rolled artwork loader |
| 009 | [Context-Scoped Search](009-search/spec.md) | 1.0.0 | Implemented | Corpus by context (songs vs stations), never mixed; phone + car scoping |
| 010 | [Shortcuts & Settings](010-shortcuts-and-settings/spec.md) | 1.0.0 | Implemented | 4 editable letter/icon folder shortcuts; crossfade setting; phone-authored, car-mirrored |
| 011 | [Platform Integration](011-platform-integration/spec.md) | 1.0.0 | Implemented | Permissions; manifest/service/Auto; FGS; cleartext; edge-to-edge; backup; build config |

## Maintenance

- Adding a spec: create `specs/NNN-name/spec.md`, add a row here, keep numbers append-only.
- Changing a spec: bump its frontmatter `version` + `last-updated`, update its row.
- Requirement IDs (`FR-NNN-n`) are append-only and never renumbered.
