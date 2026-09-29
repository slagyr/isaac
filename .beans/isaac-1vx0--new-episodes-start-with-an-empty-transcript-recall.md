---
# isaac-1vx0
title: New episodes start with an empty transcript (recall only)
status: todo
type: bug
priority: high
created_at: 2026-09-29T14:31:12Z
updated_at: 2026-09-29T14:31:12Z
---

Ruling: Micah, 2026-09-29. **A new episode starts with an empty transcript.** The only thing in it is recall for the prompt that opened it.

## Problem

When the open episode is cold (last transcript entry older than the TTL, default 60 min), the next user message calls `ensure-open-container!` in `isaac-episodes/src/isaac/session/policy/episodes.clj`. It closes the old episode and opens a successor on the same session id, then `inject-on-open!` appends recall. It never truncates the session transcript, so the new episode carries every old entry. It also leaves `:last-input-tokens` on the session untouched (only `compact-chain!` zeroes it). A stale gauge from the previous episode then trips compaction on the first turn of the new one.

Evidence: yopp, session `acp-2026-09-28-1645-a6c4`, 2026-09-29 13:51Z. It sat idle ~20h. The first message ran `session/compaction-check` with gauge 930,470 (the previous day's last turn) on a 43-entry, ~20k-token transcript. It compacted (5m48s) *before* the episode opened (13:55Z). The new episode only looked nearly empty because that compaction had just shrunk the transcript.

## Wanted

- Opening an episode on a cold session (`:chained` action from the TTL path) closes the old episode against its full transcript, same as now: gist, scenes, recall index.
- The session transcript then holds only the recall block for the triggering prompt (plus the new user message). No old entries carry over.
- The session gauge is zeroed on that open (`:last-input-tokens` 0 and the tally cursor reset), so the first turn of a new episode never compacts.
- The cold check runs before the compaction check on a turn. A cold session never compacts; it chains.
- The old transcript is not lost. It stays readable through the closed episode (chronicle / episode store), the same way a compacted-away transcript is.
- Warm path unchanged.

## Acceptance

- Scenario: cold episode + stale large gauge → new user message → no compaction events, one `episodes/opened` (chained), transcript = recall + new message, `:last-input-tokens` 0 before the provider call.
- Scenario: cold episode, recall finds nothing → transcript holds only the new user message.
- Scenario: warm episode → transcript untouched (regression guard).
- Scenarios need writing + `bb bean-gate baseline` before this goes todo.

## Likely repo scope

`isaac-episodes` (ensure-open-container!, recall inject), maybe `isaac-agent` (turn ordering: cold check before `should-compact?`; a session-store truncate/splice without a summary).

feature-baseline: isaac-episodes f5e87dee78204250d86a330e706f5b1c0d0ce2e3
feature-blob: isaac-episodes features/episodes/live.feature bf841d3b717f37d832de14f2996c6e54b2b2b827 112
