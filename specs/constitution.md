# Phoenix Constitution

The principles every spec and every change must respect. When a spec conflicts with the
constitution, the constitution wins — or the constitution is amended first, deliberately.
Derived from how the app is actually built on `main` as of 2026-07-28.

## P1 — One playback engine, two surfaces

There is exactly one audio pipeline: the Media3 `MediaLibrarySession` inside
`PlaybackService`. The phone Compose UI is a **`MediaController` client** of that same
session; Android Auto renders the same session's browse tree and now-playing template.
Never introduce a second player that owns the queue or audio focus. (The crossfade helper
engine is the sole exception and it owns neither — see spec 003.)

## P2 — The car draws no custom screens

On Android Auto no media app paints its own UI; everything is fed into Auto's standard
template. All car behaviour is expressed as browse-tree nodes, content-style hints,
media metadata, and session/custom commands built in `PlaybackService`. A spec must never
assume a bespoke car screen.

## P3 — State is shared and reactive

Cross-surface state (radio favorites, radio recents, YouTube playlists, user settings,
sort mode, library revision) lives in a single holder exposed as a `StateFlow`. Both the
phone UI and the service observe it, so a change in one place is reflected everywhere live.
Persist the **full object**, not just an id, when the object must survive offline or a
process trim (e.g. a favorite station stays playable when it has left the top list).

## P4 — MediaStore is the only library source

Local audio is whatever Android's MediaStore has indexed — the app does no filesystem
walking of its own. A `.nomedia` file legitimately hides content; a manual **Rescan**
is the remedy after copying files. Respect the MediaStore contract (relative paths,
volumes, audiobook flags).

## P5 — Resolve remote streams just-in-time, never persist them

Radio and YouTube stream URLs are volatile (YouTube's are short-lived and IP-bound).
Persist stable identifiers (station uuid + stream URL, YouTube videoId) and resolve the
actual playable URL at play time via the `ResolvingDataSource`, so a queued or restored
item stays playable long after any transient URL would have expired.

## P6 — Degrade gracefully, never crash on absent data

Missing permission, an empty MediaStore, a failed network fetch, an unresolvable media id,
a private YouTube playlist — each must yield an empty list, a placeholder, or a logged
player error, never a crash. Unknown browse ids resolve to a benign "Unavailable" node.

## P7 — Conventional now-playing

The car now-playing screen shows only standard transport controls plus search and a single
shuffle toggle. No bespoke buttons crowd it. (Historical letter/heart/radio buttons were
deliberately removed — don't reintroduce them there.)

## P8 — Reverse-engineering discipline

When reconstructing or extending this app, build from these specs and the source of truth
they cite — not from neighbouring modified copies of the project. Keep the specs and the
README/BUILD docs in step with the code; a stale doc is a defect.

## Amending this document

Changes are versioned like any spec (frontmatter below). A principle change is a **major**
bump and should explain what motivated it.

---
version: 1.0.0
last-updated: 2026-07-28
