---
# isaac-n7ll
title: Dream schedules move to :when (RRULE); foundation drops the 5-field :cron trigger
status: draft
type: feature
priority: normal
tags:
    - dream
    - scheduler
created_at: 2026-11-16T19:37:08Z
updated_at: 2026-11-16T19:37:08Z
parent: isaac-e0qe
blocked_by:
    - isaac-ovas
    - isaac-wdxu
---

Repos: **isaac-dream** (crew `:dream :schedule`), **isaac-foundation** (remove the 5-field `:cron` trigger). Child 3 of isaac-e0qe. Blocked by isaac-ovas and isaac-wdxu.

Micah, 2026-11-16: "one strategy is better than multiple strategies." After cron moves to RRULE, the only 5-field cron left is dream's schedule (zanebot: `crew/zane.edn :dream {:schedule "0 3 * * *"}`), which is the sole reason foundation keeps its `:cron` trigger and parser.

## What

- Dream's schedule becomes an RRULE: `:dream {:when "FREQ=DAILY;BYHOUR=3;BYMINUTE=0"}` (optional `:dtstart`), registered as a foundation `:calendar` task in the install `:tz`. `:schedule` hard-rejects, per entity, like `:expr` in isaac-wdxu.
- Foundation drops the `:cron` trigger kind and its 5-field parser (`scheduler/cron.clj`), the `:expr` trigger field and its schema text; `features/scheduler/triggers.feature`'s `:cron` scenario goes (a removal check is a one-time acceptance item, not a permanent scenario).
- Handbooks: dream chapter (`:when`), foundation scheduler section (no `:cron`).

## Deploy (planner)

Rewrite each crew's `:dream :schedule` to `:when` in the same breath as the restart (zanebot: zane `0 3 * * *` → `FREQ=DAILY;BYHOUR=3;BYMINUTE=0`; check every crew file and yopp).

## Acceptance

Draft — scenarios to come (dream registers a `:calendar` task from `:when`; `:schedule` is refused naming the crew; one-time grep that `:kind :cron` / `cron.clj` are gone from foundation src).
