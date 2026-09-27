---
# isaac-70cr
title: TurnStore port and durable turn-request lifecycle
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-27T23:09:08Z
parent: isaac-q3u3
blocked_by:
    - isaac-xoqn
---

Likely repo: **isaac-agent**. Design: Micah + planner, 2026-09-27. Builds on the completed held-turn queue (isaac-ohsy). Blocked by the busy-session waiting room (isaac-xoqn), which changes the same queue.

## One queue, one port

Agent has exactly one turn queue: `isaac.turn.queue` (records under `turns/held`). This bean does **not** add a second store. It extracts the queue's persistence into a `TurnStore` port; today's `turns/held` files become its first (file) adapter. A database adapter is possible later, not part of this bean. Do not merge this port into `SessionStore`.

## Contract to plan

- Agent owns one submission/admission lifecycle for turn requests from CLI, comms, Foreman, and Hail. Persist the unresolved request and its stable ID; build the charge when the request is admitted.
- TurnStore records cover queued, held, claimed/running, and terminal states.
- Submission with an existing source/request ID is idempotent. Claiming one request is atomic.
- **The drive owns retry and recovery** (2026-09-21 ruling: turn errors are weather; the drive parks and resumes, and owns continuations). No other component retries a turn request.
- **Weather deferral with attention.** An infrastructure failure (auth expiry, provider 401/403, empty terminal response) parks the request instead of failing it. The parked request carries an attention reason, visible in `turns` status/list and emitted as a turn observation (Foreman and Hail may relay it to a human), and resumes automatically on recovery. Dead-lettering is for poison requests only. This moves the "hails never die" guarantee from Hail into Agent.
- **Coalesced requests.** When xoqn merges several waiting requests into one turn, each source request keeps its own record and ID; all point at the one turn and share its terminal outcome. Idempotency by source ID still holds for each.
- On restart, recover queued/held requests and reconcile running records with the existing session turn markers and transcript-repair path before re-driving.
- Persist lease release identities needed for recovery (isaac-ey7a), never live connections, providers, comm channels, or resolved charges.
- Expose status/list/drop through the existing `turns` operator surface. Preserve the current queue's tick and release-token wake behavior.

## Scenario plan to review

1. A submitted request survives restart and runs after capacity becomes available.
2. Two submissions with the same source ID yield one request and one turn.
3. Concurrent claimers start a request once; a crash at claim is reconciled on restart.
4. A turn that fails on provider auth parks with an attention reason and runs when the provider recovers, without a second submission.
5. Two coalesced requests each report the shared turn's terminal outcome.
6. Terminal outcome and dropped-request status remain queryable through the store.

Draft until runnable scenarios are committed and baselined. This bean is the storage and lifecycle seam; candidate selection is isaac-l3vb.
