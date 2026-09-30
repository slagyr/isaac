---
# isaac-e9jl
title: Turn queue runs one turn at a time across the server
status: completed
type: bug
priority: critical
created_at: 2026-09-30T00:04:30Z
updated_at: 2026-09-30T00:57:56Z
---

Likely repo: **isaac-agent**. Critical: live on zanebot since the 2026-09-29 train (agent 505a60e).

## Why

The turn queue runs one turn at a time across the whole server.
`isaac.turn.worker/run-one-pass!` claims a record and calls
`process-record!`, which calls `bridge/dispatch!` and blocks until the turn
ends; `tick!` is single-flight (`tick-state*`). Every hail-delivered turn
now goes through the queue, so one long worker turn stalls every other
session. Field, 2026-09-29 23:56Z: isaac-0lb7's turn for idle session
isaac-work-4 sat `queued` behind a Scrapper turn (b0ae2ae6) running since
23:48 on another session. Old Hail delivered in parallel.

## Design

- The tick claims and **starts** each runnable turn, then moves on. The
  turn runs on its own thread (the same executor style the bridge already
  uses for async turns, or a future); `process-record!`'s bookkeeping
  (outcome, merged ids, live-comm cleanup, held→:held on a hold) runs when
  that turn ends.
- Per-session serialization stays: `:address-busy?` and the waiting-room
  `in-flight?` check are unchanged. Only different sessions overlap.
- A claimed record must not be claimed again by the next tick while its
  turn runs (the claim already moves it to :running; keep it that way).
- The finished turn's release still nudges the queue (release path).
- Keep a throwing pass from stranding `tick-state*` (isaac-2lc4).

## New steps (thin wrappers over `isaac.llm.api.grover`)

- `Then within {n:int} seconds session {s:string} is waiting on the model` — polls `grover/waiting?`.
- `When the model releases session {s:string}` — `grover/release-wait!`.

## Acceptance

- isaac-agent `features/turn/turn_store.feature` — "queued turns on different sessions run side by side"
- `bb features features/turn` stays green (waiting room + pools + 2lc4 tick scenario).

feature-baseline: isaac-agent 3f3cabb2d4be8c74e67f281e6bace4084dfe0fe1
feature-blob: isaac-agent features/turn/turn_store.feature df09889c67cf4c959ba3959b42db544c554cb85e 174

## Summary of Changes

The queue tick claims each runnable turn and starts it on its own thread (bound-fn + future), then moves on; process-record!'s bookkeeping runs when that turn ends. Per-session serialization unchanged. Test-only worker/await-idle! drains started turns; finished futures leave the registry (planner fix on review: the registry otherwise grew forever in production). New steps: 'session X is waiting on the model', 'the model releases session X'. Main 38e2789; deploy hotfix a6231af = 505a60e + this (branch hotfix/e9jl). bb spec + jvm-spec 1827/0, features 917/0; gate PASS.
