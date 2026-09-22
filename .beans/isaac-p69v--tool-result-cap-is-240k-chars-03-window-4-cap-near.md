---
# isaac-p69v
title: 'Tool result cap is 240k chars (0.3 × window × 4): cap near 30k like Claude Code, decoupled from the window and configurable'
status: todo
type: task
priority: high
tags:
    - agent
    - tokens
created_at: 2026-09-22T19:48:54Z
updated_at: 2026-09-22T19:48:54Z
---

## Problem

`isaac.session.transcript/truncate-tool-result` caps a single tool result at
`0.3 × context-window × 4` characters. With the zanebot model entry at 200k that
is **240,000 characters (~60k tokens) per tool result**. Claude Code caps a Bash
result near 30k characters and reads files in windows. One oversized result in
Isaac rides in every subsequent request of the session until compaction, so a
single 240k result costs ~60k tokens on every cycle after it.

Measured 2026-09-22 on zanebot `isaac-work-2` (claude-code / claude-opus-5):
largest stored tool result 32,366 chars; the transcript held 163 tool results
(425 KB, 124 of them `exec__run`) and every request that morning was 270–304k
tokens. The cap did not bite that day — volume did — but it is the wrong
guardrail: it scales with the model window, not with what a result is worth.

## Design

- Drop the formula. A constant default of **32,000 chars** (Micah, 2026-09-22:
  match the `tools.defaults.max-bytes` 32k / 400-line shell cap already set on
  zanebot rather than derive from the window), configurable at
  `tools.defaults.max-result-chars` with a per-tool override
  (`tools.<tool>.max-result-chars`), same shape as `tools.defaults.max-bytes`.
- Keep head-and-tail truncation; the marker names the omitted count and where
  the full output can be read (the transcript entry stays whole on disk).
- Apply at prompt build (where it is today), not at storage.
- Log once per truncated result: `:tool/result-truncated` with tool name, kept
  and dropped chars.

## Scenarios (worker writes)

1. A tool result longer than the default cap is sent head+tail with the marker;
   the stored transcript entry is untouched.
2. `tools.defaults.max-result-chars` raises/lowers the cap; a per-tool value wins.
3. A result under the cap is sent whole (no marker).
4. Changing the model's context-window does not change the cap.

## Acceptance

- Scenarios green (feature file under `features/session/` or `features/tools/`).
- `bb ci` green in isaac-agent.
- Config schema documents the new keys; `isaac config validate` accepts them.

## Related

isaac-dgod (compact on real stamps), isaac-la8h (parallel tool calls hint).
