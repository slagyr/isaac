---
# isaac-c4em
title: 'config set: drop the siblings-all-files placement rule (preference only)'
status: in-progress
type: task
priority: normal
created_at: 2026-09-30T04:01:23Z
updated_at: 2026-09-30T04:04:39Z
---

Ruling: Micah, 2026-09-30. Drop the "siblings are all files" placement rule that isaac-cvri added. Placement is just:

- **Existing entry:** written where it already lives. An entity file never becomes inline, and an inline entry never becomes a file.
- **New entry:** its own entity file if `:prefer-entity-files` is true, otherwise inline in `isaac.edn`.

Both live instances (yopp, zanebot) set `:prefer-entity-files true`, so crews/cron jobs created there (including via `handbook__configure`) still land as files.

## Wanted

- Remove rule 2 from `isaac.config.mutate` (`inline-siblings?`, `dir-has-files?`, the `:siblings-all-files?` state key and its `choose-set-location` branch) and its unit specs. Clean cutover.
- `set-many!` and the whole-entity companion split from isaac-cvri stay.
- Update `src/isaac/foundation/handbook.md` (Config → Paths and editing) to describe the two-rule precedent.

## Acceptance

`isaac-foundation/features/cli/config_set_new_entity_placement.feature` (rewritten, 4 scenarios, `@wip`). `features/cli/config_set_many.feature` scenario 5 (a new whole entity in a batch) must still pass; if it relied on the siblings rule, the planner re-baselines it with `:prefer-entity-files true` in its setup.

## Likely repo scope

isaac-foundation only.

feature-baseline: isaac-foundation 132a2eef6d65d6dcedd0e8a7e4def0aaadad7691
feature-blob: isaac-foundation features/cli/config_set_new_entity_placement.feature add91e870d8cfec40bbbbf5fc63cc11060df3de1
feature-blob: isaac-foundation features/cli/config_set_many.feature b9e3935d788dd77275b51b9dd989614094c33fc2 75
