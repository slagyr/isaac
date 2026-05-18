---
# isaac-u9ci
title: Retire :impl v1 alias on :comms slots
status: completed
type: task
priority: normal
created_at: 2026-05-18T19:05:34Z
updated_at: 2026-05-18T20:10:46Z
---

The `:impl` key on comm slots is a v1 alias for `:type` (see `slot-impl` in `src/isaac/configurator.clj:45-54`). The comms schema's renderer + the "no aliases / shims" preference both call for retirement.

## Scope

1. Drop `:impl` from the `comm-instance` def in `src/isaac/config/schema.clj`.
2. Drop the `:impl` / `"impl"` fallback in `isaac.configurator/slot-impl`. Slot type resolution becomes: `:type` (or `"type"`) only; nil otherwise.
3. Migrate specs that use `{:impl :foo}` to `{:type :foo}`:
   - `spec/isaac/configurator_spec.clj` (~10 occurrences)
   - `spec/isaac/config/loader_spec.clj` (~1 occurrence)
   - any others surfaced by grep
4. Boot validation will flag stray `:impl` as `unknown key` (existing path in `loader/check-comm-slot`). Confirm the error message is clear enough; if not, add a targeted check.

## Out of scope

- Auto-migration of user configs. Stray `:impl` will surface at boot as a warning. Document the rename in the bean summary.

## Definition of done

- `comm-instance` schema has no `:impl`.
- `slot-impl` reads `:type` only.
- All specs updated; `bb spec` green; `bb features` green.

## Acceptance criteria

This is a pure refactor — the existing test suite is the spec. After the change:

- `bb spec` passes (configurator_spec, loader_spec migrated from `:impl` to `:type`).
- `bb features features/cli/config.feature` passes (existing comm-validation scenario migrated from `:impl :smoke-signals` to `:type :smoke-signals`).
- `bb isaac config schema comms.value` no longer lists `impl`.

## Summary of Changes

- Removed :impl entry from comm-instance schema in src/isaac/config/schema.clj
- Simplified slot-impl in src/isaac/configurator.clj to read :type/"type" only (dropped :impl/"impl" fallback)
- Migrated all specs in spec/isaac/configurator_spec.clj from {:impl :foo} to {:type :foo}
- Migrated spec/isaac/config/loader_spec.clj comm slot from {:impl "console"} to {:type "console"}
- Updated features/cli/config.feature scenario from :impl :smoke-signals to :type :smoke-signals; updated expected error pattern from comms.relay.impl to comms.relay.type

Stray :impl keys in user configs will now surface as unknown-key warnings at boot via the existing check-comm-slot validation path.
