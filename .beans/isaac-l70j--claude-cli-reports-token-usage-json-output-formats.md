---
# isaac-l70j
title: 'claude-cli reports token usage: json output formats replace text mode'
status: in-progress
type: feature
priority: normal
created_at: 2026-07-12T20:08:06Z
updated_at: 2026-07-12T21:23:38Z
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



## Verify fail (attempt 2, 2026-07-12): the rework branch is not verifiable because the new real smoke spec has a syntax error, so the spec suite does not load

Evidence:
- Current implementation branch is `origin/bean/isaac-l70j` at `ca0c2ce` (`isaac-l70j: restore claude_cli_real_spec after bad rebase commit`).
- `clojure -M:spec spec/isaac/llm/claude_cli_spec.clj spec/isaac/llm/claude_cli_real_spec.clj` fails before running examples with `Syntax error reading source at (spec/isaac/llm/claude_cli_real_spec.clj:164:1)` and `EOF while reading, starting at line 46`.
- Parenthesis count on `spec/isaac/llm/claude_cli_real_spec.clj` is unbalanced (`opens 159`, `closes 158`).
- Targeted bean feature scenarios still pass: `clojure -M:features features/llm/api/claude_cli.feature:217 features/llm/api/claude_cli.feature:228 features/llm/api/claude_cli.feature:240` -> `3 examples, 0 failures, 9 assertions`.
- Because the real spec file does not parse, the bean cannot satisfy the acceptance gate or CI verification in its current state.

## Planner rescope (2026-07-12, prowl) — criterion 5 is real-binary usage on the RESPONSE; transcript persistence is proven hermetically

Both verify fails are upheld as accurate. But criterion 5 as originally written is
over-specified, and that over-specification is what pushed the worker into a
real spec that stands up a session + drive turn and no longer parses. Settling
the design so the fix is clean.

### The seam split

Two distinct things were conflated in criterion 5:

1. **"Does the real claude binary actually emit usage in json/stream-json
   mode?"** — this can ONLY be proven by a real run, and it lives on the
   claude-cli RESPONSE object. This is the `@real` smoke's unique job.
2. **"Does response usage get mapped into the persisted transcript token
   fields?"** — this is deterministic wiring with no dependence on the real
   binary, and it is ALREADY required hermetically by **scenario 1**
   ("stubbed json output -> reply text correct AND transcript entry carries
   nonzero input/output tokens"). Scenario 2 covers the streaming terminal-usage
   case the same way.

A `@real` test that spins up a session, runs a drive turn, and inspects a
persisted transcript entry duplicates scenario 1's coverage while adding real-
binary latency and flake. That is a testing anti-pattern, not stronger proof.

### Revised criterion 5

- [ ] 5. `@real` smoke: a real claude turn returns **nonzero `:input-tokens`
      and `:output-tokens` on the claude-cli response** (`sut/chat` result
      `:usage`). No session/drive-turn/persisted-transcript scaffolding in the
      real spec.
- The response-usage → transcript-token-field mapping is proven by **scenario 1**
  (non-stream) and **scenario 2** (streaming terminal usage), hermetically, with
  stubs. Confirm both assert the persisted transcript entry's nonzero token
  fields; if either only asserts the response object, strengthen THAT hermetic
  scenario — do not push it into the `@real` smoke.

### Consequence for the syntax error

Deleting the session/drive-turn scaffolding from
`spec/isaac/llm/claude_cli_real_spec.clj` (reverting the real smoke to a direct
`sut/chat` usage assertion) should remove the unbalanced-paren block entirely.
Whatever remains must parse: `clojure -M:spec spec/isaac/llm/claude_cli_spec.clj
spec/isaac/llm/claude_cli_real_spec.clj` loads and runs green (real spec gated
behind `@real` as before, not run in the default suite).

### Acceptance (unchanged except crit 5)

- Scenarios 1-4 green (hermetic).
- Revised crit 5 real smoke green under the `@real` gate only.
- `bb ci` green (spec suite LOADS — the parse failure is the hard blocker).

This note resets the verify-fail count. Resume in work.

## Planner note (2026-07-12, prowl) — rescope stands; parse fix not yet applied

Verifier confirms the re-handoff target `origin/bean/isaac-l70j` @ `cc17952`
(and head `ca0c2ce`) STILL fails to load:
`Syntax error ... claude_cli_real_spec.clj:164:1`, `EOF while reading, starting
at line 46`, opens/closes unbalanced. Feature scenarios pass; the spec suite
cannot load.

No new planning is required — the rescope above already prescribes the fix. This
is a worker execution gap: the branch was re-handed off WITHOUT applying the
rescope. Do not re-verify or re-escalate until the spec suite LOADS.

Required before next verify handoff:
- Delete the session/drive-turn/persisted-transcript scaffolding from
  `spec/isaac/llm/claude_cli_real_spec.clj`; the `@real` smoke asserts nonzero
  `:input-tokens`/`:output-tokens` on the `sut/chat` RESPONSE `:usage` only.
- Prove the file parses:
  `clojure -M:spec spec/isaac/llm/claude_cli_spec.clj spec/isaac/llm/claude_cli_real_spec.clj`
  loads and runs green (real spec behind the `@real` gate).
- Transcript-usage persistence stays proven hermetically by scenarios 1 and 2.
- `bb ci` green. The parse failure is the hard blocker — nothing merges until it
  loads.

Verify-fail count remains reset; this is a work handoff, not a fail.

## Human escalation (2026-07-12, prowl)

Verify loop is not converging. The plan-side rescope is fully recorded
(beans `b78263d7`, `ff645df2`) and the required fix is mechanical, but three
successive work handoffs have re-presented `cc17952` / `ca0c2ce` WITHOUT
applying it — `spec/isaac/llm/claude_cli_real_spec.clj` still does not parse
(opens 159 / closes 158; `EOF` at `:46`; syntax error at `:164`), so the spec
suite cannot load and verify cannot pass.

This is not resolvable by planning: it is a worker execution gap on a file
outside the planner's write scope, and re-handoffs are not landing the change.
Escalated to human (Discord #isaac + iMessage) to apply the parse fix or
reassign. Required fix unchanged from the rescope above: revert the `@real`
smoke to a direct `sut/chat` response-`:usage` assertion (deletes the
unbalanced-paren session/`bridge/dispatch!`/persisted-transcript block); prove
`clojure -M:spec spec/isaac/llm/claude_cli_spec.clj spec/isaac/llm/claude_cli_real_spec.clj`
loads; `bb ci` green.
