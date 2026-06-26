---
# isaac-orc1
title: 'Orchestration smoke test: exercise the full bean workflow with a deliberate no-op'
status: todo
type: task
priority: low
created_at: 2026-06-26T15:17:03Z
updated_at: 2026-06-26T15:17:03Z
---

## Purpose
This bean exists **purely as a process test**. Its only job is to travel the complete orchestration pipeline (planner → worker(s) → unverified handoff → verifier) with as little actual work as possible. The goal is to surface gaps, friction points, and missing pieces in our bean workflow, tooling, communication, and handoff practices.

This is **not** a real feature or bug fix. Success = the bean makes it all the way through the defined process while we document what broke or felt awkward.

## What "no-op" means for this bean
- Worker should do the absolute minimum required to move the bean forward (e.g. a trivial edit or just a note that the step was exercised).
- No new code, no tests, no docs changes are required to "complete" the technical work.
- All value comes from the **journey** and the observations written along the way.

## Acceptance Criteria (process-oriented)
- Bean is created by planner in `todo` state with clear process-test intent documented in the body.
- At least one worker claims the bean (`status=in-progress`), performs the minimal handoff work, and hands it off with `--tag=unverified`.
- Verifier picks it up, reviews the journey notes, either marks it completed or returns it with feedback, and removes the `unverified` tag.
- Throughout the exercise, participants append observations to the bean body under a `## Process Observations` section covering:
  - Friction in claiming / status updates
  - Clarity (or lack) of acceptance criteria from the worker/verifier perspective
  - Gaps in tooling (`beans` CLI behavior, git workflow, notifications)
  - Communication/hand-off issues (between planner, worker, verifier, and human)
  - Any missing steps or unclear rules in the current AGENTS.md / bean workflow
- At least one follow-up item (new bean or note) is created if significant gaps are found.
- The final completed bean body serves as a short "after action report" of the test run.

## Notes for Participants
- Feel free to be brutally honest in the observations. The point is improvement of the system, not "finishing work".
- If something is painful, document it instead of working around it silently.
- This bean can be used multiple times if we want to test specific parts of the pipeline (e.g. multi-worker handoff, hail-driven flow, etc.).
- Do **not** treat this as a normal bean that needs "real" implementation to be valid.

## Handoff
When creating this bean, the planner should treat it like any other: give it a proper ID, make the intent obvious, and put it in the active list.

Subsequent workers and verifiers should treat the process steps themselves as the work to be verified.
