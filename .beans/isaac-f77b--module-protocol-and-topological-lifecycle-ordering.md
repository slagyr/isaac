---
# isaac-f77b
title: Module protocol and topological lifecycle ordering
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-04T11:10:29Z
updated_at: 2026-06-04T14:41:04Z
parent: isaac-brth
---

Defines the foundation's `Module` protocol and the lifecycle the
foundation runs around it. Every loaded module's top-level `:factory`
returns a `Module` instance. Foundation calls `on-startup` in
topological order across `:deps`, `on-shutdown` reverse.

## Protocol

`isaac.module/Module`, with two methods:

- `on-startup [this]` — called once per process after the config
  load and all contribution validation has passed.
- `on-shutdown [this]` — called on graceful shutdown.

Both are optional via `extend-protocol`-style partial implementations
or default no-op impls — modules implement what they need. A
contribution-only module (e.g., a slash-command pack) may implement
neither; the foundation just gets a Module instance back from the
factory and never calls into it.

Deliberately NOT in this bean:

- `on-config-change` — that's the Reconfigurable Node concern, its
  own later bean.
- Per-berth contribution methods (no `make-comm-node`-style methods).
  Per-type dispatch happens via multimethods in the config-berth's
  `:factory`, not via the Module protocol.

## Lifecycle

1. Config validated; module-index complete; all contributions matched
   and validated.
2. For each loaded module: `requiring-resolve` its top-level `:factory`
   symbol. Call `(factory)`; require the return value to satisfy
   `Module`.
3. Compute a topological order from `:deps`: provider modules' nodes
   come before consumer modules' nodes.
4. Call `on-startup` on each Module in topological order.
5. On graceful shutdown: reverse order, `on-shutdown`.

## Failure policies

- `:factory` symbol fails to `requiring-resolve` ⇒ abort load with
  a clear error naming the module and the missing symbol.
- `(factory)` throws ⇒ abort load with a clear error.
- Factory returns a value that doesn't satisfy `Module` ⇒ abort load
  with a clear error.
- `on-startup` throws ⇒ abort load. Optionally invoke `on-shutdown`
  on already-started modules in reverse order to give them a chance
  to release resources. (Decide during implementation; default to
  best-effort shutdown of started modules.)

These choices preserve the invariant: if the foundation returns from
load, every module is up.

## Acceptance

No new Gherkin feature file. Speclj at
`spec/isaac/module/lifecycle_spec.clj` (or similar):

- Defining a Module via `defrecord` / `extend-protocol` works; both
  methods are optional.
- A two-module fixture (`marigold.bridge` provider, `marigold.longwave`
  consumer with `:deps {:marigold.bridge {…}}`) loads and starts in
  topological order. Use an atom to record the call order; assert
  `[:marigold.bridge :marigold.longwave]`.
- Shutdown reverses order.
- A module whose `:factory` symbol can't resolve aborts the load.
- A factory that throws aborts the load.
- A factory returning a non-Module value aborts the load.
- An `on-startup` that throws aborts the load AND invokes
  `on-shutdown` on previously-started modules (best-effort).

Integration validation will land in later beans (config-berth
processing, manifest-only-berth processing) where lifecycle is
exercised against real-shaped modules.

## Out of scope

- `on-config-change` / Reconfigurable Nodes.
- Hot-reloading modules at runtime.
- Module loading from network coordinates (deps.edn already handles
  fetch).
- Per-berth dispatch methods on the protocol.

## Dependencies

- Blocked by isaac-htkp (need `:factory` field on manifest and
  `:berths` for topological computation; both land there).

## Notes for the worker

- Topological order across `:deps`: providers' nodes ALWAYS before
  consumers'. If two modules are mutually dependent (cycle), abort
  with a clear error naming the cycle. Cycles among Module lifecycles
  are not supported.
- `requiring-resolve` is the right tool — it loads the namespace on
  first reference. Modules are loaded lazily, only when their factory
  is invoked.
- The startup-failure rollback (best-effort `on-shutdown` on already-
  started modules) is worth doing even on first impl. Modules holding
  external handles (websockets, file handles, processes) need a chance
  to release them; otherwise startup failure leaks resources.
