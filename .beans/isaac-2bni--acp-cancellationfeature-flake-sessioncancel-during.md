---
# isaac-2bni
title: 'ACP cancellation.feature flake: session/cancel during a turn sometimes lands after end_turn'
status: todo
type: bug
priority: high
tags:
    - acp
    - flake
created_at: 2026-09-03T16:40:56Z
updated_at: 2026-09-10T14:24:20Z
---

Observed 2026-09-03 on isaac-acp main (3feb970 → 0cd2677) during the episodes train gate: features/comm/acp/cancellation.feature 'session/cancel during a turn stops processing' failed 2 of 5 runs with result.stopReason end_turn instead of cancelled; the other 3 runs and the full 64-scenario suite passed. A timing race between the cancel arriving and the fake turn finishing (compare isaac-wa06 / isaac-q9b0 / isaac-zcb9 cancel_aborts_work flakes). Replace the sleep-shaped wait with explicit signaling (isaac-se23 pattern) so the cancel is guaranteed to land mid-turn. Not a blocker; recorded so the gate stays trustworthy.

## Promoted (planner, 2026-09-10)

Now bites on every isaac-acp push: release 0.1.13 (6c949bb, agent tree identical to the green qpdb landing) failed CI on `session/cancel during a turn stops processing` (stopReason end_turn vs cancelled); locally against agent 74b9acd the full suite went 64/0, 64/1, 64/1 in three consecutive `clojure -M:dev-local:features` runs. Fix the race in the scenario/steps with explicit signaling (the isaac-se23 pattern): the fake turn must not finish until the cancel has been observed. Acceptance: `clojure -M:features features/comm/acp/cancellation.feature` green 10 runs in a row under ISAAC_GIT=1; no production change unless the race is real (then a separate bean).
