---
# isaac-qpp4
title: 'Verify loop: escalate to planner after N consecutive verify-fails instead of infinite worker bounce'
status: todo
type: feature
priority: normal
created_at: 2026-07-03T20:42:11Z
updated_at: 2026-07-05T01:49:54Z
---

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
