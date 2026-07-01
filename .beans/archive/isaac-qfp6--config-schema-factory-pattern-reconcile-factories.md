---
# isaac-qfp6
title: 'Config-schema factory pattern: reconcile factories declared in :isaac.config/schema'
status: completed
type: task
priority: high
created_at: 2026-06-13T11:58:38Z
updated_at: 2026-06-13T12:09:48Z
parent: isaac-n0a1
---

Item 1 of the unified config schema (isaac-n0a1): the factory pattern
should be available to EVERY `:isaac.config/schema` table, not just
comms via berth `:config`. A table whose composed value-spec declares
a `:factory` gets reconciled into the nexus by the berth engine
(isaac.config.berths/reconcile!) — pure-data tables (crew, models)
simply omit it.

Today the engine derives reconcilable paths + node factories from
berth `:config` (config-berths/config-paths). Generalize so it ALSO
sources them from the composed `:isaac.config/schema` tables (via
schema-compose's effective root). Keep berth `:config` as a source
during migration so existing comms keeps working until isaac-? moves
it.

- [ ] berths/reconcile! (and conform-berth-slices in the loader, and
      effective-root-schema / open-map-paths / normalize-errors)
      derive factory'd paths from the union of berth `:config` paths
      and `:isaac.config/schema` entries whose composed value-spec
      carries a `:factory`.
- [ ] Prove with a FIXTURE `:isaac.config/schema` table that declares
      a `:factory` (no berth `:config`): a configured slot is
      instantiated into the nexus, on-config-change!/removal work —
      the full lifecycle, sourced from `:isaac.config/schema`.
- [ ] Existing comms (still on berth `:config`) unaffected — both
      sources active.

Foundation-set (berths.clj, schema_compose.clj). Independent of the
map-form bean (different files). Parallel-safe.

## Acceptance
- bb spec + bb features green.
- New fixture proves a `:isaac.config/schema`-declared factory
  reconciles into the nexus with full lifecycle, no berth `:config`.

## Summary
The reconcile engine draws factory'd paths + node schemas from the UNION of berth `:config` and `:isaac.config/schema` tables whose composed value-spec declares a `:factory` (new `reconcile-sources`, `config-schema-factory-sources`, `spec-declares-factory?`). Pure-data tables untouched; comms still on berth `:config` (unchanged) until isaac-kv0p. Fixture proves a `:isaac.config/schema`-declared factory reconciles into the nexus with full lifecycle.
