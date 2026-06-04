---
# isaac-yb39
title: Berth contribution validation against :manifest :schema
status: completed
type: feature
priority: normal
created_at: 2026-06-04T11:08:43Z
updated_at: 2026-06-04T14:19:35Z
parent: isaac-brth
blocked_by:
    - isaac-htkp
---

Second child bean for the berth epic. With berths declared (isaac-htkp)
the foundation can now validate consumer contributions against the
matching berth's `:manifest :schema`.

## Behavior

- Top-level namespaced keys in a consumer manifest are treated as
  berth contributions.
- For each contribution, foundation looks up the berth declaration
  in the module-index (across all loaded modules).
- If the berth exists, validate the contribution value against the
  berth's `:manifest :schema` using apron.
- If the berth doesn't exist, return a config-load error naming the
  unknown berth.
- Successful contributions land in module-index at
  `/module-index/<consumer-id>/manifest/<berth-key>/...`.

Reserved (un-namespaced) top-level manifest keys are NOT
contributions: `:id`, `:version`, `:factory`, `:deps`, `:lifecycle`,
`:berths`. Foundation knows these and skips them when collecting
contributions.

## Feature

`features/module/berth_contributions.feature` — three `@wip` scenarios:

- happy: contribution to a known berth validates and lands in module-index.
- error: contribution missing a required field, reported with field path.
- error: contribution to a berth no installed module declares.

## Acceptance

- Remove `@wip` from `features/module/berth_contributions.feature`.
- `bb features features/module/berth_contributions.feature` passes.
- isaac-htkp's scenarios still pass; bean D adds contribution-validation
  behavior, doesn't change anything about how `:berths` itself is parsed
  or preserved.

## Out of scope

- `:dynamic-schema` resolution (later bean — when contributions'
  `:extra-schema` participates in the config berth's value-spec).
- `:deps` resolution / refusal of contributions from modules whose
  deps aren't installed (bean F).
- Module protocol dispatch and lifecycle (bean E).
- Validating contributions whose schemas reference the meta-schema
  via `c3kit.apron.schema/spec-schema` (deferred until isaac-2yqb
  lands AND a follow-up bean wires the meta-schema into contribution
  validation).

## Dependencies

- Blocked by isaac-htkp (need the `:berths` data shape parsed and
  preserved before contributions can be matched against it).
- NOT blocked by isaac-c2g5 or isaac-2yqb — scenarios use only
  built-in apron types (`:string`, `:keyword`, `:map`).

## Notes for the worker

- The contribution-key path expression in scenario 1's assertion uses
  JSON-Pointer escaping (`marigold.bridge~1comm` for the `/` in the
  namespaced berth key). Same plumbing concern as isaac-htkp; same
  one-line fix if the path splitter doesn't honor `~1`.
- Don't validate contributions inside provider modules against their
  own berths. Provider berth declarations live in `:berths`, not as
  top-level namespaced keys. The contribution-matching pass operates
  on a manifest's top-level keys minus the reserved set.

## Summary of Changes

`isaac.module.manifest`:

- `read-manifest` now tolerates namespaced top-level keys (no longer throws `unknown extension kind` for them) — they're potential berth contributions.
- After `lexicon/conform!` strips them (the schema doesn't name them), namespaced top-level keys not in `known-keys` are merged back so the post-discovery validator can find them.

`isaac.module.loader`:

- New post-discovery pass `validate-contributions!` walks each manifest's namespaced top-level keys (skipping `known-meta-keys` ∪ `known-extend-kinds`), looks the berth up across all loaded modules, and validates the contribution value against the berth's `:manifest :schema` via `isaac.schema.lexicon`.
- Error shapes match the bean: unknown berth → `module-index["<consumer>"][<berth-key>]` / "berth not declared by any installed module"; validation errors → `module-index["<consumer>"].<berth-key>[<contribution-outer-key>].<field>` / "...".
- A scoped `berth-lexicon` rebinds apron's `:present?` validation to surface "must be present" (the ISAAC wording for berth missing-field errors; apron's default is "is required").

bb features features/module/berth_contributions.feature 3/0; bb spec 1811/0; bb features 736/0.
