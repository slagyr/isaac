---
# isaac-1rtr
title: 'Bean orchestration on Foreman: failure paths'
status: scrapped
type: feature
priority: normal
created_at: 2026-09-27T23:09:08Z
updated_at: 2026-09-29T13:59:46Z
parent: isaac-q3u3
blocked_by:
    - isaac-q6fj
---

Likely repos: **orchestration** and **isaac-foreman** (machine rows only; engine behavior belongs to isaac-tjjm/isaac-lr8h). Second of three migration beans under milestone isaac-q3u3.

## Contract to plan

Exercise the bean machine's non-happy paths on the Foreman path. A duplicated event or turn request must never duplicate work.

## Scenario plan to review

1. Verification failure returns the bean to work; repair then completes it.
2. All worksite members busy: the work turn waits; a release starts exactly one pending turn.
3. Restart during a handoff leaves an inspectable machine state that resumes without a duplicate turn.
4. A turn that ends without the expected crew signal follows the machine's backstop row.
5. A provider-auth outage parks the turn with attention; the machine shows the bean held on infrastructure, not failed.
6. Human escalation: a held bean appears in `isaac foreman list --state held` with its owner.

Draft until scenarios are approved.


## Scrapped (2026-09-29) — merged into isaac-q6fj
Micah + planner: orchestration has no feature runner, so the migration becomes two ungated beans; the failure rows are part of the one bean-work machine (isaac-q6fj).
