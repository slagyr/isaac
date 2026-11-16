---
# isaac-wdxu
title: Cron jobs move to :when / :at; finite jobs leave the config; isaac cron list
status: todo
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

feature-baseline: isaac-cron fe97fc1e43120d5b23c2c3a546cfbbba66aa4ab6
feature-blob: isaac-cron features/config_validate.feature da65173f37f007001a913ca5fb87f207c770b8e4 10
feature-blob: isaac-cron features/delivery.feature 2f0e789bf44a7f30b11b9266e75384712e560f6d 10,30
feature-blob: isaac-cron features/frequencies.feature 56eeaadb3be13b953722b539043d94f4d6af3d89 13,43
feature-blob: isaac-cron features/hot_reload.feature b0bb7a392b06593f0abdc04e679107d32bdba10f 10
feature-blob: isaac-cron features/origin.feature 50ecbf17a0d7a67a1e06fe294ce9a07f9cd4fc96 9
feature-blob: isaac-cron features/prompt.feature daf8c7cfe770e66fb7fd34467ad44de278ffa1b0 15,31,50,72,81
feature-blob: isaac-cron features/rules.feature 1c405b1a63834320177331aa00d8d7691b9d20f9 16,28,52,69,86,104,118,147
feature-blob: isaac-cron features/scheduling.feature 710e22b96c34adba95c512461a1200d734894a49 21,38,52,69,84,104,125,142,160
