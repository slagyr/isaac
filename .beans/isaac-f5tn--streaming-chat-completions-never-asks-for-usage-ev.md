---
# isaac-f5tn
title: 'Streaming chat-completions never asks for usage: every Fireworks/grok turn records zero tokens'
status: todo
type: bug
priority: high
created_at: 2026-09-21T04:06:56Z
updated_at: 2026-09-21T04:06:56Z
---

## What happens

On the streaming Chat Completions path (`isaac-agent/src/isaac/llm/api/chat_completions.clj`,
`chat-stream-with-completions-api`) the request body is built as
`(wire-body (assoc request :stream true))` and **never sets
`stream_options: {"include_usage": true}`**. OpenAI-compatible servers —
Fireworks and grok included — only emit a usage block in a stream when that
flag is set. There is no `stream_options` anywhere in isaac-agent.

So:

- the final SSE chunk carries no `usage`, `process-sse-event` has nothing to
  stash, `:usage` stays `{}`
- `shared/parse-usage` maps `{}` to `{:prompt-tokens 0 :output-tokens 0}` —
  the translation is correct, there is simply nothing to translate
- `normalized-provider-prompt-tokens` returns 0, `store-response!` guards on
  `(pos? prompt-tokens)`, so no stamp is written and nothing is logged

Measured on zanebot: 273 GLM-5.3 (Fireworks) requests, **zero** `:prompt-tokens`
entries in server.log and **zero** `cache-read-tokens` readings across the whole
log. The session gauge for those sessions is blind — which is how a single bean
resent the same conversation to ~223K tokens, 273 times, for 40M tokens total,
without ever tripping compaction.

The non-streaming path is fine: `chat-with-completions-api` reads `(:usage resp)`
off the response body, which servers always send.

## Blast radius

Every provider that uses the Chat Completions adapter in streaming mode —
fireworks, grok, any openai-compatible base-url. The messages (Anthropic) and
responses (OpenAI Responses) adapters are unaffected; both get usage in-stream
with no opt-in.

## Cached-input reporting rides on the same fix

`shared/parse-usage` already reads `prompt_tokens_details.cached_tokens` into
`:cache-read-tokens`. With no usage block we cannot tell whether the server
served any of our prompt from its prefix cache, so we cannot confirm a cached
rate or measure cache hit ratio for Fireworks. Once usage arrives, this comes
for free.

## Done when

- `stream_options` is in `wire-fields` and the streaming path sets
  `{:include_usage true}`
- a streaming scenario asserts `:prompt-tokens`/`:output-tokens` are reported
  from the closing chunk (usage-only chunk: `choices` empty)
- `:cache-read-tokens` surfaces when the server reports `cached_tokens`
- a live GLM turn on zanebot stamps a session gauge
