---
# isaac-jgng
title: Compaction requests sized to the context window die 'closed' on chatgpt; cap per-request tokens and adapt chunk size on failure
status: draft
type: bug
priority: high
tags:
    - agent
    - compaction
created_at: 2026-09-04T16:29:12Z
updated_at: 2026-09-04T16:29:12Z
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
