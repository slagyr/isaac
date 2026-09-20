---
# isaac-dr1k
title: 'Hail: do not bind a second work turn for a bean-id already in flight on that session'
status: draft
type: bug
priority: high
tags:
    - hail
created_at: 2026-09-20T20:13:09Z
updated_at: 2026-09-20T20:13:09Z
---

Split from isaac-1zkz (duplicate-dispatch conflict, 2026-09-20). Do not reopen isaac-1zkz for this.

Hail `af220fb2` was still executing on isaac-work-1 when hail `f01037ca` dispatched the **same bean-id** (`isaac-1zkz`) to the **same session**. Two `claude --print` turns ran concurrently in `~/agents/isaac/work-1/isaac-google-1zkz` and overwrote each other's files (`component.clj`, `google_steps.clj`, `tenants.feature`) within seconds. The second turn stood down (restored its own files since `a38b37b`, left the first turn's in-flight edits, `bb spec` 131/0).

Related but distinct from **isaac-3wiu** (recovery rebound a work hail into ad-hoc session `2026-06-29-1749-iaqu` by crew, not by band). This bean is **in-band duplicate bind**: a second hail for an in-flight bean-id to a session that already has a live turn on that bean.

## Observed

- First hail: `af220fb2` bound isaac-work-1, later rebound to `2026-06-29-1749-iaqu` (3wiu).
- Second hail: `f01037ca` same `{:bean-id "isaac-1zkz"}` while the first turn was still live.
- Worktree clobber in `isaac-google-1zkz`.

## Wanted

Do not dispatch a bean-work hail to a session that is already running a turn on that bean-id. If the bound session is in-flight, the delivery waits (pending) or routes to another free band session — never a second concurrent turn on the same worktree.

## Acceptance (draft — scenarios at promotion)

A hail whose bound session already has an in-flight turn for the same `:bean-id` is not started there. Scenario: two work-band hails with the same bean-id; the second does not bind until the first turn ends.

Do **not** recut isaac-1zkz product. Do **not** absorb isaac-3wiu (wrong-session recovery).
