---
# isaac-qjdp
title: 'isaac-dream: habit step reads before the turn finishes (CI red)'
status: in-progress
type: bug
priority: high
created_at: 2026-10-29T16:50:30Z
updated_at: 2026-10-29T16:51:38Z
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
