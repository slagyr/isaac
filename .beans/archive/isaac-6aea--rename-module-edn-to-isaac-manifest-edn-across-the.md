---
# isaac-6aea
title: "Rename module.edn to isaac-manifest.edn across the codebase"
status: completed
type: task
priority: normal
created_at: 2026-05-05T21:12:42Z
updated_at: 2026-05-05T23:45:01Z
---

## Description

Why: we agreed during planning to namespace the module manifest filename (avoids classpath collisions when modules ship as tools.deps artifacts that may include other libs with a generic 'manifest.edn'). isaac-4or2's description called for isaac-manifest.edn, but the worker shipped with module.edn. This bead does the rename.

## Scope

- Rename the 2 manifest files on disk:
  - modules/isaac.comm.discord/resources/module.edn -> isaac-manifest.edn
  - modules/isaac.comm.telly/resources/module.edn -> isaac-manifest.edn
- Update src/isaac/module/loader.clj — replace 'module.edn' with 'isaac-manifest.edn' (lines 82, 103, anywhere else).
- Update specs that reference the filename:
  - spec/isaac/config/loader_spec.clj
  - spec/isaac/module/loader_spec.clj
  - spec/isaac/module/layout_spec.clj
- Update feature files that reference the filename:
  - features/modules/coordinates.feature
  - features/modules/discovery.feature

## Acceptance

- No 'module.edn' references remain anywhere in src/, spec/, features/, or modules/ (except possibly historical comments).
- bb spec passes.
- bb features passes.
- A grep for 'module.edn' across the project returns nothing actionable.

## Acceptance Criteria

All on-disk manifest files renamed to isaac-manifest.edn; all source/spec/feature references updated; bb spec and bb features pass; grep for 'module.edn' is clean

