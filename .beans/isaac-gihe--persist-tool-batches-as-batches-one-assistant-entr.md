---
# isaac-gihe
title: 'Persist tool batches as batches: one assistant entry per batch, results in call order'
status: draft
type: bug
priority: normal
tags:
    - agent
    - tools
created_at: 2026-09-15T17:12:16Z
updated_at: 2026-09-15T17:12:16Z
---

## Problem

When a model response carries N tool calls, isaac executes them as one batch and sends all N results back in a single follow-up request (`tool_loop/-run-default` → `execute-tool-batch`, up to `:tools :max-parallel`, default 4; results assembled by `followup-fn` → `api/followup-messages`). Mid-turn the model sees the batch correctly.

The transcript does not record it as a batch:
- `announce-tool-call!` (`drive/turn.clj` ~1324) runs for every call before any tool starts and `persist-tool-call!` (~180) writes **one assistant entry per call**.
- `persist-tool-result!` writes each result when that tool finishes, so results land in completion order, not call order.

Whenever the prompt is rebuilt from disk (next turn, after mid-turn compaction, overflow retry via `rebuild-chat-request`), the batch is replayed as separate one-call assistant messages:
- Chat-completions (`prompt/builder.clj` `filter-messages-openai`): assistant(c1), assistant(c2), assistant(c3), tool(r2), tool(r1), tool(r3). OpenAI requires tool results immediately after the assistant message that requested them; this ordering likely violates that (unverified against the provider).
- Anthropic (`filter-messages-anthropic`): tool calls are dropped entirely and results become user text.
- Responses (ChatGPT): items are flat and matched by `call_id`, so replay is structurally valid.

Measurement impact: every zanebot session's transcript shows exactly one tool call per assistant message (2026-09-15 scan), while the server log for `isaac-work-1` shows 12 of 77 responses carried 2–6 calls. Hypothesis (unverified): replaying the model's own history as one-call-at-a-time steps discourages batching, working against the standing "Batch independent tool calls" hint (`llm/turn_instructions.clj`).

## Proposal

- Persist a batch as one assistant entry whose content holds all N `toolCall` items, in model order.
- Persist results in call order (buffer until the batch completes, or write with a batch index and order on read).
- Replay per API reconstructs the batch: one assistant message with N tool calls followed by N results in call order.

## Open questions (not decided)

- Streaming visibility: today each call is persisted as soon as it is announced. Writing the batch entry at announce time keeps that; buffering results changes when a result becomes visible on disk (crash mid-batch).
- Anthropic replay dropping tool calls entirely — separate bean, or in scope?
- Migration of existing transcripts (clean cutover per project stance, or tolerate old one-call entries on read?).

## Acceptance (draft — scenarios TBD)

- A response with 3 tool calls whose tools finish out of order produces one assistant entry with 3 calls and 3 results in call order.
- Rebuilding the prompt from that transcript yields, for chat-completions, one assistant message with 3 `tool_calls` immediately followed by 3 `tool` messages in call order.
- The transcript scan of calls per assistant message matches the server log's calls per response.
