# Phoenix — Specification Library

This directory is the source of truth for **what Phoenix does and why**. We use
**Spec-Driven Development (SDD)**: a change to behaviour starts as a change to a spec
here, and the spec is what we review, agree on, and then build against.

These initial specs were **reverse-engineered from the `main` branch** as it stood on
2026-07-28 (app `versionName 1.0`). They describe the system *as built*, so every
requirement below is already implemented unless its status says otherwise. From here on,
new work is specified first.

## Layout (Spec Kit convention)

```
specs/
├─ README.md          ← this file: process + conventions
├─ constitution.md    ← the non-negotiable principles every spec must respect
├─ INDEX.md           ← catalogue of all specs + their current versions
└─ NNN-feature-name/
   ├─ spec.md         ← the WHAT and WHY (required)
   ├─ plan.md         ← the HOW: technical approach for a change (added when we build)
   └─ tasks.md        ← the ordered work breakdown (added when we build)
```

Only `spec.md` exists for the reverse-engineered set. `plan.md` / `tasks.md` are created
per feature when we take on a change to it — that is the SDD loop:

1. **Specify** — edit or add a `spec.md`. Describe behaviour, not implementation.
2. **Plan** — write `plan.md`: the technical design that satisfies the spec.
3. **Break down** — write `tasks.md`: small, ordered, verifiable steps.
4. **Build & verify** — implement, checking each functional requirement.
5. **Reconcile** — if reality diverged from the spec, update the spec in the same change.

## Anatomy of a `spec.md`

Each spec opens with YAML frontmatter, then these sections:

- **Overview** — one paragraph: what this feature is and who it serves.
- **User scenarios** — Given / When / Then acceptance scenarios, phone and car.
- **Functional requirements** — testable `FR-###` statements. Each one should be
  verifiable by observation. These are the contract.
- **Key entities** — the domain objects and their meaningful fields.
- **Edge cases & rules** — the non-obvious behaviours that make the feature correct.
- **Non-goals** — what this feature deliberately does *not* do.
- **Open questions** — anything unresolved (empty for a shipped feature).

### Requirement IDs

`FR-<spec#>-<n>`, e.g. `FR-005-3` is the third functional requirement of spec 005.
IDs are **stable and append-only**: never renumber. If a requirement is dropped, mark it
`(removed in vX.Y)` rather than deleting it, so references never dangle.

## Versioning

Versioning is **git plus frontmatter** — no extra tooling.

- Every `spec.md` carries a `version` (semver) and `last-updated` in its frontmatter.
- Bump **patch** for clarifications/typos, **minor** for added or changed requirements,
  **major** for a behavioural redefinition.
- On every spec change, update that spec's `version` + `last-updated`, and update its row
  in [`INDEX.md`](INDEX.md).
- Git history is the audit trail; the commit that changes a spec should also carry the
  code (or note that it is spec-only).

## Status vocabulary

`Implemented` · `Draft` · `Proposed` · `In progress` · `Deprecated`

## Traceability

Every spec lists the **source files** that implement it in frontmatter. When you move or
rename code, update the affected specs' `source` lists in the same commit.
