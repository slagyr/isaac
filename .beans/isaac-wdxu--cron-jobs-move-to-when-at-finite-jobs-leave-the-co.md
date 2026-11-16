---
# isaac-wdxu
title: Cron jobs move to :when / :at; finite jobs leave the config; isaac cron list
status: draft
type: feature
priority: normal
tags:
    - cron
created_at: 2026-11-16T19:01:51Z
updated_at: 2026-11-16T19:01:51Z
parent: isaac-e0qe
blocked_by:
    - isaac-ovas
---

Repo: **isaac-cron** (pins foundation at the child-1 landing). Child 2 of isaac-e0qe (design + review decisions live there). Blocked by the foundation child.

## What

Clean cutover from `:expr` to `:when` (RRULE) / `:at` (one-shot) / `:dtstart` / `:late-window` (ISO-8601 duration, default `PT12H`); `:expr` and unknown keys hard-reject, `:when` + `:at` together is an error, and every error is per entity (`cron.<name>…`) so other jobs still schedule. Jobs register as foundation `:calendar` tasks (`:at` jobs as `:at`). Finite jobs (`:at`, COUNT/UNTIL) are removed through the mutate path after their last run, and their last definition + outcome land in `<root>/cron.edn` under `:history` (with `:removed-at`). Missed one-shots run late within `:late-window`, else skip + remove + log `:cron/one-shot-expired` (history `last-status expired`). New `isaac cron list`: name, rule or `at …`, next run in the install zone. Handbook chapter rewritten (`:when` / `:at` / `:dtstart` / `:late-window`, the subset, removal of finite jobs, how to verify with `cron list`).

## Cutover scenarios (merged 2026-11-16, isaac-cron fe97fc1)

All 21 existing scenarios now use `:when` and are `@wip`. isaac-cron has no running features until this bean lands; remove every `@wip` as part of it. Pin foundation at isaac-ovas's landing (`367c9aa`) for the `:calendar` trigger.

## Deploy (planner, after landing; not part of the gate)

Rewrite zanebot's cron files in the same breath as the restart (corrected migrations in isaac-e0qe decision 9). List yopp's jobs and migrate them when yopp next upgrades.

## Acceptance

New scenarios in `isaac-cron/features/rules.feature` (feature-level `@wip`):

| line | scenario |
|---|---|
| 16 | a :when rule registers as a calendar task in the install zone |
| 28 | a one-shot :at job runs once, leaves the config, and keeps its record |
| 52 | a rule whose UNTIL has passed leaves the config after its last run |
| 69 | a missed one-shot runs late within :late-window |
| 86 | a one-shot missed by more than :late-window is skipped, removed and logged |
| 104 | a bad rule is an error on its own job; the other jobs still schedule |
| 118 | config validate names the job and the bad part |
| 147 | cron list shows each job's rule and its next run |

Plus the 21 rewritten scenarios (scenario-level `@wip`):

- `features/scheduling.feature` :21 :38 :52 :69 :84 :104 :125 :142 :160
- `features/prompt.feature` :15 :31 :50 :72 :81
- `features/delivery.feature` :10 :30 · `features/frequencies.feature` :13 :43
- `features/config_validate.feature` :10 · `features/hot_reload.feature` :10 · `features/origin.feature` :9

Run: `bb features` (whole suite) with every `@wip` removed.
