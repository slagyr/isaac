---
# isaac-35gx
title: 'claude-code driver records CLI message ids and num_turns per driven turn: requests vs tool cycles become distinguishable'
status: todo
type: task
priority: normal
tags:
    - agent
    - claude-code
    - tokens
created_at: 2026-09-22T20:30:14Z
updated_at: 2026-09-22T20:30:14Z
---

## Problem

In driven mode (the claude CLI runs the tool loop over MCP) Isaac's transcript
and logs record one cycle per `tool_use` block. Nothing records the CLI's own
message ids or the `result` event's `num_turns`, so a turn that batched three
reads in one model message is indistinguishable from three single-call
messages. Measured 2026-09-22 (isaac-pn98): pn98-opus-personal-2013 shows
entries 1/1/1/1 with tool starts 0.15–0.3 s apart — one batched message
serialized — and Isaac cannot say so. The same gap makes the per-request stamp
work in isaac-dgod harder to verify on this lane.

## Change

- Each driven cycle's transcript `toolCall` entry (or its cycle stamp) carries
  the CLI assistant message id (`msg_…`) so blocks of one message are grouped.
- At driver exit, log `:claude/driver-summary` with `:num-turns` from the
  `result` event, `:tool-cycles`, `:distinct-messages`, and the result usage
  (input / cache-read / cache-write / output). Info level, once per turn.
- `turn/model-response-summary` gains `:requests` (distinct messages) next to
  `:tool-calls-count` when the adapter supplies it.

## Acceptance

- Spec: a fake driven script whose one assistant message carries two
  `tool_use` blocks yields two cycles with the same message id and
  `:distinct-messages 1`; two messages with one block each yield 2.
- `bb spec` / `bb ci` green in isaac-claude-code (and isaac-agent if the
  summary field lands there).
- One-time on zanebot: rerun the pn98 three-file prompt on the `:claude`
  lane and read `:distinct-messages` from the summary log line.

## Related

isaac-pn98 (hint + measurement caveat), isaac-dgod (per-request stamps),
isaac-8cur (replay burst, landed).
