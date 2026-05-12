---
# isaac-ytr
title: "OpenAI-compatible provider - shared client for Grok, OpenAI, and Ollama /v1"
status: completed
type: task
priority: normal
created_at: 2026-04-01T17:57:23Z
updated_at: 2026-04-01T18:01:49Z
---

## Description

Implement an OpenAI-compatible provider that works for Grok, OpenAI, and Ollama's /v1 endpoint.

## Feature Files
- features/providers/grok/messaging.feature
- features/providers/openai/messaging.feature
- features/providers/grok/auth.feature
- features/providers/openai/auth.feature

## API Format
POST <baseUrl>/chat/completions
Auth: Authorization: Bearer <apiKey>

Request: standard OpenAI chat completions format (system message in messages array, not a separate field like Anthropic)
Response: { choices: [{ message: { role, content, tool_calls } }], usage: { prompt_tokens, completion_tokens } }
Streaming: SSE with data: chunks, [DONE] sentinel

## Provider Config
api: "openai-compatible" in the provider config table distinguishes this from "anthropic-messages".

## Provider Abstraction
This is the natural point to extract a provider protocol/interface from the existing Ollama, Anthropic, and this new OpenAI-compatible implementation. The interface should cover: chat, chat-stream, chat-with-tools.

## Differences from current Ollama client
- Auth header (Ollama needs none)
- Response shape (choices array vs single message)
- Streaming format (SSE vs newline-delimited JSON)
- Token field names (prompt_tokens vs prompt_eval_count)

The existing Ollama native client can remain for local use. This provider covers the /v1 compatible endpoints.

