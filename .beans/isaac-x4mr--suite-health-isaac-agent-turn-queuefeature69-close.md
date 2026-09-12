---
# isaac-x4mr
title: 'Suite health (isaac-agent): turn_queue.feature:69 closed turnstile park/wake flake'
status: completed
type: bug
priority: high
tags:
    - suite-health
created_at: 2026-09-10T14:26:21Z
updated_at: 2026-09-12T20:33:00Z
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

## Worker checkpoint (scrapper@isaac-work-3, 2026-09-12)

Done: branch `bean/isaac-x4mr` @ `c40b112` is rebased on `origin/main@0164ed9`. Root causes were shared async fixture races: Grover reset orphaned wait gates; scripted dequeue was non-atomic; concurrent turn-worker nudges were dropped; teardown forgot live futures; filesystem/transcript assertions could run before async turns; and cancellation fixtures used timing windows. Fixes release gates, atomically dequeue, coalesce worker ticks, deterministically drain/cancel futures, await async compaction/turns at assertion and send boundaries, and use blocking cancellation seams without weakening intent.

Acceptance complete: target queue feature green 5/5 (5 examples, 0 failures, 20 assertions each); widened family green 5/5 (59/0/166); five consecutive literal `bb features` runs green at 754 examples, 0 failures, 1793 assertions, 1 existing pending (180.5s, 174.3s, 172.3s, 177.7s, 184.9s); prior five consecutive unwrapped JVM full runs green; final `bb spec` green at 1608 examples, 0 failures, 3316 assertions. Native task timeout was raised from 180s to 600s because successful full runs exceed 180s under load.

Next: verifier lands `bean/isaac-x4mr` and runs CI Tests. Review entry points: `src/isaac/llm/api/grover.clj:31`, `src/isaac/turn/worker.clj:79`, and `spec/isaac/session/session_steps.clj:582`.

## Landed on main (2026-09-12)

main-sha: isaac-agent 7e2df9da4551a8a12d384dbca9db3997e2e0595d

## Verify fail (attempt 1, 2026-09-12): CI Tests 34715758715 on land SHA 7e2df9da still red at parallel_tool_batches.feature:124

HEAD: e2888a3c (beans) / 7e2df9da (isaac-agent origin/main)
Working tree: clean except ?? wt/ on agent
GHA: https://github.com/slagyr/isaac-agent/actions/runs/34715758715
Job verify / `bb ci`: spec **1608/0/3316**; features **754/1/1791**, 1 pending.
Failure: `features/session/parallel_tool_batches.feature:124` — "one call fails and the other succeeds — each result is its own, the cycle completes" (`Then the memory comm has events matching:` Expected truthy: false). Native bb features path (Graal). Isolated/full-suite flake this bean already owns (folded isaac-1d7x). Local verifier `bb features` on the same tree was 754/0/1793. Acceptance requires CI Tests green on the landing commit — not met. Do not reopen qpdb/5gvq/1d7x/ohsy. Hail 0261129e.

## Verify repair (attempt 2, scrapper@isaac-work-1, 2026-09-12)

The CI-only `parallel_tool_batches.feature:124` failure was an invalid scheduler-order fixture: the scenario required the quick success event before a real `fs__read` failure, but neither tool had a synchronization relationship, so Graal was free to complete `fs__read` first. This did not test the stated contract (independent success/failure, both returned in provider batch order); it asserted incidental wall-clock scheduling.

Repair keeps the approved behavior and makes its precondition explicit without sleeps: a test failure tool blocks on the existing completion-signal seam until `test__quick` finishes, then returns `{:isError true}`. The scenario still runs one failing and one successful tool concurrently, asserts both memory-comm results plus reply, asserts both transcript results, and asserts provider follow-up results in original call order. No product code changed, no scenario assertion was removed, and no `@wip` was added.

Branch: `bean/isaac-x4mr-fix @ ed9ad7cedd50e9b7b80aba935d3152dda8d4e430` (base `origin/main@7e2df9da4551a8a12d384dbca9db3997e2e0595d`).

Evidence:
- RED on landed main: focused `parallel_tool_batches.feature:124` failed immediately with actual events `fs__read` then `test__quick`.
- New helper spec: 1 example, 0 failures, 2 assertions.
- Target scenario: 20 consecutive native Graal runs green, 1/0/3 each.
- Full target feature: 6 examples, 0 failures, 17 assertions.
- Full `bb spec`: 1609 examples, 0 failures, 3318 assertions.
- Full native `bb features`: 754 examples, 0 failures, 1793 assertions, 1 existing pending.

Verifier should land `bean/isaac-x4mr-fix`, run `bb ci`, and confirm CI Tests green on the new landing SHA.

## Landed on main (2026-09-12)

main-sha: isaac-agent 44c3e3c2ef6709c2c598af0a799d29841a6967ea
