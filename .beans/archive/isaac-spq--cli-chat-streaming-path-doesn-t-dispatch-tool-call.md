---
# isaac-spq
title: "CLI chat streaming path doesn't dispatch tool calls"
status: completed
type: bug
priority: high
created_at: 2026-04-11T14:05:46Z
updated_at: 2026-04-11T17:13:00Z
---

## Description

Tools are passed into the chat request via build-chat-request but the streaming dispatch path for REAL providers (ollama, possibly anthropic/openai) does not return structured tool_calls in their streaming responses. Grover's chat-stream does emit tool_calls in its final chunk so the grover-backed feature scenario passes, but real ollama streaming + qwen3-coder:30b produces text-format tool calls that Isaac's loop never detects.

## Reproduction
echo 'Please run isaac --help and show me the output' | bb isaac chat

With qwen3-coder:30b via ollama, the model responds with plain text explaining it can't run commands, or emits XML-like function call markup that Isaac prints verbatim instead of dispatching.

## Why the grover tests still pass
Grover's chat-stream (src/isaac/llm/grover.clj:74) returns the scripted response as its final chunk, preserving tool_calls in [:message :tool_calls]. The consumer loop in stream-and-handle-tools! detects those correctly and executes the tools. This is a test-double behavior that doesn't match real provider streaming.

## Why ollama fails
Real ollama streaming endpoint either does not support tool calling, or returns tool calls in a format the streaming consumer doesn't recognize. qwen3-coder:30b falls back to text-format tool calls embedded in content.

## Root cause
src/isaac/cli/chat.clj stream-result (line 415+) uses dispatch-chat-stream unconditionally for CLI channel, or dispatch-chat for non-CLI. Neither path uses dispatch-chat-with-tools, which is the only dispatch that explicitly wires tool calling for real providers.

## Fix direction
Branch stream-result on whether tools are present in the request:

  (if (:tools request)
    ;; tools → use the tool-calling dispatch (non-streaming, provider-correct)
    (dispatch-chat-with-tools ...)
    ;; no tools → stream for snappy UX
    (print-streaming-response ...))

Trade-off: when tools are active, the response arrives all at once instead of streaming. Acceptable — tool turns aren't the place for real-time rendering.

## Priority
P1 — core chat functionality with tools is broken against real providers. Isaac works only with test-double (grover) today.

## Acceptance
- echo 'Run isaac --help and show me the output' | bb isaac chat actually runs the exec tool and shows the help text
- bb features and bb spec pass (existing grover scenarios still work)

## Acceptance Criteria

1. Remove @wip from 'Tool calls dispatch when provider lacks streaming tool support' and verify:
   bb features features/session/llm_interaction.feature:60

2. Manual: echo 'Run isaac --help and show me the output' | bb isaac chat dispatches the exec tool and prints the help text

3. Existing grover tool call scenario still passes:
   bb features features/session/llm_interaction.feature:42

4. bb features and bb spec pass with 0 failures

## Implementation notes

- Add :streamSupportsToolCalls support to grover: when false, chat-stream strips :tool_calls from the final chunk before returning
- In chat.clj stream-result, branch on (:tools request): if present, route to dispatch-chat-with-tools; otherwise keep the current streaming path
- This makes 'tools → non-streaming, no tools → streaming' the routing rule
- Trade-off: tool turns lose real-time text streaming, which is acceptable

