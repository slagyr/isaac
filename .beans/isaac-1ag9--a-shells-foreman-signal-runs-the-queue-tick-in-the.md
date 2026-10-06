---
# isaac-1ag9
title: A shell's foreman signal runs the queue tick in the CLI process and orphans the turn; only the server runs turns
status: in-progress
type: bug
priority: high
created_at: 2026-10-06T22:43:19Z
updated_at: 2026-10-06T22:44:29Z
---

Likely repos: **isaac-agent** (`wake!`) + **isaac-foreman** (call it). Found by Foreman pilot 1, second run, 2026-10-06.

## Why

After isaac-94c0, `isaac foreman signal` from a shell boots the Agent runtime and submits the
turn — then `retry!`/signal calls `worker/tick!` inline (`isaac-foreman/src/isaac/foreman/core.clj:92`).
In a shell that tick runs in the CLI process: on zanebot (cli.log, 21:47:19) the shell created
session `bean-isaac-8uno`, stole the stale work-1 lock, claimed turn `a461ac71`, started it on a
future, and exited. The record says `:running` with nothing running it; the lock belongs to a
dead pid; the instance sits in `working`.

`hail send` never ticks, so it never hit this. A shell queues; only the process running the
queue worker (the server) runs turns.

## Design

- Agent: `isaac.agent.turn.worker/wake!` — ticks only when this process started the queue
  worker (`worker/start!`); otherwise a no-op. In-server callers (the `foreman__signal` tool)
  keep their prompt start; a shell leaves the turn to the server's tick (~10 s).
- Foreman: replace the inline `worker/tick!` with `worker/wake!`.
- Feature harness: the in-process harness counts as the server (existing Foreman scenarios that
  expect a signal's turn to run at once stay green); `the next isaac command starts in a fresh
  process` makes that command a shell with no queue worker.
- Consider grepping other modules for inline `worker/tick!` outside the server path.

## Acceptance

- isaac-foreman `features/foreman/turn_action.feature:175`.
- The rest of isaac-foreman and isaac-agent features stay green.

## Pilot recovery (after this deploys)

Turn `a461ac71` (orphaned `:running`) can then be dropped from a shell safely — isaac-tais sends
`:foreman/turn-died`, the machine stalls, and Prowl's alert turn is queued for the server.

feature-baseline: isaac-foreman 65c002d182e927f2bae82e24b72eac9a9fa7ec27
feature-blob: isaac-foreman features/foreman/turn_action.feature e96ba4e70df499d46bdb2a4cb40d3014b40c4201 175

## Contract conflict (2026-10-06)

The baselined `features/foreman/turn_action.feature:191-192` has a Gherkin table cell
`lamp-room\s.*\s(queued|held)` containing an unescaped `|`. The table parser splits
that regex into `lamp-room\s.*\s(queued`, so the acceptance scenario throws
`java.util.regex.PatternSyntaxException: Unclosed group near index 22` at
`isaac.foundation.cli-steps/stdout-matches` before the queue tick. This cannot
be fixed by changing product code or step logic honestly; the planner must escape
the pipe in the scenario and re-baseline. `bb features features/foreman/turn_action.feature:174`
reproduced the error with the local isaac-agent worktree dependency. Agent wake lifecycle
is implemented and `bb ci` green on `bean/isaac-1ag9` at fad6d1d; Foreman's
`worker/wake!` and `@wip` removal are in the foreman worktree, not yet committed.
The first gate invocation, before committing the feature, reported:
`FAIL isaac-foreman features/foreman/turn_action.feature: scenario "Scenario: a signal from a fresh shell only queues the turn; the server's queue runs it (isaac-1ag9)" still carries @wip`
(the gate checks HEAD, not working-tree edits). After planner repairs the table and
re-baselines, resume at `features/foreman/turn_action.feature:191`, then run the
focused feature, `bb ci`, and the gate against the worktree branch.
