---
# isaac-94c0
title: Foreman CLI signal/retry boot the Agent runtime, so a :turn action works from a real shell
status: completed
type: bug
priority: normal
created_at: 2026-10-06T20:23:23Z
updated_at: 2026-10-06T20:44:02Z
---

Likely repo: **isaac-foreman**. Found by Foreman pilot 1 (isaac-8uno on zanebot), 2026-10-06.

## Why

`isaac foreman signal` (and `retry`) run from a real shell crash when a transition fires a `:turn` action:
`No implementation of method: :list-sessions of protocol: SessionStore … for: nil`. Agent's
`turn-submit/submit!` resolves the session target against the registered session store, and a
fresh CLI process has none. The in-process feature harness pre-registers a store, which hid it.
Same bug Hail had in isaac-1i1x: `isaac hail send` fixed it by booting the Agent runtime in the
CLI process (`host/ensure-runtime!` → `runtime/install!`, see `isaac-hail/src/isaac/hail/cli.clj`
`ensure-runtime!`). Submission is queue-only, so the turn still runs in the server.

## Design

- Foreman's CLI commands that can submit turns (`start` if the initial state fires actions,
  `signal`, `retry`) boot the Agent runtime the way `hail send` does, before submitting.
- No "server-only" refusal: the CLI writes the durable queue record; the server's worker runs it.
- The step `the next isaac command starts in a fresh process` lives in
  `isaac-hail/feature-steps/isaac/hail/handoff_steps.clj`. Add the same step to Foreman's
  feature-steps (or move it to isaac-agent's spec support and keep Hail green).

## Acceptance

- isaac-foreman `features/foreman/turn_action.feature:158` — signal works from a fresh shell.
- The rest of the isaac-foreman features stay green.

feature-baseline: isaac-foreman b2572e6e9b0ed2fd2719c5c0e92fc594f256a5a2
feature-blob: isaac-foreman features/foreman/turn_action.feature 131acccb92ac60785b3bcfd1c8da00bdd1ac5a65 158

## Landed on main (2026-10-06)

main-sha: isaac-foreman 01d46c1328a5b9a435dcdd33b857db614be83e08
