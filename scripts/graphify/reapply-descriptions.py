#!/usr/bin/env python3
"""Re-apply curated node descriptions (and non-generic community names) from a
previous graph.json onto a freshly rebuilt one, matching by node id.

A structural rebuild (`graphify hook-rebuild`) re-runs AST extraction and drops
per-node `description` fields. Community names survive when the topology is
stable, but descriptions do not. This helper carries the human/assistant-authored
descriptions forward for nodes that still exist, so an offline (no-LLM) rebuild
in CI or a git hook keeps the enrichment instead of wiping it.

New nodes (added by new functionality) are intentionally left without a
description — run the `/graphify` describe pass in a Claude Code session to fill
those in.

Usage: reapply-descriptions.py <old-graph.json> <new-graph.json>
"""
import json
import sys


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    old_path, new_path = sys.argv[1], sys.argv[2]
    with open(old_path, encoding="utf-8") as fh:
        old = json.load(fh)
    with open(new_path, encoding="utf-8") as fh:
        new = json.load(fh)

    old_desc = {n["id"]: n["description"] for n in old.get("nodes", []) if n.get("description")}
    old_name = {
        n["id"]: n["community_name"]
        for n in old.get("nodes", [])
        if n.get("community_name") and not str(n["community_name"]).startswith("Community ")
    }

    restored_desc = 0
    restored_name = 0
    for node in new.get("nodes", []):
        nid = node.get("id")
        if not node.get("description") and nid in old_desc:
            node["description"] = old_desc[nid]
            restored_desc += 1
        current = str(node.get("community_name") or "")
        if (not current or current.startswith("Community ")) and nid in old_name:
            node["community_name"] = old_name[nid]
            restored_name += 1

    with open(new_path, "w", encoding="utf-8") as fh:
        json.dump(new, fh, ensure_ascii=False, indent=0)

    print(
        f"[graphify] re-applied {restored_desc} description(s) and "
        f"{restored_name} community name(s) from {old_path}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
