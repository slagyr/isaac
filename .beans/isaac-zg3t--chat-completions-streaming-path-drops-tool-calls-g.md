---
# isaac-zg3t
title: chat-completions streaming path drops tool calls; GLM-5.3 agentic turns die as :empty-terminal-response
status: in-progress
type: bug
priority: high
tags:
    - unverified
    - llm
    - providers
created_at: 2026-09-21T02:46:54Z
updated_at: 2026-09-21T03:03:15Z
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


## Worker handoff (2026-09-21, scrapper@isaac-work-1)

Branch **isaac-agent `bean/isaac-zg3t`** @ `7cfe69b` (base `origin/main` 4cd20fc), pushed. `bb ci` green: config-bypass-lint ok, lint-cli-host ok, **1668 spec examples / 0 failures / 3459 assertions**, **843 feature examples / 0 failures / 2006 assertions / 1 pending** (the pending predates this bean).

### The fix — `src/isaac/llm/api/chat_completions.clj`

- New private `merge-tool-call-fragment` folds one `delta.tool_calls` fragment into the calls seen so far, **keyed by `:index`**: the opening fragment carries `id`/`type`/`function.name`, later fragments extend `function.arguments` a few characters at a time, and two parallel calls interleave. `id`, `type` and `name` are taken from whichever fragment carries them; `arguments` is appended as a string.
- `process-sse-event` now accumulates those fragments into `:tool-call-fragments`, a **sorted map** of index to partial call, so `vals` come back in the order the model opened them. Content, model, usage and finish_reason accumulation is unchanged.
- `chat-stream-with-completions-api` builds its result with **the same `extract-tool-calls`** the non-streaming path uses — identical ids, names and parsed arguments — and hands the real tool calls to `stop-reason`, which can now return `:tool-use`. The hardcoded `:tool-calls []` is gone.

### reasoning_content disposition — SURFACED, not dropped

`delta.reasoning_content` is forwarded as `{:reasoning-delta …}` from the stream callback. That is exactly what the claude/messages adapter does with `delta.thinking` (`messages.clj:212`) and what ollama does with `:thinking`: the drive turns it into `comm/on-reckoning`, so it reaches the comm live. It is **not** written to the transcript and **not** replayed to the model — transcript reckoning stays the provider-summary path (`turn.clj:1456`), unchanged by this bean. Recorded in a comment at the call site.

### Test harness — `src/isaac/llm/api/grover.clj`

The fixture could not have caught this bug: grover's Chat Completions SSE branch emitted content deltas only, never `delta.tool_calls`. It now streams each scripted tool call the way the wire does — an opening fragment with index/id/name, then **the arguments split across a second fragment** — sets `finish_reason` `tool_calls` when a call is present, and emits a `reasoning_content` delta when the scripted response carries `:reasoning {:summary …}`.

### Acceptance

| bean acceptance | where |
|---|---|
| streamed `delta.tool_calls` fragments execute the tool and the turn completes | `features/llm/api/chat_completions/openai_dispatch.feature:186` — grover:openai, real adapter, fragments over SSE, transcript shows `toolCall exec__run` → `toolResult` → assistant "Found crumbs!"; request asserted `body.stream true` |
| streaming and non-streaming agree on shape (ids, names, parsed arguments) | `chat_completions_spec.clj` "returns a streamed tool call, in the non-streaming path's shape" — asserts equality against the same logical response driven through `sut/chat` |
| `stop-reason` is `:tool-use` when a streamed response carries tool calls | spec "stop-reason is :tool-use when a streamed response carries tool calls", equal to the non-streaming path's |
| arguments split across chunks reassembled; two calls stay separate by index | specs "reassembles arguments split across chunks", "keeps two tool calls separate, merged by index", "keeps two streamed tool calls separate" |
| reasoning_content disposition recorded, with a scenario since surfaced | `openai_dispatch.feature:220` — comm receives `reckoning "Which jar?"` then `reply "Found crumbs!"`; spec "surfaces reasoning_content deltas as reasoning chunks" |

Also added: a schema-conformance example for a streamed tool-call response (`api/validate-response`).

### Notes for verify

- Gate: `bb bean-gate verify isaac-zg3t` → exit 2, `no feature-baseline` — ungated bean, so this goes to the verify band rather than a gated self-close.
- **Two new scenarios were added** to `features/llm/api/chat_completions/openai_dispatch.feature` (a non-baselined feature file). No existing scenario was reworded or removed. Acceptance criterion 1 ("executes the tool and the turn completes") cannot be shown at the spec level alone, and criterion 5 asks for a scenario when reasoning is surfaced.
- The shared `../isaac-agent` checkout is parked on another session's `bean/isaac-209q`; all work was done in worktree `../isaac-agent-zg3t`. Nothing in the shared checkout was touched.
- One flake seen once and not since, unrelated to this bean: spec "session feature steps parks a slow tool-loop send so a later cancel can still fire" (timing). Green on every subsequent run.
