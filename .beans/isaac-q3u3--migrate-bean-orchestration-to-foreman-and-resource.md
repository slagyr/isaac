---
# isaac-q3u3
title: 'Milestone: bean orchestration on Foreman and resource pools'
status: draft
type: milestone
priority: normal
created_at: 2026-09-27T22:33:12Z
updated_at: 2026-09-27T23:09:08Z
---

Design: Micah + planner, 2026-09-27. **Milestone**: bean orchestration runs on Foreman + Agent turn admission + resource pools, replacing worker-to-worker hail choreography.

## Migration beans (children, in order)

1. isaac-q6fj — machine config and happy path, running alongside the hail path.
2. isaac-1rtr — failure paths: repair loop, busy worksites, restart mid-handoff, missing signal, auth outage, human escalation.
3. isaac-20gd — cut over and retire band choreography.

## Upstream chain

- Now: finish isaac-xoqn (busy-session waiting room); isaac-tjjm (Foreman event intake) has no blockers and can run in parallel.
- isaac-70cr (TurnStore port + drive-owned recovery) after xoqn.
- isaac-ey7a (pool contract, turn leases, non-blocking acquire) → isaac-l3vb (session + pool selection; sessions independent of resources).
- Then in parallel: isaac-npmp (Worksite pool), isaac-lr8h (Foreman `:turn` action), isaac-ex4q (Hail hands off to Agent).
- Deferred, not on the path: isaac-kxqj (tool-call-scoped leases).

Design record: isaac-tdgt § "Architecture revision (2026-09-27)".
