---
# isaac-kv0p
title: Move comms validation schema into :isaac.config/schema; drop berth :config
status: completed
type: task
priority: high
created_at: 2026-06-13T11:59:13Z
updated_at: 2026-06-13T12:27:20Z
parent: isaac-n0a1
blocked_by:
    - isaac-5jk5
    - isaac-qfp6
---

Integration step of the unified config schema (isaac-n0a1): move the
comm validation shape out of the `:isaac.server/comm` berth `:config`
and into `:isaac.config/schema {:comms ...}`, using the map-form
dynamic-schema (isaac-5jk5) and the generalized factory engine
(isaac-qfp6).

Target:
```
:isaac.config/schema
{:comms {:schema {:type :map :key-spec {:type :id}
                  :value-spec {:type :map
                               :factory        isaac.comm.factory/create!
                               :dynamic-schema {:berth :isaac.server/comm :path [:extra-schema]}
                               :schema {:type {... [:registered-in? :isaac.server/comm [:comms]]}
                                        :crew {... :crew-exists?}}}}}}
:berths {:isaac.server/comm {:description ... :manifest <contrib schema>}}  ;; :config removed
```

- [ ] Server manifest + marigold themed manifest: relocate the comm
      schema; delete berth `:config`.
- [ ] effective-root-schema / config-paths now find comms via
      `:isaac.config/schema` (the qfp6 engine), gather via the
      map-form `:berth` (5jk5).
- [ ] manifest_self_consistency: claimed path <-> schema present; no
      duplicate path definitions (error if a path is defined both in
      berth `:config :schema` and `:isaac.config/schema`).
- [ ] Features config_berth_processing / schema_composition: same
      assertions, comms now sourced from `:isaac.config/schema`.

Blocked by isaac-5jk5 + isaac-qfp6. SERIALIZE with the flatten +
meta-schema manifest beans (same file region).

## Acceptance
- bb spec + bb features green.
- Comms validation + lifecycle work entirely from `:isaac.config/schema`;
  `:isaac.server/comm` has no `:config`.

## Summary
Comms validation moved from the `:isaac.server/comm` berth `:config` into `:isaac.config/schema {:comms}` with map-form `:dynamic-schema {:berth :isaac.server/comm :path [:extra-schema]}` and `:factory isaac.comm.factory/create!`. The berth keeps only `:description` + `:manifest`. Wired `schema-compose/inline-schema` and `berths/compose-config-table-schema` to run `dynamic/compose` (shared composer) so the effective root and node schema agree; unified open-map error rewriting onto `reconcile-sources`. Fixed `compose-map` to leave open maps open (no spurious empty `:schema`). Marigold comm berth `:config` dropped (its `:isaac.config/schema` = production contributions now carries `:comms`). The marigold bridge fixture moved to its own `[:relays]` path (decoupling the generic engine features from comms; added generic nexus-node steps). Duplicate-path invariant guard added to manifest_self_consistency.
