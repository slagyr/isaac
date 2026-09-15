---
# isaac-gihe
title: 'Persist tool batches as batches: one assistant entry per batch, results in call order'
status: in-progress
type: bug
priority: normal
tags:
    - agent
    - tools
created_at: 2026-09-15T17:12:16Z
updated_at: 2026-09-15T17:47:06Z
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

## Decisions

- Decision (2026-09-15, Micah): Anthropic replay dropping tool calls is out of scope here — isaac-lddb (blocked by this bean).
- Decision (2026-09-15, Micah): no migration of existing transcripts. Old one-call assistant entries keep replaying as they do today; only new batches are written batch-shaped.
- Results must land on disk in call order. Whether to buffer results until the batch completes or write with a batch index and order on read is the implementer's call; record which, and what a crash mid-batch leaves on disk.

## Open questions (not decided)

- Streaming visibility on disk: today each call is written as it is announced. Keeping the batch's assistant entry written at announce time preserves that; note any change in when a result becomes visible.

## Scenarios

Scenarios approved 2026-09-15 (Micah). Committed `@wip` in isaac-agent `515a40b`; no new steps.

`features/session/parallel_tool_batches.feature`
- `:141` the transcript records a batch as one assistant entry, with results in call order (isaac-gihe) — **replaces** `:58` "the transcript records every call before any result, and results pair with calls by id"
- `:161` one call fails and the other succeeds — results are recorded in call order (isaac-gihe) — **replaces** the completion-order transcript Then in `:114`
- `:179` a batch rebuilt from the transcript replays as one assistant message with every call, then the results in call order (isaac-gihe) — **new**

At landing:
- Delete the scenario at `:58`.
- In `:114` "one call fails and the other succeeds — each result is its own, the cycle completes", delete its `session "on-deck" has transcript matching:` step (quick done before broken winch); keep its memory-comm and last-LLM-request assertions. `:161` now owns the transcript order.
- Remove `@wip` from `:141`, `:161`, `:179`.
- `:16`, `:38`, `:80`, `:85` stay unchanged.

## Acceptance

```
ISAAC_GIT=1 bb features features/session/parallel_tool_batches.feature
bb ci
```

After deploy (evidence): a transcript scan of calls per assistant message on zanebot matches the server log's calls per response for new turns.
