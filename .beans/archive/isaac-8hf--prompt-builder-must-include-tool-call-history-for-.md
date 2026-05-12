---
# isaac-8hf
title: "Prompt builder must include tool call history for OpenAI-compatible providers"
status: completed
type: bug
priority: high
created_at: 2026-04-14T17:45:08Z
updated_at: 2026-04-14T19:15:35Z
---

## Description

OpenAI rejects requests with: Missing required parameter messages[3].tool_calls[0].type. The prompt builder strips tool calls entirely (works for Ollama) but OpenAI needs the full chain: assistant message with tool_calls array (type: function), followed by tool result messages (role: tool, tool_call_id). Provider-specific prompt building needed.

features/session/tool_history.feature — 2 scenarios

Root cause: src/isaac/prompt/builder.clj filter-messages strips tool calls at line 56-57. OpenAI path needs to preserve them with proper format.

## Acceptance Criteria

Both @wip scenarios in features/session/tool_history.feature pass with @wip removed. Marvin can use tools through GPT-5.4 without errors.

