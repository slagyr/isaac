---
# isaac-siua
title: 'Stateful Responses chaining resends every historical tool result: chained cycles must carry only the outputs since the last response id'
status: in-progress
type: bug
priority: high
tags:
    - llm
    - responses
created_at: 2026-09-19T18:08:14Z
updated_at: 2026-09-19T18:10:36Z
---

## Bug (observed live on zanebot, 2026-09-19 18:06Z — see isaac-1umd)

With `:stateful true` on `models/grok-4-6.edn`, a two-cycle turn on `isaac-work-3` chained correctly (cycle 2 carried `previous_response_id`, xAI 200) but cycle 2's body was 844,856 chars against 958,425 for the full-context cycle 1. The session transcript holds 253 tool-result entries totalling 871,655 chars: the chained request resends **every** historical tool result, not just the outputs since the last response id. Chaining therefore saves almost nothing on long-lived worker sessions, which is exactly where it matters (isaac-7l5m's motivation: ~1 MB × ~80 cycles per work turn).

## Root cause (isaac-agent `src/isaac/llm/api/responses.clj`, `->responses-request`)

```clojure
input (if chained?
        (->> all-messages
             (remove #(= "system" (:role %)))
             (filter #(or (= "tool" (:role %)) (= "function_call_output" (:type %))))
             ...)
```

`all-messages` is the full prompt (whole transcript + this turn's messages). The filter keeps every tool-role message ever recorded. The landed scenario (`features/llm/api/responses/stateful.feature`, "cycle 2 chains … sends only the new tool results") passes only because its session is empty, so the one tool result in the transcript *is* the new one.

## Fix

The chained `:input` must contain only the tool outputs (`function_call_output` items) produced **after the response identified by `previous_response_id`**: the results of the tool calls made in the immediately preceding cycle. Two acceptable shapes; the worker picks one and states it in the bean:

1. The tool loop (`isaac.llm.tool-loop`, which already tracks `chain-id` and appends each batch's results to `req`) passes the new results explicitly (e.g. `:chain-input` = this batch's tool-result messages) and the adapter uses that when `chained?`; or
2. The adapter takes the messages **after the last assistant message** of the request (everything appended since the previous response) and filters those.

Either way: no historical tool results, no user/assistant replay, `instructions` still omitted on chained cycles, `store true`, fallback-to-full-context on `previous_response_id … not found` unchanged. Unit specs in `spec/isaac/llm/responses_spec.clj` cover: transcript with earlier tool results → chained input has only the new batch; multi-call batch → all of that batch's outputs, in call order; cycle 3 carries only cycle 2's outputs.

## Scenarios (committed `@wip` in isaac-agent `7ffd522`, `features/llm/api/responses/stateful.feature`, branch `bean/isaac-siua`)

| scenario | asserts |
|---|---|
| cycle 3 chains from cycle 2 and carries only cycle 2's tool output | request 3: `previous_response_id resp-2`, `input.#count 1`, `input.0.type function_call_output` |
| a later turn's chained cycle does not resend tool results from earlier turns | turn 2 cycle 1 (request 3) unchained; request 4: `previous_response_id resp-3`, `input.#count 1` |

Existing scenarios in that feature stay green.

## Step ledger

| step | status |
|---|---|
| the isaac EDN file … exists with: / the crew … allows tools: / the following sessions exist: / the following model responses are queued: / the user sends … on session … / outbound HTTP request N matches: / outbound HTTP request N has no … | reuse |

No new steps.

## Acceptance

`@wip` removed and

```
cd isaac-agent && bb spec spec/isaac/llm/responses_spec.clj spec/isaac/llm/tool_loop_spec.clj && bb features features/llm/api/responses/stateful.feature && bb ci
```

Post-deploy (not a scenario): on zanebot, a work-turn cycle 2+ `:body-chars` in `server.log`/`cli.log` drops from ~850 KB to the size of that cycle's tool outputs plus the tool catalog. Record the before/after numbers in this bean.

## Exceptions

(none)

Dispatched: hail 5d4494a1 2026-09-19T18:09:37Z (band isaac-work)
