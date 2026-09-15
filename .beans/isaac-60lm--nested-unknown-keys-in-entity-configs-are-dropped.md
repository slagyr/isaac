---
# isaac-60lm
title: 'Nested unknown keys in entity configs are dropped silently: recurse unknown-key warnings'
status: draft
type: bug
priority: normal
tags:
    - foundation
    - config
created_at: 2026-09-15T17:47:11Z
updated_at: 2026-09-15T17:47:11Z
---

## Problem

Unknown keys nested inside entity configs are dropped silently: no error, no warning.

Found on isaac-7gjs (2026-09-15): after `:max-request-tokens` was removed from the compaction schema, a model config with `:compaction {:max-request-tokens 1}` loads with errors `[]` and warnings `[]`. A control with `:compaction {:threshold 5.0}` does error (`models.harbor.compaction.threshold`), so nested values are validated, but nested keys the schema no longer knows are not reported.

Cause, isaac-foundation `src/isaac/config/warnings.clj`:
- `root-entity-warnings` → `collect-unknown-key-warnings` checks only the entity's top-level fields (`models.<id>.<field>`), one level.
- `nested-unknown-key-warnings` recurses into closed maps and open maps, but `config-table-warnings` applies it only to statically declared top-level tables, not to entity kinds (`models`, `crew`, `providers`, …).
- `slice-unknown-key-warnings` for berth slices is also shallow by its own docstring.
- Every unknown-key finding is a warning, never an error.

Effect: config that references a removed or misspelled nested key (e.g. zanebot `models/gpt.edn` `:compaction {:max-request-tokens 400000}` after isaac-7gjs) keeps loading with no signal, so a clean-cutover removal cannot be enforced or even noticed.

## Proposal

- Recurse into entity fields with `nested-unknown-key-warnings` against each field's schema, so `models.<id>.compaction.<key>` and similar nested maps report unknown keys.
- Same for berth slices.
- Keep them warnings (consistent with existing unknown-key behavior).

## Open questions (not decided)

- Should unknown keys under a closed nested schema be errors instead of warnings (true hard-reject for clean cutovers), or stay warnings everywhere?
- Double reporting: make sure the entity pass and the config-table pass never both report the same path.

## Acceptance (draft — scenarios TBD)

- A model config with `:compaction {:max-request-tokens 1}` produces a warning keyed `models.<id>.compaction.max-request-tokens` with value "unknown key".
- A valid nested compaction config produces no warning.
- Existing top-level, entity, slice, and table unknown-key warnings are unchanged.
