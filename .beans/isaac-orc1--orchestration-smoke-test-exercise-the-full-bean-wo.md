---
# isaac-orc1
title: 'Orchestration smoke test: exercise the full bean workflow with a deliberate no-op'
status: completed
type: task
priority: low
tags: []
created_at: 2026-06-26T15:17:03Z
updated_at: 2026-06-27T16:42:22Z
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

## Process Observations

### Worker pass (scrapper, hail-driven)
- Claimed the bean and exercised the worker handoff path without changing product code.
- Minimal work performed: claim + workflow notes + follow-up bean for process gaps.

### Friction in claiming / status updates
- The trusted hail said this session was the `isaac-1` checkout in `quarters`, but that path was not present here. I had to discover the actual repo checkout manually before I could act.
- `list_skills` returned no discovered skills, even though the worker workflow references loading a hail/bean-work skill. The task was still doable, but only by falling back to repo docs and direct CLI usage.

### Clarity of acceptance criteria
- The no-op intent is clear in the bean body.
- For a hail-driven worker, the exact expected deliverable is still slightly implicit: the bean says to append observations, while the hail says to claim, note the step, document gaps, and hail verify. Those are compatible, but not expressed as a short worker checklist in one place.

### Tooling / git / notifications
- Session bootstrap is the main gap: repo/session location was not authoritative from the hail alone.
- Skill discovery mismatch is a second tooling gap: referenced skills may not be available in-session, so the workflow needs an explicit fallback path.
- `beans list --all` does not exist; the CLI help corrected this quickly, but it is an easy assumption to make when inspecting tracker state.

### Communication / hand-off
- The hail was sufficient to proceed once the repo was located.
- The verify target is clear at the band level, but there is no local skill/document in this checkout showing the exact expected verify hail payload shape for this project, so I am using the direct bean details + commit references.

### Missing / unclear workflow rules
- There should be a single authoritative place that tells a hail-driven worker: expected checkout path, repo root, whether skills are guaranteed to exist, and the fallback if they do not.
- There should be an explicit statement in the worker docs for process-test beans that no product-code/test changes are expected, so the worker does not have to infer whether normal red/green rules are intentionally suspended.

## Follow-up
- Created follow-up bean to track the bootstrap/skill-discovery gap observed in this run.


### Replayed hail / idempotency check (2026-06-27)
- A later autonomous `isaac-work` hail for the same bean arrived after the bean was already `in-progress` + `unverified`.
- After syncing the actual `isaac-1` checkout, the worker saw the existing claimed/handoff state and did not try to re-claim the bean.
- This is a useful process signal: replayed/autonomous work hails need an explicit idempotency rule, but the current git+beans state was enough to avoid a duplicate claim.
- The repo-local fallback docs now exist in `.toolbox/commands/work.md`, which helped recover even though `load_skill "work-bean"` was still unavailable in-session.

## Verification (2026-06-29)
Closed as a process-test bean. The acceptance is met from the bean body itself:

- planner created it as a process-test bean
- worker claimed it, handed it off, and appended process observations
- the body documents friction, tooling gaps, hand-off clarity, and workflow-rule gaps
- a follow-up item was created and recorded

No product-code or test proof was required for this bean by design.
