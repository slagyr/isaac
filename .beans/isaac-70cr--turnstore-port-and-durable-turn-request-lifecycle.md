---
# isaac-70cr
title: 'TurnStore: a durable record and stable id for every turn, queue-only submission, idempotency keys'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-28T02:36:51Z
parent: isaac-q3u3
blocked_by:
    - isaac-xoqn
    - isaac-ey7a
---

Likely repo: **isaac-agent**. Design: Micah + planner, 2026-09-27 (revised after isaac-xoqn landed).

## What exists (2026-09-27)

- One queue: `isaac.turn.queue`, records under `turns/held` — only for requests held by a pool or waiting on a busy session (xoqn, with coalescing). A turn that runs immediately never gets a record; while running it has only a session turn marker; nothing is kept once it finishes.
- **Weather is already done** (`drive/weather.clj`, isaac-f3hq): provider failures park with a marker, a sweep re-drives, an auth park posts attention at once. Nothing for this bean to add there.
- Every front door runs the turn inline unless a pool or busy session holds it; nothing can queue a turn without running it.

## Decisions (2026-09-27, Micah + planner)

1. **Every submission gets a durable record and a stable turn id the moment it is accepted.** States: queued, held, waiting, running, finished (outcome ok / error / cancelled / dropped). Finished records are kept (retention decided on isaac-d6pw). The turn id is the request id — one id throughout.
2. **TurnStore port** extracted from the queue, file store as the first adapter: `turns/<id>.edn` for every state (replaces `turns/held`). Not merged into `SessionStore`.
3. **Queue-only submission:** `isaac prompt --queue` prints `queued: <id>` and returns without running; Hail and Foreman use the same call in-process. A plain `prompt` still runs inline, but now leaves a record too.
4. **Idempotency keys live here** (moved from isaac-d6pw — Hail and Foreman both need them; they belong with storage). `prompt --queue --key <k>`; a repeated key returns the accepted turn (`already accepted`) and creates nothing.
5. **Atomic claim** — two processes or ticks never both start one queued turn. Proved by a unit spec.
6. **Restart:** a record left `running` with no live turn is reconciled with its session marker; the existing resume path re-drives it under the same turn id.
7. `turns list` shows unfinished turns; `turns list --all` adds finished ones with outcome.
8. **Merged waiting requests** keep their own ids; the later ones record `merged-into` the turn that ran, and each shows its outcome.
9. The drive owns retry and recovery (09-21 ruling stands); nothing in the store retries.

## Acceptance

Feature: `isaac-agent/features/turn/turn_store.feature` (new, 6 scenarios, `@wip` on main at ba59214). Remove `@wip`; all pass:

- [ ] `bb features features/turn/turn_store.feature` — `:20` queue + restart + same id, `:42` idempotency key, `:65` finished turns listed with outcome, `:82` crash-left running record runs once, `:110` merged requests share the outcome, `:130` dropped stays listed
- [ ] Unit spec: concurrent claims on one queued record — exactly one wins.
- [ ] Unit spec: the TurnStore port has a file adapter and an in-memory adapter for specs.
- [ ] Existing `turn_queue.feature`, `waiting.feature`, `resume_queue.feature`, `weather_suspend.feature` stay green (held records move from `turns/held` to `turns/<id>.edn`; update steps that seed `turns/held`).
- [ ] `bb verify` green; version bump.

Serialized after isaac-ey7a (both rewrite `turn/queue.clj`). Candidate selection is isaac-l3vb; origin, `turns show`, and `turn_get` are isaac-d6pw.

feature-baseline: isaac-agent ba5921425d3ed889e1b472be49afa9ed04a599a7
feature-blob: isaac-agent features/turn/turn_store.feature 5b50a2dbc53ce829df12be89c2dc1e5021e48335
