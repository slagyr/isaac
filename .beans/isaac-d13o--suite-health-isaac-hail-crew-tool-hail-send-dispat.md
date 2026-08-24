---
# isaac-d13o
title: 'Suite health (isaac-hail): crew-tool hail-send dispatch scenarios expect 1 pending got 0'
status: draft
type: bug
priority: high
created_at: 2026-08-24T14:15:18Z
updated_at: 2026-08-24T14:15:18Z
---

## Problem

On isaac-hail `main@9c9d742`, full `bb features` is red with 2 failures (2 pending unrelated hail-get stubs remain):

1. `features/crew-tool.feature` — "crew with hail-send allowed dispatches a hail from a turn" — `the sole pending hail EDN contains` expected 1 got 0
2. `features/crew-tool.feature` — "hail-send with a real explicit session still dispatches (isaac-8lhv)" — same assertion, expected 1 got 0

Surfaced by isaac-u7ug verify-fail attempt 2. Bean diff did not touch crew-tool.feature; u7ug acceptance was rescoped off full-suite green so this debt is no longer a silent verifier trap.

## Diagnosis notes (planner, 2026-08-24)

- Failing Then is `the sole pending hail EDN contains:` → `store/list-records "pending"` via feature-steps.
- That step does **not** `await-turn!` (unlike the sibling "there are no pending hails" / error-matching steps). Possible race vs genuine send/root miss.
- u7ug changed `store/runtime-root` to `nexus → loader → root/current-root` (aligned with queue). If ambient nexus `:root` diverges from the feature root, list-records can miss pending writes — check first before rewriting product behavior.
- Do not paper over by weakening the dispatch contract; restore green dispatch scenarios.

## Scope

isaac-hail only. Repair until the two crew-tool scenarios pass. No production change unless root-resolution or hail-send path is actually broken.

## Proposed acceptance (promote to todo after scenarios confirmed)

```
cd isaac-hail && bb features features/crew-tool.feature
```

0 failures. Full `bb features` regressions limited to the pre-existing 2 pending hail-get stubs (or fewer).

## Sequencing

Unblocks honest full-suite verify for subsequent isaac-hail beans. Independent of isaac-u7ug product scope (durable ledger).
