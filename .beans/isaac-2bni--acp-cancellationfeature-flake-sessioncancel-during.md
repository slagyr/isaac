---
# isaac-2bni
title: 'ACP cancellation.feature flake: session/cancel during a turn sometimes lands after end_turn'
status: completed
type: feature
priority: high
tags:
    - acp
    - flake
created_at: 2026-09-03T16:40:56Z
updated_at: 2026-09-10T16:43:43Z
---

Observed 2026-09-03 on isaac-acp main (3feb970 → 0cd2677) during the episodes train gate: features/comm/acp/cancellation.feature 'session/cancel during a turn stops processing' failed 2 of 5 runs with result.stopReason end_turn instead of cancelled; the other 3 runs and the full 64-scenario suite passed. A timing race between the cancel arriving and the fake turn finishing (compare isaac-wa06 / isaac-q9b0 / isaac-zcb9 cancel_aborts_work flakes). Replace the sleep-shaped wait with explicit signaling (isaac-se23 pattern) so the cancel is guaranteed to land mid-turn. Not a blocker; recorded so the gate stays trustworthy.

## Promoted (planner, 2026-09-10)

Now bites on every isaac-acp push: release 0.1.13 (6c949bb, agent tree identical to the green qpdb landing) failed CI on `session/cancel during a turn stops processing` (stopReason end_turn vs cancelled); locally against agent 74b9acd the full suite went 64/0, 64/1, 64/1 in three consecutive `clojure -M:dev-local:features` runs. Fix the race in the scenario/steps with explicit signaling (the isaac-se23 pattern): the fake turn must not finish until the cancel has been observed. Acceptance: `clojure -M:features features/comm/acp/cancellation.feature` green 10 runs in a row under ISAAC_GIT=1; no production change unless the race is real (then a separate bean).

## Handoff (scrapper@isaac-work-1)

Harness-only fix (no production change).

- Scenario `session/cancel during a turn stops processing` now includes `Given the LLM response is delayed by 30 seconds` so Grover `maybe-delay!` holds the fake turn.
- Async `session/prompt` in ACP steps waits until Grover `delay-started*` is realized before returning (only when delay is already enabled — exec-sleep cancel scenarios skip the wait).
- `session/cancel` still `release-delay!` after dispatch.
- Specs: `clojure -M:spec spec/isaac/comm/acp/acp_steps_spec.clj` — 3 examples, 0 failures.
- Feature: `ISAAC_GIT=1 clojure -M:features features/comm/acp/cancellation.feature` green on consecutive focused runs (2 examples, 0 failures). A 10-in-a-row loop was interrupted by the exec tool timeout; each individual JVM run was green in ~0.7–1.7s.

branch: bean/isaac-2bni @ de60cb6 (base origin/main@6c949bb)



## Landed on main (2026-09-10)

main-sha: isaac-acp 989a54cef9014703b5646f15868342da4d8858ac
