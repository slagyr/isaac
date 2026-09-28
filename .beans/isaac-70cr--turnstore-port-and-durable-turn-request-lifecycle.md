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

## Contract conflict (2026-09-27)

The baselined feature uses step-table capture DSL in `Then the stdout matches:` (lines 25, 39, 48, 62, 143, 150): `#"[a-z0-9]+":turn-id`, `#turn-id`, `#"[a-z0-9]+":waiting-id`, and `#waiting-id`. That step in `isaac-foundation/spec-support/src/isaac/foundation/cli_steps.clj:430` calls `extract-patterns` and `re-find (re-pattern pattern)` on the raw row. It does **not** apply `isaac.foundation.step-tables/match-value` or capture refs; only `stdout-json-contains`/`stdout-edn-contains` invoke the step-table DSL. The first `queued:` assertion passes as a regex by accident, but the later `#turn-id` assertion expects the literal string `#turn-id` in the table, not the captured id; feature run is red (4 failures/6 examples). The scenario requires an id to be captured and re-used by subsequent steps. It cannot be fixed by editing the baselined feature as a worker. Planner must amend/rebaseline this scenario or provide a supported capture step. The step also uses literal `#waiting-id` in `turns drop`, which has no registered interpolation (only `#held-id` is interpolated).

Worker checkpoint (in `isaac-agent-70cr`, `bean/isaac-70cr`): TurnStore port with memory/file adapters and atomic in-process claim unit spec green (`bb spec --focus spec/isaac/turn/store_spec.clj`), queue/prompt/worker/CLI integration in progress and acceptance red (`bb features features/turn/turn_store.feature`, 4 failures). No green full suite or gate; no landing attempted. Resume at `spec/isaac/turn/queue_steps.clj:43` for capture/interpolation once planner resolves the contract. The `@wip` removal is the only feature edit.


## Planner adjustment (2026-09-28, prowl@isaac-plan) — stdout matches is a regex

Do not build a capture DSL into `Then the stdout matches:`. That step runs each row as a raw regex (`cli_steps/stdout-matches`). `#"[a-z0-9]+":turn-id` and `#turn-id` inside that table are not captures. Only `stdout-json-contains` and `stdout-edn-contains` use the step-table DSL.

The one capture that already works is the held-id path: a line `held: <id>` is stored, and a later command interpolates `#held-id`. Extend that same hook. Do not add a second interpolation name.

### Contract (isaac-agent main `8c36e43`, file still `@wip`)

- `prompt --queue` prints `queued: <id>`. The postflight stores that id as `:turn-id`, the same way it stores `:held-id` from `held:`.
- `turns show #turn-id` prints the id, session, state, and outcome as plain lines. The scenario matches those lines as regex rows. Scenarios 1 and 2 use this. They no longer match a column table.
- `turns list` / `turns list --all` assertions are regex rows (`Status\?`, `ok`, `error`, `tide-9`, `one`, `two`, `three`, `merged-into`, `dropped`). Not column tables.
- A waiting turn's list prints `waiting: <id>`. The postflight stores that id as `:held-id`. The drop command is `turns drop #held-id`. There is no `#waiting-id`.

`turns show <id>` is in this bean. It prints the record's id, session, state, and outcome, one per line. isaac-d6pw may add origin and a richer show later. Do not wait for it.

### Re-baselined

    feature-baseline: isaac-agent 8c36e43998b6dbae9c7b7d5af46263f39ace0a0a
    feature-blob: isaac-agent features/turn/turn_store.feature ee6be659a6e7eb4a1bfe313279b204036badd8d8

The file is `@wip`, so the blob names no lines. All six scenarios are this bean's. Drop the file `@wip` only after they pass.

### Worker now

1. Rebase `bean/isaac-70cr` onto `8c36e43`. Keep the implementation (`3cff743`). Feature diff may only drop the file `@wip`.
2. Extend the existing postflight: `queued: <id>` → `:turn-id`, `waiting: <id>` → `:held-id`. Interpolate `#turn-id` and `#held-id` in later `isaac` commands. Do not implement table-cell capture.
3. `bb features features/turn/turn_store.feature` green, then `bb bean-gate verify isaac-70cr` exit 0, then land. Do not land while the file is `@wip` on main.

This note resets the verify-fail counter.

feature-baseline: isaac-agent 8c36e43998b6dbae9c7b7d5af46263f39ace0a0a
feature-blob: isaac-agent features/turn/turn_store.feature ee6be659a6e7eb4a1bfe313279b204036badd8d8
