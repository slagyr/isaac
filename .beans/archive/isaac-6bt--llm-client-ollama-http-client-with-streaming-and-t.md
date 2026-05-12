---
# isaac-6bt
title: "LLM client - Ollama HTTP client with streaming and tool loop"
status: completed
type: task
priority: normal
created_at: 2026-03-31T19:49:06Z
updated_at: 2026-03-31T22:57:30Z
---

## Description

Implement LLM interaction per features/session/llm_interaction.feature.

## Ollama API
Endpoint: POST http://localhost:11434/api/chat
Default model: qwen3-coder:30b (supports tool calling)

## Response Shape (non-streaming)
{
  "model": "qwen3-coder:30b",
  "message": {"role": "assistant", "content": "..."},
  "done": true,
  "done_reason": "stop",
  "prompt_eval_count": 42,
  "eval_count": 100
}

## Response Shape (streaming)
Newline-delimited JSON chunks, each with partial content.
Final chunk has "done": true with token counts.

## Tool Call Response
Model returns: message.tool_calls[].function.{name, arguments}
Note: arguments is a parsed object, not a JSON string (unlike OpenAI).

## Tool Call Loop
1. Send prompt
2. If response has tool_calls: append assistant message to transcript
3. Execute tool, append toolResult to transcript
4. Re-build prompt with updated history, send again
5. Repeat until response has no tool_calls (done_reason: "stop")

## Token Tracking
After each response, update session index:
- inputTokens += prompt_eval_count
- outputTokens += eval_count
- totalTokens = current context size estimate

## Testing
LLM interaction features hit a live Ollama server. Tests that require the server should be tagged appropriately. The "server unavailable" scenario tests error handling with no running server.

## Feature File
features/session/llm_interaction.feature

