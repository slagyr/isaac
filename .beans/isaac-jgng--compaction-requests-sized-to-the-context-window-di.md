---
# isaac-jgng
title: Compaction requests sized to the context window die 'closed' on chatgpt; cap per-request tokens and adapt chunk size on failure
status: todo
type: bug
priority: high
tags:
    - agent
    - compaction
created_at: 2026-09-04T16:29:12Z
updated_at: 2026-09-04T16:46:02Z
---

Repo: **isaac-agent** (`src/isaac/session/compaction.clj` chunk plan,
`src/isaac/drive/turn.clj perform-compaction!`).

## Problem

The chunk planner sizes summarization requests against the model's context
window: 90k-token histories on a 278k window are sent as ONE request
(`needs-chunking false`). On chatgpt those requests never answer — they die
"closed" after ~15 min (see the SSE-stall bean) — five attempts in a row on
2026-09-04 03:39–03:55Z, then `compaction-stopped :too-many-failures`, then
the turn ran to `context-exhausted` at 274k. The same history on Opus
(200k window) split into two chunks and succeeded. Repeated 2026-09-04
15:44–16:15Z at 48k tokens. Compaction is currently the single largest
consumer of worker wall clock on zanebot.

## Design (settle before todo)

- Cap the per-request summarization size independently of the window:
  `:compaction {:max-request-tokens N}` (proposed default 32k) — chunk
  whenever history exceeds it, regardless of `needs-chunking` by window.
- On a failed/stalled chunk, halve the chunk size and retry once before
  counting a consecutive failure (adaptive), so one bad provider day does not
  brick the session (companion to isaac-vrtb).
- Consider a dedicated summarization effort (low) — reasoning models at
  effort 7 may sit silent for the whole think; summaries do not need it.
  Open question for Micah: fixed low effort vs inherit.
- Do NOT change the trigger threshold here (that is config; tono-work-1's
  session-level 0.3 was hand-set).

## Acceptance (sketch)

Spec: a 90k-token history with `max-request-tokens 32k` on a 278k window
plans ≥3 chunks; a chunk failure with `:stream-stalled` retries at half size
before incrementing consecutive-failures. Feature: existing
`compaction_logging` / `compaction` scenarios stay green; one new scenario
shows chunked compaction under the cap with the Grover fixture.


## Decision (2026-09-04, Micah): fixed low effort for summarization

- Compaction requests run at a fixed low effort, independent of the session's
  effort: `:compaction {:effort 2}` (universal 0–10 knob; translated per API
  as usual, omitted for models with `:allows-effort false`). Configurable in
  the same compaction layers as `:threshold` / `:head`; code default 2.
- Same model as the session (no dedicated compaction model).
- Plus the two mechanical guards from the design: `:max-request-tokens`
  (default 32k) forces chunking regardless of window; a chunk that fails
  with `:stream-stalled` / "closed" retries once at half size before
  counting a consecutive failure.

## Acceptance (runnable)

Specs (`spec/isaac/session/compaction_spec.clj`, `spec/isaac/drive/turn_spec.clj`):
1. The summarization request built by `summarize-messages` carries
   `:effort 2` when the session effort is 7; a model with `:allows-effort
   false` gets no effort key.
2. `:compaction {:effort 5}` in crew config overrides the default.
3. A 90k-token history with `max-request-tokens 32000` on a 278k window
   plans ≥3 chunks; 20k tokens plans 1.
4. A chunk failing with `{:error :stream-stalled}` is retried once at half
   the chunk size; only a second failure increments `consecutive-failures`.

Feature (`features/session/compaction_logging.feature` or `compaction.feature`):
one scenario with the Grover fixture showing a chunked compaction under the
cap and `the last LLM request matches` `reasoning.effort` = low (Responses
bucketing of effort 2). Existing compaction scenarios stay green.

    bb spec spec/isaac/session spec/isaac/drive
    bb features features/session/compaction.feature features/session/compaction_logging.feature

Full gate green. Hand off `--tag=unverified`.
