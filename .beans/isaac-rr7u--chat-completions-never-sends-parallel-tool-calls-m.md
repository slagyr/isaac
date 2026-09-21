---
# isaac-rr7u
title: 'chat-completions never sends parallel_tool_calls: models take the server default'
status: todo
type: bug
priority: normal
created_at: 2026-09-21T04:28:52Z
updated_at: 2026-09-21T04:28:52Z
---

## Why

`:parallel_tool_calls` is in `wire-fields` (`isaac-agent/src/isaac/llm/api/chat_completions.clj`)
but **nothing in isaac-agent ever sets it**, so the field is absent from every
request and each server applies its own default.

Measured on zanebot across every streamed response in server.log:

| model | tool responses | exactly 1 call | 2+ calls | batched |
|---|---|---|---|---|
| glm-5.3 | 253 | 253 | 0 | 0.0% |
| grok-4.6 | 74 | 38 | 36 | 48.6% |

Same adapter, same tool schema, same system prompt — which already carries the
standing `parallel-tool-calls-hint` telling the model that one call per response
is the slow path. The loop handles batches (`execute-tool-batch`, 4 concurrent).
GLM still made 273 round trips for one bean (isaac-cgxa).

## Scope, honestly

A live probe on 0.1.78 asked GLM in plain English to batch two independent
reads. It made two round trips anyway (`:tool-calls-count 1`, twice) and then
claimed it had batched them. So this flag is very unlikely to fix GLM — its
refusal to batch looks baked in.

It is still the right default to send: it is the documented way to say what we
want, it costs one field, and a model that *can* batch should not be left to a
server default we never chose.

## Done when

- requests carry `parallel_tool_calls: true` when the request has tools
- the field is NOT sent when there are no tools (OpenAI rejects it then)
- an explicit `:parallel_tool_calls` on the request wins, so a model config can
  turn it off
- both the streaming and non-streaming paths behave the same
