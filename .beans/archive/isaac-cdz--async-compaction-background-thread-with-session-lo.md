---
# isaac-cdz
title: "Async compaction: background thread with session locking"
status: completed
type: feature
priority: normal
created_at: 2026-04-16T16:16:45Z
updated_at: 2026-04-16T22:44:21Z
---

## Description

Compaction runs in a background thread without blocking the active turn. A per-session atom tracks in-flight compactions as {:future f :lock obj}. The turn thread checks for the lock before appending to the transcript. Only one compaction per session at a time.

## Design

- Single atom: {session-id -> {:future f :lock obj}}
- No entry in the map = no compaction, no locking overhead
- Entry exists = compaction in-flight, turn thread acquires lock before append
- Compaction thread acquires lock only for the final splice
- On crash, atom dies, no stale state
- Compaction triggered at start of each prompt when threshold exceeded and no compaction in-flight

## Depends on
- isaac-3jg (slinky compaction strategy) for partial tail compaction

## Acceptance criteria
- Async compaction does not block the active turn
- Turn appends coordinate with splice via per-session lock
- Only one compaction per session at a time
- Second turn during in-flight compaction skips starting another
- Crash recovery: no stale in-flight state after restart

Feature: features/session/async_compaction.feature

## Notes

Implemented async compaction with per-session in-flight tracking and transcript locking. Full bb spec passes. Isolated features/session/async_compaction.feature and features/session/compaction_strategies.feature pass. Closure is blocked by conflicting transcript-order expectations in features/context/compaction.feature versus the session compaction features.

