---
# isaac-4erp
title: Running token tally replaces full-transcript chars/4 estimates
status: draft
type: task
priority: high
tags:
    - agent
    - performance
    - compaction
created_at: 2026-09-15T17:12:15Z
updated_at: 2026-09-15T17:12:15Z
blocked_by:
    - isaac-vfg8
---

## Problem

Isaac decides compaction and context exhaustion from a chars/4 estimate of the whole prompt (`isaac.llm.api.protocol/estimate-tokens`), recomputed from disk several times per cycle, even though every provider response already reports real token usage.

- `maybe-mid-turn-compact!` (`drive/turn.clj` ~1127) runs `compaction/estimate-prompt-tokens` three times after every tool batch (`before`, inside `run-compaction-check!`, `after`). Each reads the current transcript segment (`read-transcript-raw` → whole `current.ednl`) and builds the full prompt to count characters.
- `log-token-drift!` (`drive/turn.clj` ~314) re-reads the active transcript after every response to compare stamped per-entry `:tokens` against the provider's prompt tokens, storing `:token-drift-ratio`.
- chars/4 undercounts badly on code/EDN-heavy content: `isaac-work-1`'s ratio is 2.47. The gauge (`compaction/context-gauge`) multiplies the estimate by that ratio (clamped 1–3) and takes the max with `:last-input-tokens`, so compaction fires on a calibrated guess.

## Proposal: running tally

After each response we already store real usage (`:last-input-tokens`, per-turn input/output tokens; `drive/turn.clj` ~266, ~282–304). The next prompt is exactly: previous prompt tokens + that response's output tokens + tokens of entries appended since (tool results, user message, checkpoint nudge). Only the delta needs estimating, and it can be stamped once when the entry is saved (entries already carry `:tokens`).

- Gauge = last prompt tokens + last output tokens + Σ `:tokens` of entries appended since that response.
- Drop the full-transcript estimates in `maybe-mid-turn-compact!` and `run-compaction-check!`; drop `log-token-drift!`'s rescan and `:token-drift-ratio` (a tally has no drift to calibrate).
- After a compaction there is no provider count for the new prompt until the next response: bootstrap from the summary's stamped tokens (or one estimate), then return to the tally.
- Per-entry `:tokens` stamps stay (the `:slinky` strategy and chunking use them).

## Open questions (not decided)

- Does output reasoning count toward the next prompt? (Responses API: reasoning items are not replayed unless stateful — check per API.)
- Session start / first request of a turn (no prior usage): estimate once, or use the session's stored last prompt tokens?
- Whether the stamped delta should use chars/4 or a per-provider calibrated ratio.

## Depends on

- The cycle-timing bean (measure before and after).

## Acceptance (draft — scenarios TBD)

- A cycle with no compaction reads the transcript file zero times for token accounting (timing events prove it).
- The compaction trigger fires on the tallied count: a fixture whose provider usage crosses `threshold × window` compacts even though chars/4 of the transcript would not.
- `:token-drift-ratio` no longer exists in session entries (one-time check).
