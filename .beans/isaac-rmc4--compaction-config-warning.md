---
# isaac-rmc4
title: Compaction config warning
status: draft
type: feature
created_at: 2026-05-14T14:39:24Z
updated_at: 2026-05-14T14:39:24Z
---

**Status: draft — needs your refinement.**

Compaction has enough moving parts (`:strategy`, `:threshold`, per-model context windows, `:consecutive-failures`) that misconfiguration silently degrades a session. Surface the misconfig as a warning the user actually sees.

## Open questions before this is workable

- Which misconfig(s)? Candidates:
  - `:threshold` larger than the model's effective context window
  - `:strategy` not registered / unknown
  - `:consecutive-failures` ratcheting up turn after turn (compaction failing repeatedly)
  - Compaction disabled on a long-running session that's accumulating tokens
- Surface: log/warn, session metadata flag, `/status` output, all of the above?
- One-shot vs. recurring: warn once per session boot, or each time a turn crosses a threshold?

## Prior art

- `isaac-iozn` (completed): compaction budget was inverted; grover stub didn't enforce context window. That was the bug behind the symptom; this bean is about *surfacing* analogous symptoms early.
- `tidy-comet`'s session shows `:compaction {:consecutive-failures 0, :strategy :slinky, :threshold 250675}` — useful shape for what's visible today.

## TODO before promoting to `todo`

- [ ] Pick which misconfig(s) the first cut covers
- [ ] Decide warning surface
- [ ] Decide cadence (once / per-turn / per-threshold-cross)
