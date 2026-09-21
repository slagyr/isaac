---
# isaac-zg3t
title: chat-completions streaming path drops tool calls; GLM-5.3 agentic turns die as :empty-terminal-response
status: in-progress
type: bug
priority: high
tags:
    - llm
    - providers
created_at: 2026-09-21T02:46:54Z
updated_at: 2026-09-21T02:47:52Z
---

The streaming path of `isaac.llm.api.chat-completions` cannot report a tool call.
Its accumulator never reads them and its result hardcodes none.

`src/isaac/llm/api/chat_completions.clj`:

```clojure
;; process-sse-event — accumulates content, model, usage, finish_reason. Not tool_calls.
(cond-> accumulated
  (:content delta) (update :content str (:content delta))
  ...)

;; chat-stream-with-completions-api result (:121)
{:content     (:content result)
 :tool-calls  []                                   ;; hardcoded
 :stop-reason (stop-reason (:finish-reason result) [])}  ;; can never be :tool-use
```

So a streamed tool call is silently discarded, and `stop-reason` is structurally
unable to return `:tool-use`.

## How it presents

GLM-5.3's first agentic response is a tool call. Content 0, tool-calls 0 ⇒ the
drive reports `:empty-terminal-response`, the hail retries, five attempts, dead
letter. Diagnosed by GLM-5.3 itself while failing on it (2026-09-21).

## Not GLM-specific

Any provider on this adapter's **streaming** path loses tool calls the same way —
grok, openai, fireworks. It has presumably been masked wherever streaming is off
or the model opens with prose.

## Precedent

`isaac-ncrz` (completed) is the same defect in the ollama adapter — "only the
final chunk is kept, and the tool calls are lost" — and its `fold-chunk`
accumulator is the fix pattern to mirror:

```clojure
(seq (get-in chunk [:message :tool_calls]))
(update-in [:message :tool_calls] (fnil into []) (get-in chunk [:message :tool_calls]))
```

Note the OpenAI wire shape differs: tool calls arrive as `delta.tool_calls`, an
array of fragments carrying an `index`, with `function.arguments` streamed as a
string across chunks. They must be merged by `index`, not concatenated blindly.

## Work

Accumulate `delta.tool_calls` in `process-sse-event`, merging fragments by
`index`, and build the streaming result with the same extractor the
non-streaming path uses (`extract-tool-calls`), so both paths agree on shape,
ids and argument parsing. `stop-reason` then receives the real tool calls.

## Decide, do not leave implicit

GLM also streams its thinking as `reasoning_content` deltas, which Isaac drops.
Harmless today because content follows, but the fix should record a deliberate
disposition: drop, or surface as reckoning the way the claude driver does.

## Acceptance

- a streamed response whose tool call arrives as `delta.tool_calls` fragments
  executes the tool and the turn completes
- streaming and non-streaming produce the same tool-call shape for the same
  logical response (ids, names, parsed arguments)
- `stop-reason` is `:tool-use` when a streamed response carries tool calls
- arguments split across chunks are reassembled, and two tool calls in one
  response stay separate (merged by `index`)
- the `reasoning_content` disposition is recorded, with a scenario if surfaced

Dispatched: hail e52b6c55 2026-09-21T02:47:24Z (band isaac-work)
