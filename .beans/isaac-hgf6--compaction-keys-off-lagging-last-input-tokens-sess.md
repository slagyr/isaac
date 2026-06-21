---
# isaac-hgf6
title: Compaction keys off lagging :last-input-tokens — session runs over the hard context window
status: completed
type: bug
priority: high
tags: []
created_at: 2026-06-21T00:16:55Z
updated_at: 2026-06-21T00:26:46Z
---

Carved from isaac-twbz RC1 (per the data, twbz's "compaction never fires" is
wrong — it fires, but can't keep the session under the model's hard window).

## Observed (zanebot 0.1.6, session tidy-comet, crew marvin)
- `:compaction-count 37`, `:compaction-disabled false` — compaction DOES run.
- `:last-input-tokens 310778` vs context-window 278528 — the live context sits
  **~32k OVER the hard window**, yet requests still ship (422 msgs / 1.1 MB,
  ~2s to serialize, full body re-sent every turn).

## Root cause — decision metric is lagging
`isaac.session.compaction/should-compact?` (compaction.clj:28) and the turn-loop
checks (drive/turn.clj:514 `run-compaction-check!`, :474 no-progress guard) all
key off **`:last-input-tokens`** — set from the PREVIOUS LLM response's usage, not
a direct measure of the transcript/request about to be sent. Consequences:
- Triggers a turn late; a single big tool result pushes the real request well
  past the hard window before the lagging counter reflects it.
- The no-progress guard (`>= updated-total prompt-tokens` → `:compaction-stopped
  :no-progress`) compares the same lagging field, so compaction can't measure its
  own effect and may bail, leaving the session over-window.
- Trigger point is `0.8 × window` (default-threshold 0.8, context.clj:52) but the
  ship size is unbounded vs the hard window → 310k on a 278k model.

## Fix direction
- Base should-compact? / progress on a DIRECT estimate of the current transcript
  (or the request being built), not `:last-input-tokens`.
- Enforce staying under the model's hard window (compact until the built request
  fits), not just crossing a fraction of it.

## Acceptance
- A session whose live transcript exceeds the context window compacts BEFORE the
  request is built, so the outbound request fits the window (no 32k-over ships).
- Decision/progress no longer depend on `:last-input-tokens`.
- No-progress guard measures actual transcript reduction.

## Relationships
- Source: isaac-twbz (RC1). Sibling RC2 (per-turn re-resolution) overlaps the now-
  completed isaac-m14k.
- Related: isaac-92h (mid-turn checks during tool loops — complementary), isaac-5xx7
  (threshold/head as % — config units, separate).


## Handoff (work-3)

- **Fix:** `isaac-agent` `20fb5dd` — compaction decisions use `estimate-prompt-tokens` (live transcript via `prompt.builder/build`) instead of `:last-input-tokens`.
- **API:** `should-compact?` now takes `estimated-tokens` as first arg.
- **Verified:** `bb spec` isaac-agent (1049 examples).

## Verification Notes

- Verification passed on fetched GitHub `isaac-agent` `20fb5dd`, not the stale local mirror.
- `bb spec` passed on that head: `1049 examples, 0 failures`.
- The compaction decision path now uses a live prompt estimate in [src/isaac/session/compaction.clj](/Users/micahmartin/agents/verify/isaac-agent/src/isaac/session/compaction.clj:29) and [src/isaac/drive/turn.clj](/Users/micahmartin/agents/verify/isaac-agent/src/isaac/drive/turn.clj:503), not lagging `:last-input-tokens`.
- The new coverage is directly on the reopened behavior:
  - [spec/isaac/session/compaction_spec.clj](/Users/micahmartin/agents/verify/isaac-agent/spec/isaac/session/compaction_spec.clj:58) proves `should-compact?` keys off the live estimate and that `estimate-prompt-tokens` derives from the current transcript.
  - [spec/isaac/drive/turn_spec.clj](/Users/micahmartin/agents/verify/isaac-agent/spec/isaac/drive/turn_spec.clj:379) proves compaction progress/no-progress now measures post-compaction estimated prompt size rather than stale `:last-input-tokens`.
