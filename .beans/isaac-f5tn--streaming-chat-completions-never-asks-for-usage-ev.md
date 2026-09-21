---
# isaac-f5tn
title: 'Streaming chat-completions never asks for usage: every Fireworks/grok turn records zero tokens'
status: completed
type: bug
priority: high
created_at: 2026-09-21T04:06:56Z
updated_at: 2026-09-21T04:15:31Z
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

## Landed

`main-sha: df1707834fadf94ecf256e67912f9fa8d7c2c079` — agent 0.1.78.

`stream_options` added to `wire-fields`; the streaming path sends
`{:include_usage true}`. Three specs pin it (the request asks for usage; a
usage-only closing chunk with empty `:choices` is counted; `cached_tokens`
surfaces as `:cache-read-tokens`). Full gate green: 1671 specs, 843 features,
0 failures.

Deployed to zanebot, service restarted, `isaac.agent 0.1.78 ok`.

Live proof — two GLM-5.3 turns on session `f5tn-probe`:

| turn | prompt-tokens | output-tokens | cache-read-tokens |
|---|---|---|---|
| 1 (cold) | 1850 | 3 | 0 |
| **2 (same prefix)** | **1862** | **80** | **1849** |

Before this change both turns would have recorded zero. Turn 2 also answers the
open question about caching: **Fireworks is serving our prefixes from cache** —
99% of the prompt on a repeat turn. Whether that is billed at a discount is a
Fireworks pricing question; Isaac now reports the number either way.

## Correction to the blast radius above

Checked after landing. `grok` uses the same `chat-completions` adapter
(`:api "chat-completions"` in the provider catalog), so it had the same missing
request field — but xAI sends a usage block in a stream whether you ask or not,
and grok sessions were recording tokens and cache-read all along
(marvin, 09-17: `:cache-read-tokens 137472` of 147272 prompt tokens).

So the bug was in the request for every chat-completions provider, but only a
server that follows the spec strictly — Fireworks — actually withheld the
numbers. That is the same shape as isaac-uxe1: lenient servers hid it.
