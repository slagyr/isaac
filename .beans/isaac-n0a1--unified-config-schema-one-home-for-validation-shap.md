---
# isaac-n0a1
title: 'Unified config schema: one home for validation shape + the factory pattern'
status: completed
type: epic
priority: high
created_at: 2026-06-13T11:58:19Z
updated_at: 2026-06-13T12:43:39Z
---

Per /tmp/unified-config-schema.md plus three refinements from the
2026-06-13 conversation. Today config validation shape lives in two
places — `:isaac.config/schema` (top-level tables) and berth
`:config :schema` (extension surfaces like comms). This unifies them:
`:isaac.config/schema` becomes the single authoritative home for
validation shape AND the optional `:factory` that stores an instance
in the nexus; berths keep only registry/contributions/path role.

## Target shape (comms)

```clojure
:isaac.config/schema
{:comms {;; descriptors (:entity-dir etc.) as today
         :schema {:type :map :key-spec {:type :id}
                  :value-spec {:type :map
                               :factory        isaac.comm.factory/create!
                               :dynamic-schema {:berth :isaac.server/comm :path [:extra-schema]}
                               :schema {:type {:validations [[:registered-in? :isaac.server/comm [:comms]]] ...}
                                        :crew {:validations [:crew-exists?]}}}}}}

:berths {:isaac.server/comm {:description ...
                             :manifest <entry contribution schema>}}  ;; no :config

:isaac.server/comm {:telly {:namespace ... :extra-schema {...}}}      ;; contributions unchanged
```

## Ownership

| Concern | Owner |
|---|---|
| Validation shape (types, validations, gather, factory) | `:isaac.config/schema` only |
| Per-impl contributions / registry | `:isaac.server/*` berth |
| Loader descriptors (`:entity-dir`, `:frontmatter?`, `:companion`) | `:isaac.config/schema` |

Invariant: each config path has exactly one schema owner.

## The three asks rolled in

1. Factory pattern available to ALL `:isaac.config/schema` tables, not
   just comms (optional — pure-data tables omit it).
2. Doc: explicit `{:berth :path}` dynamic-schema; comms schema moves
   to `:isaac.config/schema`; flatten the `:manifest {:schema}` wrapper
   (manifest TODO at resources/isaac-manifest.edn).
3. Validate `:extra-schema` via the apron/isaac meta-schema instead of
   `{:type :any}` (manifest TODO).

## Children
See child beans for the decomposition and parallelism.

## Summary of Changes
Unified config-schema landed across 5 children:
- isaac-5jk5: map-form `:dynamic-schema {:berth :path}`
- isaac-qfp6: config-schema factory pattern (any `:isaac.config/schema` table can carry a `:factory` reconciled into the nexus)
- isaac-kv0p: comms moved to `:isaac.config/schema`; berth `:config` dropped; bridge fixture decoupled to [:relays]; duplicate-path invariant
- isaac-0jn6: `:extra-schema` validated via the `:schema-map` meta-schema lexicon type
- isaac-4a93: flattened the `:manifest {:schema}` wrapper
`:isaac.config/schema` is now the single home for validation shape + the optional factory; berths keep registry/contributions only.
