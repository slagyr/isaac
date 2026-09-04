---
# isaac-nrak
title: 'Verified beans never land on main: make merge-to-main part of the verify pass'
status: draft
type: task
priority: high
tags:
    - process
    - orchestration
created_at: 2026-09-04T00:36:46Z
updated_at: 2026-09-04T00:36:46Z
---

Five times in one week a bean passed verify and its code stayed on `bean/<id>` until a planner noticed, sometimes days later, sometimes after main had moved and the merge conflicted:

| bean | verified | found unmerged | cost |
|---|---|---|---|
| isaac-x2up | 2026-09-02 | planner merged during the 0.1.40 train | — |
| isaac-5nxf | 2026-08-31 | 2026-09-03; needed the jarr integration bean + 2 verify rounds | 3 days, 1 extra bean |
| isaac-q34y | 2026-09-03 | same day; planner merged | — |
| isaac-vuto | 2026-09-03 | same day; planner merged | — |
| isaac-l3ps (worksite W1) | 2026-08-26 | **2026-09-03** — isaac-worksite main had NO code for 8 days; main had taken a foundation bump so the merge conflicted on deps/bb pins; the branch's agent pin (10093b4) predated the turnstile namespace | 8 days, silent |

Nobody owns the merge. hail-bean-work ends at "tag unverified + hail verify"; hail-bean-verify ends at "status completed"; the pin-and-deploy train assumes main already has it. Result: `completed` does not mean "on main", and `isaac modules` pins are taken from main.

## Decision for Micah

Who merges, and when:
- **A (recommended): the verifier merges on pass.** It has the checkout, just ran the gates on the branch, and knows the exact SHA it approved. Fast-forward or merge commit onto origin/main, re-run the module's targeted gate on the merged head if the merge was not a fast-forward, note the main SHA on the bean, THEN mark completed. A conflict is a verify FAIL back to work ("rebase onto main").
- B: the worker merges before handoff and verify runs against main. Cleaner gate semantics ("verify the merge target"), but a red verify then leaves broken code on main.
- C: workers push to main directly (what jarr/lqbc did today). Skips review of the merge itself.

Also: `completed` should be unreachable without a `main-sha` line on the bean — a mechanical check the verify skill can enforce.

## Acceptance (after the decision)
- slagyr/orchestration hail-bean-verify skill: the pass path merges and records `main-sha`; conflict → fail with the rebase instruction.
- hail-bean-work skill: the unverified handoff states the branch and its base SHA.
- Exhibit check: no completed bean in isaac/.beans since the change without a main-sha line.
Related: isaac-jndk (full-gate rule), isaac-2bni / cancel flake (gate trust).
