---
# isaac-onzi
title: 'Queued turns drop :with-context-mode: isaac.agent.turn.worker/wake-charge never forwards it'
status: in-progress
type: bug
priority: normal
tags:
    - hail
created_at: 2026-09-22T21:27:18Z
updated_at: 2026-10-09T18:01:57Z
blocked_by:
    - isaac-zdnx
---

## Problem

A turn submitted by frequencies (a hail, cron, any queue producer) can carry `:with-context-mode` to choose how that one turn's context is built. It never reaches the turn. `isaac.agent.turn.worker/wake-charge` (`isaac-agent/src/isaac/agent/turn/worker.clj`) builds the request for `charge/build` from the queued record and forwards `:with-crew` and `:with-model` from `:frequencies`, but not `:with-context-mode`. So a band-level or per-hail context mode is silently ignored.

The CLI had the same gap and was fixed in isaac-zdnx: `charge/build` accepts `:context-mode-override`, and `bridge/prompt_cli.clj` maps `--with-context-mode` onto it.

## Change

In `wake-charge`, forward `(get-in record [:frequencies :with-context-mode])` as `:context-mode-override`, beside the existing `:with-crew` and `:with-model` lines. Nothing else.

The frequencies schema types `:with-context-mode` as a keyword, and a hail over HTTP delivers it as a string. Make sure the value that reaches `charge/build` is a keyword either way.

## Scenarios (committed `@wip` on isaac-agent main 4e9741c, `features/turn/queued_context_mode.feature`)

1. `:with-context-mode full` replays history for a crew set to reset
2. `:with-context-mode reset` drops history for a crew left at full

## Step ledger

| Step | Status |
|---|---|
| `default Grover setup` | existing |
| `the isaac EDN file "…" exists with:` | existing |
| `the following sessions exist:` | existing |
| `session "…" has transcript:` | existing |
| `the following model responses are queued:` | existing |
| `a turn with input "…" is submitted with frequencies:` | existing |
| `the turn queue ticks at "…"` | existing |
| `the last LLM request matches:` | existing |

No new steps. `a turn with input "…" is submitted with frequencies:` passes every value except `create` through as a string (`spec/isaac/agent/turn/queue_steps.clj`); if the keyword coercion belongs in the step rather than in production, change the step, not the feature.

## Acceptance

- `@wip` is removed from `features/turn/queued_context_mode.feature` and both scenarios pass.
- `features/session/context_mode.feature`, `features/session/context_mode_berth.feature` and `features/turn/session_selection.feature` pass with no edits to their scenarios.
- A unit spec covers `wake-charge` forwarding the override.

```
cd isaac-agent && bb features features/turn/queued_context_mode.feature && bb features features/session/context_mode.feature features/session/context_mode_berth.feature features/turn/session_selection.feature && bb ci && bb jvm-spec
```

## Likely repo scope

`isaac-agent` only.

## History

Filed 2026-09-22 against isaac-hail's `delivery_worker.clj`, which built the charge at the time. Re-triaged 2026-09-30 (planner, approved by Micah): that file is gone, hail now hands `:with-context-mode` to Agent's turn store inside `:frequencies`, and the drop moved to `wake-charge`. Blocker isaac-zdnx (the CLI side) is completed. Related: isaac-dgod trial notes (2026-09-22).

feature-baseline: isaac-agent 4e9741cc6d0846939538dcb94169521071c45391
feature-blob: isaac-agent features/turn/queued_context_mode.feature 3ccedf0d3d91b2f0de1316eeee2d8ab0d73eef92
