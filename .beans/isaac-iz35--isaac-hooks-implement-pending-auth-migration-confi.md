---
# isaac-iz35
title: 'isaac-hooks: implement pending auth-migration + config-validate feature scenarios'
status: todo
type: feature
priority: normal
created_at: 2026-07-19T18:31:58Z
updated_at: 2026-07-19T23:38:27Z
---

Split from isaac-dt9h (xapx runner-conversion sweep). NOT a runner issue — surfaced while confirming parity.

## Gap

Two `isaac-hooks` feature scenarios have no step implementations (gherclj "not yet implemented" / pending) and were already pending under the pre-xapx JVM `bb features` path:

- `features/auth_migration.feature` — "Old :hooks :auth :token slot fails validation pointing to the new slot"
- `features/config_validate.feature` — "validate reports unknown model refs with file and valid set"

These are pending, not failures — the runner conversion (dt9h) held parity (pre-existing pending stayed pending). Making them green is product work, out of scope for the runner sweep.

## Work

- Implement the retired-field schema validation so the old `:hooks :auth :token` slot fails validation and the error points to the new slot.
- Implement the config-validate step/behavior that reports unknown model refs with file + valid set.
- Implement the missing gherclj steps for both scenarios; remove any `@wip`.

## Acceptance

- [ ] `bb jvm-features` (or native features if it runs) green for both scenarios in isaac-hooks.
- [ ] `bb ci` green in isaac-hooks with features included, OR features documented JVM-only and gated accordingly.
- [ ] No regression to the native `bb spec` gate (29 examples green at dt9h time).

## Provenance

- dt9h @ isaac-hooks `0882ef9`: native `bb spec` 29ex/0; jvm-features carried these 2 pending. See dt9h "Verify fail resume" note.
