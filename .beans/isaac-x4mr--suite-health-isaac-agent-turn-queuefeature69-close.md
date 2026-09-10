---
# isaac-x4mr
title: 'Suite health (isaac-agent): turn_queue.feature:69 closed turnstile park/wake flake'
status: draft
type: bug
priority: high
tags:
    - suite-health
created_at: 2026-09-10T14:26:21Z
updated_at: 2026-09-10T14:26:21Z
---

Ambient full-suite flake on `isaac-agent` that failed GitHub Actions CI Tests on isaac-qpdb land SHA `58982c6` (run 34488061619, `bb ci` / `bb features`): 797 examples, 2 failures, one of them this scenario. **Not qpdb.** Isolated run is green. Do not reopen **isaac-qpdb**.

## Observed (2026-09-10, perceptor@isaac-verify)

- Full CI on `58982c6`: `turn/turn_queue.feature:69` — closed turnstile park/wake, transcript empty (`Tied off missing`)
- Isolated: `clojure -M:features features/turn/turn_queue.feature:58` → 1/0
- Release 0.1.59 `74b9acd` CI run 34488386985 did **not** reproduce this row (only `parallel_tool_batches:101` failed that run)
- Diff `837b6d4..58982c6` is `registry.clj` + `registry_spec` only (10 lines); `turn.clj` unchanged

Sibling: **isaac-1d7x** owns the parallel_tool_batches flakes (`:101` / `:124`) plus `compaction_logging:140`. **isaac-ohsy** (completed) is the turn-request queue product.

## This bean owns

Make `features/turn/turn_queue.feature:69` suite-stable without weakening intent.

1. Reproduce on current `isaac-agent` main in a fresh worktree. Record whether it is isolated, full-suite-only, or CI-only.
2. Name the cause: fixture race, park/wake ordering, transcript matcher, or cross-feature state leak.
3. Fix the leak. **Do not** weaken scenario intent. **Do not** `@wip` without a dedicated bean owning its return.
4. Do not recut qpdb `run-handler`. Do not reopen ohsy product unless the cause is a real queue regression (then hail plan with evidence).

## Acceptance

    cd isaac-agent
    bb features features/turn/turn_queue.feature

0 failures on repeated runs. Then record an unwrapped `clojure -M:features` (or CI Tests) run that no longer flakes at `:69`. If other unrelated reds remain, name them with owning bean ids.
