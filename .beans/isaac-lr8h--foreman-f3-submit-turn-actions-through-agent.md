---
# isaac-lr8h
title: 'Foreman F3: submit turn actions through Agent'
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:12Z
updated_at: 2026-09-27T23:09:08Z
parent: isaac-q3u3
blocked_by:
    - isaac-l3vb
    - isaac-tjjm
    - isaac-70cr
---

Likely repo: **isaac-foreman**, consuming the Agent turn-submission contract. Design: Micah + planner, 2026-09-27. Depends on durable event intake and candidate/resource admission.

## Contract to plan

- Add native `:turn` action execution. A handled transition records the pending action, submits an idempotent Agent turn request with session frequencies and `:resource-pools`, then records the returned request ID.
- Submission failure leaves an inspectable pending action for retry. A retry cannot create a second turn for the same transition/action identity.
- Turn outcome observations return through Foreman's event intake and drive table transitions, including success, error, death, and completion without the expected crew signal.
- Foreman decides workflow state and which logical pool is requested; it never selects a session or directory and does not depend on Hail. Optional `:hail` action remains a separate messaging extension.

## Scenario plan to review

1. One transition submits one durable turn request and records its ID.
2. A repeated action submission creates no duplicate turn.
3. No eligible resource holds the request; resource release starts it and the outcome advances the machine.
4. A turn that ends without the expected signal follows the machine's backstop row.

Draft until scenarios are committed and baselined. Cleanly supersede F1's recorded-but-unexecuted `:hail`-as-orchestration-action assumption where the table uses `:turn`.
