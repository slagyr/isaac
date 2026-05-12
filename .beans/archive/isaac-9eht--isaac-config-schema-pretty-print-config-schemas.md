---
# isaac-9eht
title: "isaac config schema: pretty-print config schemas"
status: completed
type: feature
priority: low
created_at: 2026-04-17T04:19:13Z
updated_at: 2026-04-19T23:15:39Z
---

## Description

Add schema introspection to the config command so operators (and especially crew agents editing config) can see what's valid without reading source.

## Subcommands
- `isaac config schema` — print all section schemas
- `isaac config schema <section>` — print one section's schema
  - section ∈ {root, defaults, crew, model, provider}

Note: schema is keyed by role, not by id. `isaac config schema crew` prints the schema every `:crew <id>` entry conforms to — there is no per-id schema.

## Output
Pretty-printed schema showing for each field: name, type, required?, default (if any), short doc.

## Depends on
- isaac-kh5s (config CLI scaffold exists)
- isaac-5w3x (schemas are rich enough to be worth printing — types/docs/required)

If we shipped schema-print against the current thin schema (just allowed-key sets), output would be underwhelming and we'd rewrite once schemas get enriched. Blocking on 5w3x avoids that churn.

Feature: features/cli/config.feature (add scenarios when unblocked)

## Acceptance Criteria

1. Remove @wip from the 6 schema scenarios in features/cli/config.feature
2. bb features features/cli/config.feature passes (all scenarios, including schema)
3. bb features passes
4. bb spec passes
5. isaac config schema uses c3kit.apron.schema.path for path navigation and c3kit.apron.schema.term for rendering

## Notes

c3kit-apron 2.6.0 ships built-in schema renderers that likely cover the pretty-print need with zero bespoke code:

- c3kit.apron.schema.term — ANSI-colored two-column terminal output
- c3kit.apron.schema.markdown — bullets, tables, full markdown docs
- c3kit.apron.schema.openapi — OpenAPI 3.0/3.1 generation (bonus if we ever expose an HTTP API)

Implementation lean: isaac config schema --format=term (default) delegates to schema.term; --format=markdown delegates to schema.markdown. No custom pretty-printer needed. Scope shrinks dramatically.
Scenarios drafted and committed (663d044). Path-based grammar shared with config get (via c3kit.apron.schema.path). Rendering uses c3kit.apron.schema.term only — markdown format dropped from scope.
schema.term has been stolen into isaac.config.schema.term (commit 47b713c). Rendering uses that namespace now, not c3kit.apron.schema.term (which is going away upstream). Full control over the renderer lets us emit command-line-safe paths inline — tracked separately as isaac-TBD.

