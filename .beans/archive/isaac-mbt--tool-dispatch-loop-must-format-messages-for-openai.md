---
# isaac-mbt
title: "Tool dispatch loop must format messages for OpenAI-compatible providers"
status: completed
type: bug
priority: high
created_at: 2026-04-14T23:49:53Z
updated_at: 2026-04-15T00:19:35Z
---

## Description

The in-turn tool dispatch loop in stream-and-handle-tools! (single_turn.clj:205-214) builds assistant and tool result messages directly, bypassing the prompt builder. These messages are missing type:function on tool_calls and use the wrong role/format for tool results.

The prompt builder (isaac-8hf fix) correctly formats transcript history, but the live tool loop creates raw messages that go straight to the LLM. On OpenAI, this causes messages[N].tool_calls[0].type errors on multi-round tool calls.

Root cause: line 207 passes raw-tools directly into the assistant message without adding type:function. Line 208-212 creates tool results with role:tool but missing tool_call_id.

features/session/tool_loop.feature — 2 scenarios

## Acceptance Criteria

Both @wip scenarios pass with @wip removed. Marvin can do multi-round tool calls on GPT-5.4 without type errors.

