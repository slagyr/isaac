---
# isaac-l70j
title: 'claude-cli reports token usage: json output formats replace text mode'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-07-12T20:08:06Z
updated_at: 2026-07-12T21:14:14Z
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



## Verify fail (attempt 1, 2026-07-12): acceptance requires the @real smoke to prove nonzero usage on the persisted transcript entry, but the bean only proves nonzero usage on the direct claude-cli response object

Evidence:
- Bean requirement 5 says: `@real smoke extended: real turn shows nonzero usage in the transcript entry.`
- The added real smoke in `spec/isaac/llm/claude_cli_real_spec.clj:44-55` calls `sut/chat` directly and only asserts `(:usage res)` has positive `:input-tokens` and `:output-tokens`. It does not create a session, run a drive turn, or inspect any persisted transcript entry.
- No feature or real integration scenario was added that checks a stored assistant message/transcript usage field under a real claude run.
- Functional tests are otherwise green on the bean branch: `clojure -M:spec` -> `1224 examples, 0 failures, 2454 assertions, 2 pending`; targeted bean scenarios `clojure -M:features features/llm/api/claude_cli.feature:217 features/llm/api/claude_cli.feature:228 features/llm/api/claude_cli.feature:240` -> `3 examples, 0 failures, 9 assertions`; `bb ci` -> specs `1224 examples, 0 failures, 2454 assertions, 2 pending`, features `633 examples, 0 failures, 1465 assertions`.
- Because the required real-smoke acceptance is specifically about the persisted transcript entry, the current coverage is insufficient to pass verification even though the implementation and non-real tests are green.
