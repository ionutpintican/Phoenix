# Graphify knowledge graph

Phoenix ships a [Graphify](https://github.com/rhanka/graphify) knowledge graph of
the codebase in [`.graphify/`](../.graphify). It turns the Kotlin source into a
queryable graph of classes, functions, call relationships and communities, so an
AI coding assistant (or you, from the terminal) can reason over the structure
instead of grepping raw files.

## What's in the graph

- **255 nodes / 314 edges** across **18 named communities** (e.g. *Playback
  Service & Crossfade*, *Local Music Library*, *YouTube Browser & Streams*,
  *Radio Browsing & Favorites*, *Settings & Shortcuts*).
- **God nodes** (most-connected abstractions): `PlaybackService`, `MusicLibrary`,
  `YouTubeBrowser`, `PlayerViewModel`, `LibraryCallback`.
- Per-symbol descriptions on 238/255 nodes.

Read [`.graphify/GRAPH_REPORT.md`](../.graphify/GRAPH_REPORT.md) for the
plain-language summary, or open the visual **Ontology Studio** by serving
`.graphify/studio/` with any static file server (or double-click
`.graphify/studio/studio.html`).

## Committed vs. local files

Tracked (commit-safe): `graph.json`, `GRAPH_REPORT.md`, `manifest.json`,
`scope.json`, and `studio/`. Everything else under `.graphify/` (cache, lifecycle
metadata, dated backups, instruction scratch dirs) is git-ignored via
[`.graphify/.gitignore`](../.graphify/.gitignore).

## Keeping it up to date

### Automatic (CI)

[`.github/workflows/graphify-graph.yml`](../.github/workflows/graphify-graph.yml)
rebuilds the graph on every push that touches source and commits the refreshed
graph back to the same branch (commit message tagged `[graphify skip]` so it
never loops). This needs the repository's **Actions → Workflow permissions** set
to **Read and write**.

The CI rebuild is deterministic and offline (AST only, no API key). It preserves
community names and re-applies existing descriptions to unchanged symbols; new
symbols are added without a description until the next assistant pass (below).

### Locally

```bash
./scripts/graphify/rebuild.sh
```

This provisions a pinned Graphify CLI and the Kotlin grammar into the
git-ignored `.graphify-tools/`, runs the structural rebuild, re-applies
descriptions, and re-exports the studio.

### Enriching descriptions / renaming communities

Structural rebuilds can't invent prose for brand-new symbols (no LLM in CI). To
(re)generate community names and node descriptions, run the `/graphify` skill in
a Claude Code session — Claude acts as the extraction backend, no API key
required:

```
/graphify . --update
```

Then commit the updated `.graphify/` artifacts.

## Querying from the terminal

Once `.graphify-tools/` is provisioned (via `rebuild.sh` or `scripts/graphify/setup.sh`):

```bash
GRAPHIFY="$(./scripts/graphify/setup.sh)"
"$GRAPHIFY" summary                              --graph .graphify/graph.json
"$GRAPHIFY" query "how does crossfade work?"     --graph .graphify/graph.json
"$GRAPHIFY" path  "PlayerViewModel" "PlaybackService" --graph .graphify/graph.json
"$GRAPHIFY" explain "MusicLibrary"               --graph .graphify/graph.json
```

## The Claude Code integration

`graphify install` also wrote:

- [`.claude/skills/graphify/`](../.claude/skills/graphify) — the `/graphify` skill.
- [`CLAUDE.md`](../CLAUDE.md) — guidance telling Claude to consult the graph for
  architecture questions.
- [`.claude/settings.json`](../.claude/settings.json) — `PreToolUse` hooks that
  remind Claude the graph exists (guarded; they no-op when `.graphify/graph.json`
  is absent and never require the CLI to be installed).

## About the Kotlin grammar

`@sentropic/graphify` ships WASM grammars for its declared languages (Java,
Python, …) but not Kotlin. `scripts/graphify/setup.sh` installs a
web-tree-sitter-compatible Kotlin grammar
(`@tree-sitter-grammars/tree-sitter-kotlin`) and exposes its `.wasm` under the
module name Graphify resolves, so Kotlin sources are parsed via AST like any
first-class language. Bump the pinned versions with the `GRAPHIFY_VERSION` /
`KOTLIN_GRAMMAR` env vars.
