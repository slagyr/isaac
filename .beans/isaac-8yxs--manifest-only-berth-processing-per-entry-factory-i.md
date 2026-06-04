---
# isaac-8yxs
title: 'Manifest-only berth processing: per-entry :factory invocation'
status: completed
type: feature
priority: normal
created_at: 2026-06-04T14:40:17Z
updated_at: 2026-06-04T19:26:13Z
parent: isaac-brth
blocked_by:
    - isaac-htkp
    - isaac-yb39
    - isaac-fzsz
    - isaac-f77b
    - isaac-c2g5
---

For berths without a `:config` shape, the foundation collects
contributions from module manifests and calls the entry-level
`:factory` declared by the berth's `:manifest :schema` once per
contribution. The factory does whatever the berth needs — typically
register the entry in the nexus at a known path.

Routes are the canonical example. Each module's manifest contributes
route entries; the foundation hands each to a registration factory;
the entries land in the nexus where the HTTP router reads from.

## Behavior

1. After contribution validation (isaac-yb39), foundation has the
   set of validated contributions per berth.
2. For each manifest-only berth (no `:config`):
   a. Walk the berth's `:manifest :schema`. If the entry-level shape
      declares a `:factory`, that's the registration point.
   b. For each contribution entry, call `(factory entry)`.
   c. The factory returns whatever the berth wants stored. Foundation
      installs it at a berth-conventional nexus path (default
      `[<berth-id>]`).
3. A berth WITHOUT an entry-level `:factory` falls through to the
   foundation default: merge all contributions into a map at
   `[<berth-id>]`. (Useful for purely declarative berths like routes
   if no registration logic is needed.)

## Fixture

Reuses the marigold.bridge / marigold.longwave fixtures from
isaac-jr64:

- `spec/marigold/bridge/resources/isaac-manifest.edn` declares
  `:marigold.bridge/signal-route` as a manifest-only berth whose
  entry-level schema specifies
  `:factory marigold.bridge.signal/register-route!`.
- `spec/marigold/bridge/src/marigold/bridge/signal.clj` defines
  `register-route!` (installs the entry into the nexus).
- `spec/marigold/longwave/resources/isaac-manifest.edn` adds a route
  entry: `[{:method :get :path "/longwave/ping" :handler marigold.longwave/ping-handler}]`.

## Feature

`features/module/manifest_berth_processing.feature` — one `@wip`
scenario: a route contribution is registered in the nexus via its
per-entry factory.

## Acceptance

- Remove `@wip` from `features/module/manifest_berth_processing.feature`.
- `bb features features/module/manifest_berth_processing.feature` passes.
- All prior berth-wave scenarios still pass.
- A new step `Then the nexus has a route <key> with handler <symbol>`
  implemented and asserts presence at the conventional path.

## Out of scope

- HTTP dispatch through the registered route. Tests register; they
  don't actually serve.
- Berths without entry-level factory (the "foundation default"
  fall-through). Worth supporting in the impl per the design but
  not asserted in this bean's scenarios.

## Dependencies

- isaac-htkp (manifest shape)
- isaac-yb39 (contribution validation)
- isaac-fzsz (deps resolution — longwave's :deps on bridge)
- isaac-f77b (Module protocol — factories load via Module instances)
- isaac-c2g5 (lexicon — `:type :symbol` in fixture manifest schemas)

## Notes for the worker

- This bean is structurally simpler than isaac-jr64 (no schema
  composition, no Node protocol, no per-type multimethod dispatch).
  The entry-level factory is just a fn; foundation calls it; result
  goes into the nexus.
- The conventional nexus path for a manifest-only berth's
  registrations is `[<berth-id>]`. Routes therefore land at
  `[:marigold.bridge/signal-route]` in the fixture scenario. If a
  berth wants a different path, it should be able to declare one
  (open question — not blocking this bean).

## Summary of Changes

`isaac.module.loader` (new public fn `process-manifest-berths!`):

- Walks each berth declared across the loaded `module-index`. For manifest-only berths (declare `:manifest` without `:config`), looks at the berth's `:manifest :schema` for an entry-level `:factory` (top-level, on `:spec` for `:type :seq`, or on `:value-spec` for `:type :map`). Resolves the symbol via `requiring-resolve` and invokes `(factory entry)` once per contribution entry across every consumer.
- Entry shape per schema type: `:seq` → each element of the contribution vector; `:map` → each value; scalar → the contribution itself.
- Failure to resolve a factory symbol surfaces as `module-index.berths[<berth-id>].factory` / "could not resolve factory symbol: …".
- Berths without an entry-level `:factory` are left to the foundation default (intentionally deferred per bean's Out of Scope).
- Binds `registered-in/*module-index*` for the pass so any `:registered-in?` validations the factories trigger see the closed transitive set.

**Important integration note**: this runs OUTSIDE the loader's nested-nexus wrap. `config/load-config-result` brackets discover! in `nexus/-with-nested-nexus` to scope `:fs`; the wrap's `install! previous` discards any top-level keys factories register. So callers (the test harness, app boot) invoke `process-manifest-berths!` after load returns, against the result's `:module-index`. The function is deliberately public.

`isaac.config.config-steps` harness (`load-result`):

- After `load-config-result` returns and the wrap has exited, calls `module-loader/process-manifest-berths!` against the resulting module-index (when there are no errors). Registrations land in the ambient nexus where the scenario's Then step can read them.
- The redundant `nexus/-with-nested-nexus {:fs mem}` wrapper at this layer is removed — `initialize-root!` (see `session-steps/empty-state-directory`) has already registered `:fs` in the global nexus.
- New step: `Then the nexus has a route <route-key> with handler <handler>` — reads `[:marigold.bridge/signal-route <route-key>]` and asserts the registered value equals the expected handler symbol. Both args parsed as EDN.

Fixtures (under `spec/marigold/`):

- `marigold.bridge` declares `:marigold.bridge/signal-route` as a manifest-only berth; the berth's `:manifest :schema` is a `:type :seq` over per-element `:type :map` carrying a per-entry `:factory marigold.bridge.signal/register-route!`.
- `marigold.bridge.signal/register-route!` installs the contribution at `[:marigold.bridge/signal-route [<method> <path>]]` in the nexus.
- `marigold.longwave` declares a `:deps` on bridge and contributes one route entry `[:get "/longwave/ping"]` → `marigold.longwave/ping-handler`.
- bb.edn picks up the fixtures' `src/` dirs so `requiring-resolve` finds the factory namespaces (the test stubs `add-module-deps!`, so tools.deps doesn't extend the classpath at runtime).

`isaac.config.config-steps/coord-manifest-path` now returns a `java.io.File` (instead of a string path) when the fixture lives on real disk rather than in mem-fs, so `read-manifest`'s non-string branch uses `clojure.core/slurp` — needed for the on-disk `spec/marigold/...` modules that require a real classpath.

bb features features/module/manifest_berth_processing.feature 1/0; bb spec 1833/0; bb features 739/0.
