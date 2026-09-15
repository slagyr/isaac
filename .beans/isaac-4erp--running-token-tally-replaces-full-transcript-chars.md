---
# isaac-4erp
title: Running token tally replaces full-transcript chars/4 estimates
status: in-progress
type: task
priority: high
tags:
    - agent
    - performance
    - compaction
created_at: 2026-09-15T17:12:15Z
updated_at: 2026-09-15T17:55:16Z
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

## Decisions

- Decision (2026-09-15, Micah): gauge = last prompt tokens + that response's output tokens + stamped `:tokens` of entries appended since. No full-transcript estimate per cycle; no drift ratio.
- Decision (2026-09-15, Micah): right after a compaction the tally restarts from the stamped counts of the post-compaction history (summary + kept entries + appended since); the pre-compaction provider count is never reused. The next response's usage takes over.
- Decision (2026-09-15, Micah): output reasoning tokens are excluded from the next prompt when the API does not replay them (e.g. non-stateful Responses). Covered by specs, not a scenario.
- Decision (2026-09-15, Micah): the appended delta is stamped chars/4 at write time (existing per-entry `:tokens`); no calibration ratio.
- Session entries must carry the last response's output tokens alongside `:last-input-tokens` so a new turn can continue the tally.

## Depends on

- isaac-vfg8 (cycle timing) — measure before and after.

## Scenarios

Scenarios approved 2026-09-15 (Micah). Committed `@wip` in isaac-agent `515a40b`; no new steps.

`features/session/token_accounting.feature`
- `:204` compaction plans from stamped counts, not a stringified guess (isaac-4erp) — **replaces** `:39` (same setup; the Then asserts `:session/compaction-analysis :tokens-before` instead of `:session/token-drift`)
- `:231` the gauge is the last prompt plus its output plus the entries appended since (isaac-4erp) — **new**
- `:250` entries appended since the last response can cross the threshold and compact before the next cycle (isaac-4erp) — **new**
- `:288` after a compaction the gauge counts the stamped history, not the stale provider count (isaac-4erp) — **new**

At landing:
- Delete `:39` "compaction plans from stamped counts, not a stringified guess" (replaced by `:204`).
- Delete `:65` "provider prompt tokens are reconciled against stamped counts and drift is logged".
- Delete `:132` "the gauge is calibrated by the last observed drift ratio".
- Keep `:18`, `:81`, `:116`, and `:170` unchanged.
- Remove `@wip` from `:204`, `:231`, `:250`, `:288`; strip the "(isaac-4erp)" suffix from `:204`'s title.
- Feature description contract item (3) becomes: "the gauge is a running tally — the last prompt tokens plus that response's output tokens plus the stamped tokens of entries appended since".
- `:250`'s manifest file is sized so the stamped tool result (~100 tokens) pushes 700 + 20 past 800; retune the text if stamping differs, keeping the crossing.

## Acceptance

```
ISAAC_GIT=1 bb features features/session/token_accounting.feature
ISAAC_GIT=1 bb features features/session/cycle_timing.feature
bb ci
```

One-time checks (not scenarios):
- `git grep -n "token-drift-ratio\|calibration-ratio\|log-token-drift" -- src` in isaac-agent is empty.
- With isaac-vfg8's timing events, a cycle with no compaction shows zero `:session/transcript-read` and zero `:session/token-estimate` events between `:tool/result-persisted` and `:turn/after-tools`.
- On zanebot after deploy: the `tool/result → chat/stream-request` median for an `isaac-work-1` cycle drops well below the 2.3s measured on 2026-09-15 (record the number here).
