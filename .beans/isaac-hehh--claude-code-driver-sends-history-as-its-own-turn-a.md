---
# isaac-hehh
title: Claude Code driver sends history as its own turn and counts each request's usage twice
status: in-progress
type: bug
priority: high
created_at: 2026-11-17T00:21:18Z
updated_at: 2026-11-17T00:24:03Z
---

Found by Zane Lab on 2026-11-16 (experiment `claude-provider-fix`), with a
patch proven in a scratch clone. Repo: `isaac-claude-code`, file
`src/isaac/provider/claude_code/api/claude_cli.clj`.

## What is wrong

1. **History is sent as its own turn.** In driven mode `conversation->stream-json`
   writes two stdin envelopes: the prior conversation as prose, then the live
   prompt. Claude Code runs one model turn per envelope, so every turn on a
   session with history makes a hidden extra model request whose reply Isaac
   throws away. It costs a whole request, and the old instructions in the
   history read as live ones: Haiku once re-ran an earlier turn's `fs__read`
   instead of answering the live prompt.
2. **One request's usage is counted two or more times.** `stream-cycle-usages`
   counts every non-result event that carries usage. With
   `--include-partial-messages` one API request reports usage on its
   `assistant` event (once per content block) and again on the
   `stream_event` `message_delta`. A one-request turn logs `:cycles 2`; a turn
   with history logs 4–5. `--usage`, `:turn/ended :requests` and session
   token tallies all run about 2× high.

Sandbox evidence (live config copy, Haiku, 5 turns each arm): Claude Code
`result` events per turn 2 → 1; reported requests 4–5 → 1; real prompt tokens
about halved; smoke replay 4/4 in both arms.

## Fix

- **One envelope per turn.** With history, the single user message carries the
  history first, inside `<conversation_history>…</conversation_history>` with a
  one-line preamble saying it is the conversation so far and is context, then
  the live prompt. A first turn stays a plain string.
- **Usage grouped per API message.** `assistant` and `message_start` events
  name `message.id`; a `message_delta` belongs to the `message_start` before
  it. Each field takes the largest value in its group (the figures are
  cumulative). Events with no id still count on their own, so existing
  scripted cycles are unchanged.

## Reference patch

Zane's patch (+129/−39 across the source, `claude_driver_spec.clj` and
`claude_driver.feature`) is on zanebot at
`/Users/zane/agents/zane-lab/experiments/claude-provider-fix/fix.patch`, with
`BEAN.md`, `check.sh` and `evidence.edn` beside it, against sha `836ee0da`.
Use it as a starting point; you own the result. `main` has moved since
(isaac-y3wm), so rebase it by hand.

## Acceptance

- The `@wip` scenarios baselined below pass with `@wip` removed.
- Three existing scenarios in `claude_driver.feature` assert two stdin
  envelopes (the reasoning-history one near line 90, "prior turns are replayed
  as text history before the live prompt", and the stream-json envelope one
  near line 355). They encode bug 1. Update them to the one-envelope shape;
  that is a behaviour change this bean makes on purpose.
- New fake capabilities the scenarios need: script kind `delta_usage` (the
  same request's usage repeated on a `stream_event` `message_delta`; the
  cycle's `usage` event and its `delta_usage` share one message id), and the
  step `the fake Claude Code received {n} stdin lines`.
- Capture one raw stream from the real CLI (`ISAAC_CLAUDE_REAL=1`) and confirm
  in the bean notes that `message_delta` usage is cumulative, field by field.
  If it is not, stop and say so.
- `bb ci` and `bb jvm-spec` (if the repo has it) pass. Do not bump the module
  version; that happens at deploy.

feature-baseline: isaac-claude-code 4de0cd84fe7d53717ed209a652d0872a6bdcd1e7
feature-blob: isaac-claude-code features/llm/api/claude_driver.feature 71230245efb580b8e7df36162ca232614933fca9 750,769,780
