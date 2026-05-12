---
# isaac-5w3x
title: "Migrate config validation from hand-rolled key-sets to c3kit.apron.schema"
status: completed
type: task
priority: normal
created_at: 2026-04-17T04:18:32Z
updated_at: 2026-04-18T06:41:40Z
---

## Description

The isaac-16v worker implemented config validation with hand-rolled key-set checks in src/isaac/config/schema.clj + imperative rules in src/isaac/config/loader.clj, rather than the c3kit.apron.schema approach specified in the bead description. Validation works and the 20 composition scenarios pass, but the schema data is thin (just allowed-key sets; no types, doc strings, or required markers).

This bead migrates to c3kit.apron.schema proper.

## Scope
- Express each config section as a c3kit.apron.schema entity: root, defaults, crew, model, provider
- Schema data captures: type, required?, default, validations, doc per field
- loader.clj's imperative validation delegates to schema where possible; cross-file rules (filename/id match, duplicate-id across sources, cross-refs like defaults.crew → :crew <id>) stay in loader since they span entities

## Open question
c3kit.apron.schema may not natively handle map-of-id → entity (where keys are user-defined ids, not fixed). Options if true:
1. Contribute upstream (update c3kit.apron.schema to support open-keyed maps)
2. Wrap: iterate the map and apply the entity schema to each value
3. Custom collection validator

Start with (2) if simplest; upgrade to (1) if there's a clean API to add.

## Regression boundary
- bb features features/config/composition.feature still passes (20 examples)
- bb spec passes
- features/cli/config.feature (if isaac-kh5s lands first) still passes

## Why this matters
A richer schema unlocks better operator UX: isaac config schema can print types/docs; validation errors can cite the schema; agent crew editing config can be given schema hints.

## Acceptance Criteria

1. src/isaac/config/schema.clj defines schemas using c3kit.apron.schema
2. loader.clj validation delegates to schema where entity-level; cross-file checks remain imperative
3. bb features features/config/composition.feature passes (20 examples, 0 failures)
4. bb spec passes
5. If c3kit.apron.schema was updated, the updated version is pinned in deps.edn with a note

## Notes

Verification failed: Criterion 1 not met. schema.clj does not use c3kit.apron.schema — the ns declaration has no require for it and there are no calls to schema/conform or any c3kit schema API. The schemas are plain Clojure maps with hand-rolled :type/:doc keys, which is structurally the same approach the bead's description identifies as the problem to solve. loader.clj likewise only uses these maps for key-set containment checks (contains? entity-schema key), not via c3kit schema/conform. Criteria 3 and 4 pass (bb features composition.feature: 20 examples 0 failures; bb spec: 798 examples 0 failures).

