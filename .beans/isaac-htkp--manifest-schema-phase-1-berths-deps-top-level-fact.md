---
# isaac-htkp
title: 'Manifest schema phase 1: :berths, :deps, top-level :factory (read-through)'
status: todo
type: feature
priority: normal
created_at: 2026-06-04T10:47:52Z
updated_at: 2026-06-04T10:52:25Z
parent: isaac-brth
blocked_by:
    - isaac-c2g5
---

First child bean for the berth epic (isaac-brth). Phase 1 is the
read-through data layer: module manifests gain three additive
top-level fields, the schema accepts them, the loader preserves them
in module-index, and the obvious shape mistakes surface as
config-load errors. No berth BEHAVIOR runs in this bean — that's
later beans.

## New manifest fields

- `:berths` — map from namespaced berth id to a berth declaration.
  Each value carries `:description` (required), `:manifest` (optional
  — the affordance for module contributions), and `:config` (optional
  — the affordance for user-config slots). Berth keys MUST be
  namespaced keywords.

- `:deps` — map mirroring `:modules` shape: module-id to a
  coordinate map. Same coordinate shapes the user uses in
  `isaac.edn :modules` (`:local/root`, `:git/url`+`:git/sha`, etc.).
  Phase 1 just stores; resolution lands in a later bean.

- `:factory` — symbol naming a fn that returns a value satisfying
  the foundation's Module protocol. Lifecycle hooks
  (`on-startup`, `on-shutdown`) live on the returned instance.
  Phase 1 just stores the symbol; the Module protocol and lifecycle
  invocation land in a later bean.

## Feature

`features/module/berths.feature` — 7 `@wip` scenarios:

- happy path: provider manifest's `:berths` preserved in module-index
- happy path: consumer manifest's `:deps` preserved in module-index
- berth declaration missing `:description` ⇒ config-load error
- berth keyed by un-namespaced keyword ⇒ config-load error
- `:deps` entry whose coordinate is not a map ⇒ config-load error
- top-level `:factory` that is not a symbol ⇒ config-load error
- `:berths` that is not a map ⇒ config-load error

## Dependencies

- Blocked by the apron `:type :symbol` work (next bean — prove it as
  a type lexicon extension before contributing back to apron). The
  \"top-level :factory must be a symbol\" scenario needs it.

## Acceptance

- Remove `@wip` from `features/module/berths.feature`.
- `bb features features/module/berths.feature` passes.
- Existing module/discovery and manifest scenarios still pass; no
  behavior change to today's loader semantics for manifests that
  don't use the new fields.

## Out of scope (deliberately)

- Contribution validation against berth `:manifest :schema` (later bean).
- `:dynamic-schema` directive and apron schema-merging (later bean).
- Module protocol shape and lifecycle ordering (later bean).
- `:deps` resolution / refusal of missing providers (later bean).
- `[:registered-in? :berth-id]` validation primitive (later bean).
- `:cli` re-declared as a berth (phase 4 of the epic).

## Notes for the worker

- Module fixtures in the scenarios deliberately live under `/tmp/modules/`,
  NOT under `.isaac/modules/`. There is no auto-discovery in this bean
  or anywhere else — modules are declared explicitly in `isaac.edn`
  with a coordinate. Don't add a directory scan.
- The path expression `marigold.bridge~1comm` in scenario 1's
  assertion table uses JSON-Pointer escaping (RFC 6901) for the `/`
  in the namespaced berth id. If the existing test step's path
  splitter doesn't honor `~1`, teach it to (one place to change).
