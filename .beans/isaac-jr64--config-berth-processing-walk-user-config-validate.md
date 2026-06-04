---
# isaac-jr64
title: 'Config berth processing: walk user config, validate, build Nodes, register in nexus'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-04T14:34:39Z
updated_at: 2026-06-04T19:56:04Z
parent: isaac-brth
blocked_by:
    - isaac-htkp
    - isaac-yb39
    - isaac-fzsz
    - isaac-f77b
    - isaac-2ecl
    - isaac-ma0j
    - isaac-c2g5
---

The integration crown of the berth wave. For each berth that
declares a `:config` shape, the foundation walks the user's config
at `:config :path`, validates each slot against the composed schema
(base + `:dynamic-schema`-gathered fields), calls the schema-level
`:factory` once per slot with `(path, slice)`, and installs the
returned Node in the nexus at the same path.

Per-type dispatch inside the factory happens via a multimethod
registered by the contributing module's namespace, loaded as a side
effect of `requiring-resolve`-ing the module's top-level `:factory`
(per isaac-f77b's lifecycle).

## Behavior

1. Module index built (isaac-htkp + isaac-fzsz).
2. Contributions validated (isaac-yb39).
3. Module factories called; Module instances live (isaac-f77b).
4. For each berth in the module-index with a `:config`:
   a. Read user config at `:config :path` (e.g. `[:comms]`).
   b. For each key/value in that slice, walk the berth's
      `:config :schema` looking for `:factory` (Node-level marker).
   c. Compose the schema at the Node level: merge gathered
      `:dynamic-schema` fields from contributions (isaac-2ecl) into
      `:schema`.
   d. Validate the slot against the composed schema. Validation
      uses `:registered-in?` for the discriminator (isaac-ma0j).
   e. Call `(factory path slice)`. Factory is the schema-level one
      (e.g. `marigold.bridge.comm/create-comm-node!`); it dispatches
      via multimethod on the slot's `:type` into the contributing
      module's defmethod.
   f. Install the returned Node in the nexus at `path`.

## Fixture modules (live under `spec/marigold/`)

Worker's first task is creating two fixture modules with real
deps.edn + src + manifest layouts:

\`\`\`
spec/marigold/bridge/
  deps.edn                           ; {:paths ["src" "resources"]}
  resources/isaac-manifest.edn       ; declares :marigold.bridge/comm berth
  src/marigold/bridge.clj            ; create-module factory
  src/marigold/bridge/comm.clj       ; defmulti create-comm-node!

spec/marigold/longwave/
  deps.edn
  resources/isaac-manifest.edn       ; contributes :longwave with :helm/freq
  src/marigold/longwave.clj          ; create-module + defmethod for :longwave
\`\`\`

The fixtures are STABLE across every scenario in this bean's file
(and reusable by any downstream bean that exercises the same berth
machinery). Tests vary only `isaac.edn`.

The longwave defmethod returns a Node value whose type is `:longwave`
(however the Node protocol/data shape ends up being defined per
isaac-f77b's open question).

## Feature

`features/module/config_berth_processing.feature` — three `@wip`
scenarios:

- happy: user-configured slot becomes a Node and lands in the nexus.
- error: user `:type` not in any contribution, caught by
  `:registered-in?`.
- error: user slot missing a field required by gathered
  `:extra-schema`, caught by `:dynamic-schema`-merged validation.

## Acceptance

- Remove `@wip` from `features/module/config_berth_processing.feature`.
- `bb features features/module/config_berth_processing.feature` passes.
- All prior berth-wave scenarios still pass (htkp, yb39, fzsz, F).
- A new step `Then the nexus has a :<type> node at <path>` is
  implemented and reusable.

## Out of scope

- Manifest-only berth processing (separate bean — `:route` and friends).
- Reconfigurable / on-config-change. Phase-2 concern.
- Cli-as-berth (separate bean, phase 4 of the epic).
- Performance: schema composition runs on every load; caching is
  later.

## Dependencies

Blocked by every other bean in the wave:

- isaac-htkp (manifest shape)
- isaac-yb39 (contribution validation)
- isaac-fzsz (deps resolution — so longwave's `:deps` on bridge
  resolves locally)
- isaac-f77b (Module protocol + lifecycle — to call factories)
- isaac-2ecl (`:dynamic-schema` merging)
- isaac-ma0j (`[:registered-in?]` validator)
- isaac-c2g5 (lexicon — so manifests' `:type :symbol` declarations
  work in fixture schemas)

## Notes for the worker

- Order matters in step (4). Compose first, validate second, call
  factory third, register fourth. If any step errors, the whole load
  aborts (matching isaac-f77b's startup-failure policy).
- A failed factory call should NOT silently skip the slot; it
  aborts the load. Already-registered Nodes get a `on-shutdown`
  best-effort.
- The schema-level `:factory` symbol is `requiring-resolve`-d at
  config-processing time. Modules' top-level `:factory` runs first
  (isaac-f77b), which triggers their namespace loading, which fires
  their `defmethod` calls. By the time `create-comm-node!` is
  invoked, every contributor's defmethod is registered.
- Step `Then the nexus has a :<type> node at <path>` should also
  fail loudly if the Node value doesn't satisfy whatever Node
  contract we settle on. Right now it's just "a value exists at the
  nexus path and reports its type as X" — fine for v1.
