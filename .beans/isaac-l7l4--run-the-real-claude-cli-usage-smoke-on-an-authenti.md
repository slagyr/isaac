---
# isaac-l7l4
title: Run the @real Claude-CLI usage smoke on an authenticated host (isaac-l70j crit 5)
status: todo
type: task
priority: normal
created_at: 2026-07-12T22:29:32Z
updated_at: 2026-07-12T22:29:32Z
---

## Goal

Execute the `@real` Claude-CLI usage smoke for isaac-l70j against a
logged-in Claude CLI and confirm a real turn returns nonzero
`:input-tokens`/`:output-tokens` on the `sut/chat` response `:usage`.

## Why this is split from isaac-l70j

isaac-l70j landed the claude-cli json/stream-json usage parsing and the
revised `@real` smoke at `spec/isaac/llm/claude_cli_real_spec.clj` (direct
`sut/chat` usage assertion, no transcript scaffolding — per the l70j planner
rescope). That spec is gated behind `ISAAC_CLAUDE_REAL=1` by design because it
needs live Claude authentication.

The verify host for isaac-l70j has no authenticated Claude CLI:
`ISAAC_CLAUDE_REAL=1 clojure -M:spec spec/isaac/llm/claude_cli_real_spec.clj`
fails 2 examples; direct repro
`claude --print --output-format json --model sonnet 'Reply ... pong' < /dev/null`
returns `401 Invalid authentication credentials`. The smoke's PRESENCE, SHAPE,
and LOAD are verified in l70j; only its EXECUTION against real auth is deferred
here.

## Acceptance (one-time, authenticated environment)

- [ ] On a host with a logged-in Claude CLI, run
      `ISAAC_CLAUDE_REAL=1 clojure -M:spec spec/isaac/llm/claude_cli_real_spec.clj`
      in `isaac-agent` against the merged isaac-l70j code — the `@real` usage
      smoke passes (nonzero `:input-tokens` and `:output-tokens`).
- [ ] Record the run (host, date, example/assertion counts) in this bean.
- [ ] If the real binary does NOT surface usage in json/stream-json mode,
      record that finding — it is a real-binary/version gap, not an l70j
      regression, and gets its own bean.

## Notes

- No production code expected here; this is an environment/execution check.
- Depends on isaac-l70j merging first (and the `:isaac.agent` pin advancing if
  the run is done against a deployed launcher rather than a source checkout).
