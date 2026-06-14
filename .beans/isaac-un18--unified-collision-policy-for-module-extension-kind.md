---
# isaac-un18
title: Unified collision policy for module extension kinds
status: completed
type: feature
priority: normal
created_at: 2026-05-20T20:44:42Z
updated_at: 2026-06-13T18:22:36Z
---

## Motivation

"What happens when two modules contribute the same name?" kept getting
answered differently per extension kind. After the foundation-extraction
audit (2026-06-13) the divergence is sharper — and now self-contradictory
for a single entity:

- **Activation berths** (tools, slash, cli, route, comm, provider,
  llm-api, hook) — last-loaded-wins. Slash logs `:slash/override`; the
  rest are silent.
- **Gather berths** (`:isaac.config/schema`, `:isaac.config/check`) —
  `:isaac.config/schema` deep-merges with a hard conflict-error
  (`schema-compose/merge-descriptors`); `:isaac.config/check` concats.

A configurable tool spans both: its factory lives in the
`:isaac.server/tools` activation berth (last-wins, silent) and its
config schema in the `:isaac.config/schema` gather berth (deep-merge,
conflict-error). So module B overriding A's `:my_tool` succeeds for the
factory but **hard-errors** on the schema if it differs — the same
override gesture allowed for behavior, forbidden for config.

The extraction makes multi-manifest (foundation + server + N modules)
the normal case and is exactly what grows the third-party-tool
ecosystem, so this wants settling before that ecosystem is large. It
does NOT block the cut (foundation/server contributions are disjoint).

## Decision inputs (supersede the original draft)

Two decisions postdate this bean's first draft and flip its original
"error by default" stance:

- **isaac-v6fl (scrapped):** fail-fast on duplicate tool/slash name was
  scrapped — *override is a feature*. A module declaring a built-in's
  name to replace its behavior is intended; the user's `:modules` order
  picks the winner.
- **Static config schema + deep-merge** (commit `ab480073`): config
  schema for tools/slash is declared statically and contributions
  deep-merge per config key — which introduced the gather-berth
  conflict-error half of the contradiction.

So the policy is NOT "error by default." It is last-wins, made audible.

## Resolution

**Last-loaded-wins, made audible. Same id from two modules → the later
one wins and logs `:<kind>/override` at `:warn`. Distinct ids coexist
(union). Applied identically to activation and gather berths. "Later" =
the user's `:modules` declaration (topological) order.**

This is the slash-command model (`isaac.slash.registry/register!`'s
`:slash/override`) generalized to every extension point.

### Two levels within a gather berth

The gather's deep-merge splits by level so it matches the activation
berths at the entity level while still protecting table structure:

- **Table-shell level** (`:type`, `:description`, `:key-spec`,
  `:value-spec`, `:entity-dir`, …) → **stays a conflict-error.** Two
  modules disagreeing on a table's *shape* is a "who owns this table"
  problem, not an override. The owning module declares the shell.
- **Entity level** (per-id entries inside the table's `:schema` —
  `:my_tool`, `:web_search`) → **last-wins-with-warning.** B's entry
  *wholesale replaces* A's — NOT a leaf-by-leaf blend (that would
  Frankenstein A's leftovers into B's override; the factory berth
  replaces the whole entry, so the schema must too).

### Canonical case, resolved

Two modules ship `:my_tool` (B declared after A in `:modules`):

- `:isaac.server/tools` factory → B's, `:tools/override` warned.
- `:isaac.config/schema {:tools {:schema {:schema {:my_tool …}}}}` →
  B's entry, same `:tools/override` warned, **no load error**.

One entity, one owner (B), one warning, both halves consistent. Two
modules adding *different* tools still just union.

## Work

- [ ] **Prerequisite — deterministic ordering.** `merge-contributions`
      (schema-compose) sorts contributions alphabetically by module id.
      Switch to module load (topological) order so "last wins" =
      "last-declared module wins" and matches the order the activation
      berths register in. Without this the factory and gather halves
      could pick different winners.
- [ ] Gather berth: change `merge-descriptors` entity-level conflict
      from error → last-wins-with-warning; keep the table-shell
      conflict-error.
- [ ] Activation berths: add the uniform `:<kind>/override` warning the
      non-slash kinds lack (tools, comms, providers, llm-api, hook,
      cli). Factor into one shared helper each kind calls.
- [ ] Slash stays as-is (already the model); confirm its
      `:slash/override` event name matches the chosen convention.
- [ ] Document the policy (foundation docs / ISAAC.md) so future
      extension-kind beans inherit it.

## Acceptance criteria

- One shared collision helper used by every activation berth.
- Gather berth: same-id contributions across modules → later wins,
  `:<kind>/override` warned, no error; differing table-shell → error.
- Contribution order is module load order, not alphabetical.
- A configurable tool overridden by a later module produces a single
  `:<kind>/override` warning across both its factory and config-schema
  halves, and loads clean.
- `bb spec` + `bb features` green in isaac and isaac-foundation.

## Notes

Original draft captured 2026-05-20 from the CLI-extension design talk
(isaac-vorl, since COMPLETED: it shipped :cli as a last-wins activation
berth and never blocked on this policy). The draft proposed
"error by default" — superseded here per isaac-v6fl. Resolution shaped
2026-06-13 during the foundation-extraction manifest audit.

## Summary of Changes (2026-06-13)

Implemented the resolution.

- **Gather (schema-compose/merge-descriptors)** — two-level merge: the table shell deep-merges and still errors on structural disagreement; the per-id entry map (`[:schema :schema]`) is owned per id — a later module's entry replaces an earlier one's wholesale, logged `:<config-key>/override`. The old hard conflict-error (which contradicted the factory berths' silent last-wins) is gone.
- **Ordering prerequisite** — `module-loader/topological-order` made public; both the gather (`contribution-entries`) and the activation (`process-manifest-berth!`) order contributions by it, so the two halves agree on the winner. `(name :isaac.server/tools)` = "tools" = `(name :tools)`, so both halves emit `:tools/override`.
- **Activation berths** — `process-manifest-berth!` warns `:<kind>/override` when a later module overrides a keyed (:map) berth entry by id. One uniform place covers tools/comm/provider/llm-api/hook/cli; slash keeps its existing registry-level `:slash/override` for non-berth paths.
- **Docs** — collision-policy anchor added to ISAAC.md.

Tests: schema_compose_spec (entity override no-error+logged, shell conflict errors, topological winner), loader_spec (activation override warning). `bb spec` 1962 / `bb features` 745, both green; isaac-foundation `bb ci` green after resync.

isaac commits: 892e160d (impl). foundation: f9a10b9 (resync). Unpushed.
