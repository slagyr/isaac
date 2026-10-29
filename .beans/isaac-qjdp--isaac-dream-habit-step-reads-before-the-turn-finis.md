---
# isaac-qjdp
title: 'isaac-dream: habit step reads before the turn finishes (CI red)'
status: completed
type: bug
priority: high
created_at: 2026-10-29T16:50:30Z
updated_at: 2026-10-29T17:08:59Z
parent: isaac-pcm3
---

isaac-dream `main` CI is red since isaac-b1ir landed (`edfabc6`, run 40658840830). One scenario fails: `features/habits/propose.feature:18` "dream__propose stores a habit as proposed".

## Cause (planner, 2026-10-29)

It is a test bug, not a product bug. The step `Then crew {id} has a habit matching:` (`spec/isaac/dream/habit_steps.clj`) reads the habit files right after `When the user sends …` without waiting for the turn to finish. The tool does write the habit — a transcript assertion placed first shows `Habit <uuid> proposed` and then the habit step passes. `session has transcript matching:` passes because it calls `isaac.agent.session.session-steps/await-turn!` first.

Reproduce: `bb features features/habits/propose.feature:18` fails alone every time. It passes when the operator scenarios run before it (warm JVM), which is why the worker saw `bb ci` green.

## Fix

In `crew-has-habit`, await the turn before listing habits, the same way the transcript step does (`await-turn!` from isaac-agent's session steps). No change to the feature files.

The repo was also missing the `ISAAC_HAIL_URL` secret, so the red build hailed nobody (`CI Failure Hail` failed with "Failed to parse URL"). Planner set it on 2026-10-29; nothing to do in code.

## Likely repo scope

`isaac-dream` only (`spec/isaac/dream/habit_steps.clj`).

## Acceptance

- `bb features features/habits/propose.feature:18` passes run alone, three times in a row.
- `bb ci` green in isaac-dream.
- `CI Tests` green on isaac-dream `main` after landing.
- Feature files unchanged.

## Implementation (2026-10-29)

- `isaac-dream` branch `bean/isaac-qjdp`, commit `dcd134c`: `crew-has-habit` calls `session-steps/await-turn!` before listing habits; added deterministic spec proving that a habit written on turn completion is visible. Feature files unchanged.
- TDD: new focused spec failed before fix (`Expected truthy ... got: nil`), then passed (13 examples, 20 assertions).
- `bb features features/habits/propose.feature:18` passed alone three consecutive times (1 example, 1 assertion each); `bb ci` passed (13 specs, 12 features). The original scenario also passed three times before the fix in this checkout; deterministic unit regression verifies the race independent of timing.
- `bb bean-gate verify isaac-qjdp` exited 2 (`no feature-baseline: use the verify path`). Implementation remains on its pushed bean branch for independent verification and landing; main CI cannot be checked until landing.



## Landed on main (2026-10-29)

main-sha: isaac-dream fc769f09918fdf7ad4d1e18b24ca0db30f843dc2

Verification: isolated `bb features features/habits/propose.feature:18` green three times (1 example, 1 assertion each); `bb ci` green (13 specs/20 assertions, 12 features/26 assertions); feature files unchanged; deterministic regression spec reviewed; CI Tests main run 40918224602 succeeded on fc769f0. No .verify-baseline.edn exists; no test-infrastructure changes. Existing dependency namespace warnings and structured config debug output are outside this diff.
