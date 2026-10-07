---
# isaac-9qv9
title: Responses tool calls take arguments from the done events; ChatGPT skips deltas for parallel calls
status: todo
type: bug
priority: high
created_at: 2026-10-07T14:12:58Z
updated_at: 2026-10-07T14:12:58Z
---

Likely repo: **isaac-agent**. Found by Foreman pilot 1, 2026-10-06; probed 2026-10-07.

## Why

Parallel tool calls on the ChatGPT crews arrive with empty arguments: 26 of 32
parallel calls in the pilot, 199/777 on isaac-work-3, 155/298 on tono-work-3; single
calls never. Each empty call fails ("unknown prompt: ", exec "Error: ") and the model
retries the batch.

Probe (planner, gpt-6-sol via chatgpt.com/backend-api/codex, 6 runs asking for two
parallel calls): in 4 runs the backend sent NO `function_call_arguments.delta` for
either call; in 2 it sent one whole delta per call. In every run
`response.function_call_arguments.done` carried the full `arguments` and
`response.output_item.done` carried the finished item (`name`, `arguments`,
`call_id`). Our parser (`llm/api/responses.clj` `process-responses-sse-event`) builds
arguments only from deltas and ignores both done payloads, so no deltas → `{}`.

OpenAI's Codex client (`codex-rs/codex-api/src/sse/responses.rs`) logs
`function_call_arguments.delta/.done` as unhandled and builds tool calls from
`response.output_item.done`.

## Design

- A call's arguments come from its done events: `output_item.done`'s item (name,
  arguments) is authoritative; `function_call_arguments.done`'s `arguments` too.
  Deltas are progress only (fallback for a stream that ends without done events).
- New feature step `the next Responses stream is:` — grover replays the table's rows
  as the next Responses SSE response (dotted columns build nested `item` maps; blank
  cells omitted).
- Note, not in the contract: we answer `function_call_output` with the `fc_…` item id
  as `call_id`; Codex uses the item's `call_id`. It works today — leave unless the
  fix touches it naturally.

## Acceptance

- isaac-agent `features/llm/api/responses/tool_calls.feature` (whole file).
- The rest of isaac-agent features stay green.

feature-baseline: isaac-agent 08174d7db3835f4711b6675983943b3ce345445e
feature-blob: isaac-agent features/llm/api/responses/tool_calls.feature 99fa55b924c0cc2cbde0074fb1ab570f075bea40
