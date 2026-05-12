---
# isaac-4sp
title: "ACP path returns empty content from chatgpt.com responses API"
status: completed
type: bug
priority: high
created_at: 2026-04-15T19:38:11Z
updated_at: 2026-04-15T20:31:59Z
---

## Description

The chatgpt.com/backend-api/codex/responses endpoint has a completely different tool call protocol than chat completions.

INBOUND (response → Isaac):
- response.output_item.added — contains function name and call ID
- response.function_call_arguments.delta — streams the arguments
- response.function_call_arguments.done — signals completion
Isaac only handles response.output_text.delta. Tool call events are silently dropped.

OUTBOUND (Isaac → request):
- Tool results must be sent as {type: function_call_output, call_id: ..., output: ...} in the input array
- NOT as {role: tool, tool_call_id: ..., content: ...} (chat completions format)
- The endpoint rejects role:tool with 'Invalid value: tool. Supported values are: assistant, system, developer, and user'

Both directions need fixing for Marvin to use tools through the codex endpoint.

features/providers/openai/dispatch.feature — 1 @wip scenario (Oscar under the trash lid)

## Acceptance Criteria

@wip scenario passes. Marvin uses tools through GPT-5.4 in IDEA via chatgpt.com endpoint.

## Notes

Verification failed: @wip tag still present on 'OAuth Codex provider handles tool call responses' scenario at features/providers/openai/dispatch.feature:81. Acceptance criteria require @wip removed with scenario passing.

