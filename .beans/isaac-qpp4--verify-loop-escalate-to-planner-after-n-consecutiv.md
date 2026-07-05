---
# isaac-qpp4
title: 'Verify loop: escalate to planner after N consecutive verify-fails instead of infinite worker bounce'
status: completed
type: feature
priority: normal
created_at: 2026-07-03T20:42:11Z
updated_at: 2026-07-05T03:55:28Z
---

## Problem (observed on isaac-iqqz, 2026-07-03)

The hail-bean-verify skill returns a failed bean to the WORK band. If the worker cannot resolve the failure (e.g. acceptance requires operator-only steps, or a genuine conflict), it re-hands to verify unchanged and the bean bounces worker <-> verify indefinitely. isaac-iqqz failed verification 3x identically with no progress and no escalation.

## Desired behavior

After N consecutive verify-fails on the same bean (suggest N=2), the verifier (or worker) escalates to the PLAN band instead of bouncing again — the planner decides: rescope acceptance, split the bean, mark blocked, or human-help. The loop must be bounded.

## Design questions to settle

- Where the counter lives: a bean tag/field (verify-fail-count) vs. thread-length heuristic via hail_get on the thread.
- Who escalates: verifier on emitting the Nth fail, or worker on receiving the Nth return.
- Interaction with first-fail/return-to-same-session process-test beans (those intentionally fail once) — count only CONSECUTIVE fails with no bean-body change between them.

## Scope

orchestration repo: hail-bean-verify / hail-bean-work skills (+ possibly a bean field). Draft until design settled + scenarios reviewed.

## Acceptance (committed 2026-07-04) — behavioral, orchestration repo

Mechanism: the verifier appends a `## Verify fail (attempt N, <date>)` note to the bean body on each fail, and counts `## Verify fail` notes since the last `## Planner` note. Fewer than 2 -> work-band (normal rework). 2 or more -> **plan-band** (escalation). A `## Planner` note resets the count.

Implemented (orchestration `main`):
- hail-bean-verify/SKILL.md: append fail marker + count-since-planner-note + escalate-to-plan-band at N>=2; new `🆙 ... escalated to planner` notification.
- hail-bean-plan/SKILL.md: `## Planner` note documented as the reset marker (adjustment must change something real).
- test/verify-escalation.md: behavioral acceptance (fail x2 -> escalate to plan -> planner rescopes -> pass).

Acceptance = run test/verify-escalation.md through the live loop on the deployment:
- exactly two `## Verify fail` notes then one `## Planner` note in the bean body;
- the 2nd verify-fail's outbound hail targets the PLAN band (delivered hail records), not work;
- no infinite work<->verify bounce; bean completes after the planner rescope.

No gherkin step ledger — prose-skill work, tested behaviorally like happy-path / plan-review. N=2 is tunable; intentional first-fail process-test beans never trip it (single fail).


## Verified live (2026-07-04, zanebot, process-test isaac-na8b)

Ran test/verify-escalation.md end-to-end on the freshly-deployed stack:
- worker attempt 1 -> verify fail #1 -> back to work
- worker attempt 2 -> verify fail #2 -> ESCALATED to the plan band (delivered hail `band="isaac-plan"`), NOT a third worker bounce
- planner appended a `## Planner` rescope note (count reset) -> handback to work band
- worker satisfied rescoped acceptance -> verify turn 3 (0 fails since planner note) -> PASS -> completed

Escalation evidence: the 2nd verify-fail's outbound hail targeted `isaac-plan`; the 🆙 escalation notification fired. Deployed in orchestration skills (hail-bean-verify/plan) on zanebot.
