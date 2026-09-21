---
# isaac-rr7u
title: 'chat-completions never sends parallel_tool_calls: models take the server default'
status: completed
type: bug
priority: normal
created_at: 2026-09-21T04:28:52Z
updated_at: 2026-09-21T04:34:17Z
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

## Landed — and the pessimism above was wrong

`main-sha: 1390334acf6bed646e4df314e6bc731407161504` — agent 0.1.79.
Full gate green: 1675 specs, 843 features, 0 failures. Deployed to zanebot.

The "Scope, honestly" section above predicted this would not move GLM. **It did.**
Four probes on 0.1.79, every one of them batched:

| probe | assistant messages with tool calls | calls per message |
|---|---|---|
| rr7u-glm2 | 1 | 2 |
| rr7u-t1 | 1 | 2 |
| rr7u-t2 | 1 | 2 |
| **rr7u-t3** | **2** | **2 and 2** |

Against 0 of 253 tool responses during the cgxa bean run. GLM emits parallel
tool calls when the request asks for them and does not when it doesn't —
Fireworks was applying a non-parallel default we never chose.

Correction to the record: the earlier claim that "GLM will not batch even when
told to in plain English" was not sound. That conclusion came from reading
`:tool-calls-count 1` lines in server.log which belonged to the cgxa bean run,
not to the probe — `isaac prompt` runs the turn in the CLI process and does not
write to server.log. The 253-response measurement from the bean run stands; the
one-off probe never was evidence.

Real-work batching rate on GLM is still unmeasured. The next bean it takes will
show it.
