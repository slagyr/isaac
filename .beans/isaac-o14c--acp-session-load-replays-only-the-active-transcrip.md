---
# isaac-o14c
title: ACP session load replays only the active transcript (post-compaction head)
status: in-progress
type: feature
priority: normal
created_at: 2026-07-08T20:32:13Z
updated_at: 2026-07-08T20:36:18Z
parent: isaac-zt4h
---

## Goal

ACP session load replays only the **active transcript** (post-compaction head), not the full session history. Loading a long-lived session in Toad currently streams every entry since session birth — megabytes of `session/update` notifications for old crew sessions — because replay reads the wrong store view.

## The change (small and precise)

`isaac-acp/src/isaac/comm/acp/server.clj` — `attach-session-result!` calls `store/get-transcript` (whole session file). Switch to `store/active-transcript` (reads from `:effective-history-offset` when the session has compacted; falls back to the full file when it hasn't — see `isaac-agent/src/isaac/session/store/impl_common.clj:517-525`). This covers both `session/load` and the pre-bound `--session` attach path (isaac-d84z), which share `attach-session-result!`.

## Care points

- `tool-results-by-id` in the replay is built from the same transcript passed in; with a truncated view, entries referencing pre-offset tool ids must not blow up — replay them as toolCalls without results (or skip), never crash the load.
- The compaction summary entry at the offset boundary should replay (it's the context the user needs to make sense of the head).
- Sessions with no compaction offset must replay exactly as today.

## Scenarios (worker writes these — required coverage)

1. Session with a compaction offset: `session/load` replays only entries from the offset (summary first), none from before it.
2. Session without compaction: full replay, unchanged behavior.
3. Truncated view containing a toolCall whose result landed pre-offset: replay completes without error.

## Acceptance

- [ ] Scenarios above green in isaac-acp features
- [ ] Existing d84z replay scenarios still green (attach path shares the fix)
- [ ] One-time: load a large compacted zanebot session in Toad — load time drops from full-history replay to head-only
