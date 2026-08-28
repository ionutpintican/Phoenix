#!/usr/bin/env bash
#
# Rebuild the Graphify knowledge graph in .graphify/ from the current code,
# preserving curated community names and node descriptions.
#
# This is a code-only (AST) rebuild: deterministic, offline, no API key. It is
# what CI (.github/workflows/graphify-graph.yml) and a local git hook run.
#
# Steps:
#   1. provision the pinned graphify CLI + Kotlin grammar (scripts/graphify/setup.sh)
#   2. snapshot the existing graph.json (to carry descriptions forward)
#   3. structural rebuild (graphify hook-rebuild)
#   4. re-apply descriptions/community names for surviving nodes
#   5. re-export the static Ontology Studio so it reflects the descriptions
#
# Env overrides:
#   GRAPHIFY_SCOPE   input scope: auto|committed|tracked|all (default tracked)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
SCOPE="${GRAPHIFY_SCOPE:-tracked}"

GRAPHIFY_BIN="$("$ROOT/scripts/graphify/setup.sh")"

SNAP=""
if [ -f .graphify/graph.json ]; then
  SNAP="$(mktemp)"
  cp .graphify/graph.json "$SNAP"
fi

echo "[graphify rebuild] structural rebuild (scope=$SCOPE) ..." >&2
"$GRAPHIFY_BIN" hook-rebuild --scope "$SCOPE"

if [ -n "$SNAP" ] && [ -s "$SNAP" ] && [ -f .graphify/graph.json ]; then
  python3 "$ROOT/scripts/graphify/reapply-descriptions.py" "$SNAP" .graphify/graph.json
  rm -f "$SNAP"
fi

echo "[graphify rebuild] exporting static studio ..." >&2
"$GRAPHIFY_BIN" studio export .graphify/studio >&2 2>&1 || true

echo "[graphify rebuild] done." >&2
