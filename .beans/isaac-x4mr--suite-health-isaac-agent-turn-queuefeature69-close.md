---
# isaac-x4mr
title: 'Suite health (isaac-agent): turn_queue.feature:69 closed turnstile park/wake flake'
status: in-progress
type: bug
priority: high
tags:
    - suite-health
created_at: 2026-09-10T14:26:21Z
updated_at: 2026-09-12T16:21:46Z
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

## Promoted + widened (planner, 2026-09-11)

This bean now owns the whole isaac-agent full-suite flake family; isaac-1d7x is folded in and scrapped. Every row below is green in isolation and red only in full-suite or CI runs, which says cross-feature state leak (shared atoms, scheduler tasks, grover wait gates, or fs/nexus bindings surviving a scenario), not product.

Rows (CI run → scenario):
- 34488061619 (qpdb land): `features/turn/turn_queue.feature:69` closed turnstile park/wake — transcript empty
- 34271321013 (kbu0 land): `features/session/parallel_tool_batches.feature:124` mixed concurrent batch events; `features/session/compaction_logging.feature:140` partial-compact transcript mismatch
- y802 handoff: `compaction_memory_flush` memory_write persist; `parallel_tool_batches` cancel mid-batch events (the :101 row was a real qpdb regression, fixed on main f4813d9 — not a flake)
- 34535363284 (jejt land, 2026-09-10): `features/session/cli.feature` 'sessions cancel stamps :cancelled on a live turn and returns without waiting' — 808/1; isolated `bb features features/session/cli.feature` 34/0 three times on main ac1bf9b

Method: run `bb features` in a loop (5×) on a clean worktree of main to get a reproduction rate per row; then bisect the leak by feature ordering (gherclj runs files in path order — run the failing file immediately after each candidate predecessor). Fix the leak at the fixture: reset/await in the offending feature's teardown, or make the shared state per-scenario. Do not weaken any scenario's assertions; do not @wip.

Acceptance: 5 consecutive full `bb features` runs green on the branch (report the numbers); `bb spec` green; CI Tests green on the landing commit.

- 2026-09-11 full `bb spec` on main 32dd5b4: `spec/isaac/tool/file_spec.clj` 'read allows reading in session cwd only with :cwd opt in' 1777/1; isolated 35/0 ×3 (intermittent, same family)

- 2026-09-11 full `bb spec` on bean/isaac-xqy1 (base 0.1.64): `spec/isaac/session/policy/episodes_spec.clj` 'writes two sibling episode dirs under sessions/<crew>/<sid>/episodes after compaction' expected 2 got 1; isolated 7/0 ×2 — smells like the millisecond episode-id collision under load
