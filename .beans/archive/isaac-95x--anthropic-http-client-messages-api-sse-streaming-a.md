---
# isaac-95x
title: "Anthropic HTTP client - Messages API, SSE streaming, auth"
status: completed
type: task
priority: normal
created_at: 2026-04-01T16:05:21Z
updated_at: 2026-04-01T16:25:10Z
---

## Description

Implement Anthropic Messages API client.

## Feature Files
- features/providers/anthropic/messaging.feature (response, streaming, tools, errors)
- features/providers/anthropic/auth_api_key.feature (API key auth + @slow integration)

## Endpoint
POST https://api.anthropic.com/v1/messages

## Authentication (API key only for this bead)
Header: x-api-key + anthropic-version
API key from env via ${ANTHROPIC_API_KEY} syntax in provider config

## Response Format
{
  "id": "msg_...",
  "type": "message",
  "role": "assistant",
  "content": [{"type": "text", "text": "..."}],
  "stop_reason": "end_turn",
  "usage": {
    "input_tokens": N, "output_tokens": N,
    "cache_creation_input_tokens": N, "cache_read_input_tokens": N
  }
}

## Streaming
SSE (Server-Sent Events), not newline-delimited JSON.
Events: message_start, content_block_start, content_block_delta, content_block_stop, message_delta, message_stop

## Token Tracking
input_tokens → inputTokens, output_tokens → outputTokens
cache_read_input_tokens → cacheRead, cache_creation_input_tokens → cacheWrite

## Tool Calling
Response content blocks with type "tool_use" (id, name, input).
Tool results sent as role "user" with content type "tool_result".

## Error Handling
401: authentication failed, 429: rate limited, connection refused: unreachable

