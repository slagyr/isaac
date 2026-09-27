---
# isaac-70cr
title: TurnStore port and durable turn-request lifecycle
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-27T22:33:11Z
---

Likely repo: **isaac-agent**. Design: Micah + planner, 2026-09-27. Build on the completed held-turn queue (isaac-ohsy); coordinate with in-progress busy-session queue work (isaac-xoqn).

## Contract to plan

- Agent owns one submission/admission lifecycle for turn requests from CLI, comms, Foreman, and Hail. Persist the unresolved request and its stable ID; build the charge when the request is admitted.
- Introduce a separate `TurnStore` port for durable queued, held, claimed/running, and terminal records. File-backed storage is the first adapter; a database adapter is possible later, not part of this bean. Do not merge this port into `SessionStore`.
- Submission with an existing source/request ID is idempotent. Claiming one request is atomic. On restart, recover queued/held requests and reconcile running records with the existing session turn markers and transcript-repair path before re-driving.
- Persist lease identities needed for recovery, never live connections, providers, comm channels, or resolved charges. Expose status/list/drop through the existing `turns` operator surface.
- Preserve the current queue's tick and release-token wake behavior; define one authoritative owner for retry/recovery to prevent duplicate turns.

## Scenario plan to review

1. A submitted request survives restart and runs after capacity becomes available.
2. Two submissions with the same source ID yield one request and one turn.
3. Concurrent claimers start a request once; a crash at claim is reconciled on restart.
4. Terminal outcome and dropped-request status remain queryable through the store.

Draft until runnable scenarios are committed and baselined. This bean is the storage and lifecycle seam; candidate selection is isaac-l3vb.
