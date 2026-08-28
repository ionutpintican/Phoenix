#!/usr/bin/env bash
#
# Provision the Graphify CLI and the Kotlin tree-sitter grammar into a local,
# git-ignored tooling directory, then print the absolute path to the graphify
# binary on stdout (all other output goes to stderr).
#
# Why the Kotlin shim: @sentropic/graphify ships prebuilt WASM grammars for the
# languages it declares as optional deps (Java, Python, ...), but NOT Kotlin.
# Graphify resolves a grammar by requiring `tree-sitter-<lang>/tree-sitter-<lang>.wasm`
# relative to its own install, so we install a web-tree-sitter-compatible Kotlin
# grammar (@tree-sitter-grammars/tree-sitter-kotlin) and expose its .wasm under a
# `tree-sitter-kotlin/` module name where graphify looks for it.
#
# Env overrides:
#   GRAPHIFY_VERSION  npm spec for the CLI      (default @sentropic/graphify@0.17.1)
#   KOTLIN_GRAMMAR    npm spec for the grammar  (default @tree-sitter-grammars/tree-sitter-kotlin@1.1.0)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOL_DIR="${GRAPHIFY_TOOL_DIR:-$ROOT/.graphify-tools}"
GRAPHIFY_VERSION="${GRAPHIFY_VERSION:-@sentropic/graphify@0.17.1}"
KOTLIN_GRAMMAR="${KOTLIN_GRAMMAR:-@tree-sitter-grammars/tree-sitter-kotlin@1.1.0}"

log() { echo "[graphify setup] $*" >&2; }

mkdir -p "$TOOL_DIR"
cd "$TOOL_DIR"
# A dot-prefixed dir name is an invalid npm package name, so write the manifest
# directly rather than relying on `npm init`.
[ -f package.json ] || printf '{"name":"graphify-tools","private":true,"version":"0.0.0"}\n' > package.json

log "installing $GRAPHIFY_VERSION and $KOTLIN_GRAMMAR ..."
npm install --no-audit --no-fund "$GRAPHIFY_VERSION" "$KOTLIN_GRAMMAR" >&2

# Expose the Kotlin grammar wasm under the module name graphify resolves.
SHIM="$TOOL_DIR/node_modules/tree-sitter-kotlin"
SRC_WASM="$TOOL_DIR/node_modules/@tree-sitter-grammars/tree-sitter-kotlin/tree-sitter-kotlin.wasm"
if [ ! -f "$SRC_WASM" ]; then
  log "ERROR: Kotlin grammar wasm not found at $SRC_WASM"
  exit 1
fi
mkdir -p "$SHIM"
cp "$SRC_WASM" "$SHIM/tree-sitter-kotlin.wasm"
printf '{"name":"tree-sitter-kotlin","version":"1.1.0","description":"wasm grammar shim provisioned by scripts/graphify/setup.sh"}\n' > "$SHIM/package.json"

BIN="$TOOL_DIR/node_modules/.bin/graphify"
if [ ! -x "$BIN" ]; then
  log "ERROR: graphify binary not found at $BIN"
  exit 1
fi
log "ready: $("$BIN" --version)"
echo "$BIN"
