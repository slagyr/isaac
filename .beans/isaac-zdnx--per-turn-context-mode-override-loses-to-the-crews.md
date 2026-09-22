---
# isaac-zdnx
title: Per-turn context-mode override loses to the crew's :context-mode (--with-crew and --with-model do win)
status: in-progress
type: bug
priority: normal
tags:
    - agent
    - cli
    - unverified
created_at: 2026-09-22T21:04:40Z
updated_at: 2026-09-22T21:27:18Z
---

## Observed (2026-09-22, zanebot, agent 0.1.81)

`isaac prompt -s isaac-work-2 --with-context-mode full -m "…"` on a session
whose crew (scrapper) sets `:context-mode :reset` resolved to
`:session/behavior-resolved :context-mode :reset` and skipped compaction with
`:reason :context-reset`. The same prompt with `--with-crew main` (a crew with
no context-mode) resolved `:full`. So the per-turn override is honoured only
when the crew is silent; a crew value beats it. `--with-model` and `--with-crew`
do win per turn, so this is inconsistent with the other `--with-*` overrides
(isaac-4e4b's stated intent: uniform override across hail/prompt/acp/chat).

## Expected

Per-turn overrides (`--with-context-mode`, hail `:with-context-mode`) take
precedence over crew config for that turn, same as `--with-model`.

## Acceptance

- Scenario: a crew with `:context-mode :reset`; a prompt turn with
  `--with-context-mode full` builds the request from the full transcript and
  logs `behavior-resolved :context-mode :full`.
- Scenario: no override → crew value applies (unchanged).
- `bb spec` / features green in isaac-agent.

## Handoff (worker, 2026-09-22)

Root cause: `isaac.charge/build` resolves per-turn session behavior via a
private `behavior-opts` helper that only forwarded crew and model overrides
(`:model-override`/`:model-ref`) into the opts map passed to
`session-ctx/resolve-behavior`. It never forwarded a context-mode override, so
even though `isaac.session.frequencies/behavioral-override` and
`isaac.session.context/resolve-behavior*` already had correct precedence
plumbing for `:context-mode`, the CLI's `dispatch-prompt!` had nothing to hand
`charge/build` and the crew's `:context-mode` always won for existing
sessions. `--with-model`/`--with-crew` worked because their overrides did
flow through `behavior-opts`.

Files changed:
- `src/isaac/charge.clj` — `behavior-opts` now accepts `context-mode-override`
  and assocs `:context-mode` into resolve-behavior opts; `build` destructures
  and threads a new `:context-mode-override` request key.
- `src/isaac/bridge/prompt_cli.clj` — `dispatch-prompt!` passes
  `:context-mode-override (:with-context-mode override)` into `charge/build`.
- `spec/isaac/charge_spec.clj` — new spec "forwards a context-mode override to
  resolve-behavior, same as the model override" (RED before the fix, GREEN
  after).
- `features/bridge/cli-prompt.feature` — new scenario "--with-context-mode
  overrides the crew's :context-mode :reset for the turn (isaac-zdnx)".
- `CHANGELOG.md` — one line under Unreleased naming isaac-zdnx.

Test commands (isaac-agent-isaac-zdnx worktree):
- `bb spec` → 1711 examples, 0 failures, 3567 assertions
- `bb features` → 848 examples, 0 failures, 2038 assertions, 1 pending
  (pre-existing "Mid-turn compaction keeps the request in flight" — not yet
  implemented, unrelated to this bean)
- `bb ci` → exit 0 (runs the same spec/features suites)

Not done: hail's `:with-context-mode` path was not separately wired — the
only production call site forwarding per-turn overrides into `charge/build`
today is the CLI's `dispatch-prompt!` (`isaac.bridge.prompt_cli`). Other
`charge/build` callers (`isaac.drive.weather`, `isaac.turn.worker`) don't
thread any `:with-*` overrides at all; if hail dispatch shares
`dispatch-prompt!`/`charge/build` the fix already covers it, but if hail has
its own charge-building call site it was not located/touched in this pass and
may need the same `:context-mode-override` wiring.

Branch: `bean/isaac-zdnx`, commit `c08328f`, pushed to origin.

## Planner check (2026-09-22)

Reran on bean/isaac-zdnx c08328f: `bb spec` 1711/0, `bb features` 848/0 (1 pending, pre-existing). Diff reviewed: `:context-mode-override` threads through `charge/build` → `behavior-opts` → `resolve-behavior`, CLI passes it. PR opened to isaac-agent main; tagged `unverified`. The same drop exists in isaac-hail `delivery-charge` (crew + model only): filed as isaac-onzi, blocked by this bean landing and an agent pin bump. Note: until isaac-onzi lands, a band-level `:with-context-mode` does nothing; reset mode on scrapper/perceptor works because it is set on the crew, not the band.
