---
# isaac-8yxs
title: 'Manifest-only berth processing: per-entry :factory invocation'
status: todo
type: feature
priority: normal
created_at: 2026-06-04T14:40:17Z
updated_at: 2026-06-04T14:40:37Z
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
