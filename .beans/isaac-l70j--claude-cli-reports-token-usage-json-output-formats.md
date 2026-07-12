---
# isaac-l70j
title: 'claude-cli reports token usage: json output formats replace text mode'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-07-12T20:08:06Z
updated_at: 2026-07-12T20:26:17Z
---

## Goal

claude provider turns report real token usage. Today transcripts show usage all-zeros (observed 2026-07-12) because the adapter runs the CLI in text mode, which prints only the answer. The claude binary exposes usage in its structured output modes.

## Design

- Non-stream runs: --output-format json — the CLI emits a single JSON object with the result text plus usage (input/output tokens, cache counters, total_cost_usd). Parse result text from it; map usage into the standard turn token fields (input-tokens/output-tokens/cache-read/cache-write) so session accounting, sessions list, and any future budget machinery see claude turns.
- Streaming runs (stream-non-tool-turns): --output-format stream-json — parse text deltas for chunks; the terminal event carries usage.
- Keep the tool-protocol and system-prompt behavior (isaac-ozv9) unchanged — only the output parsing layer changes.
- If the installed CLI version lacks a field, absent usage degrades to zeros (never fails a turn on accounting).

## Scenarios (worker writes; required coverage — stub emits REALISTIC claude json/stream-json fixtures)

1. Non-stream turn: stubbed json output -> reply text correct AND transcript entry carries nonzero input/output tokens.
2. Streaming turn: stubbed stream-json -> chunks emitted in order, final usage recorded.
3. Malformed/missing usage in output -> turn succeeds, usage zeros (accounting never breaks a turn).
4. argv tables updated for the new --output-format values (spec+gherkin lockstep per kn7y lesson).
5. @real smoke extended: real turn shows nonzero usage in the transcript entry.
