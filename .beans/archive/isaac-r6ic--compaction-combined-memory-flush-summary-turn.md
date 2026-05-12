---
# isaac-r6ic
title: "Compaction: combined memory-flush + summary turn"
status: completed
type: feature
priority: normal
created_at: 2026-04-21T22:48:09Z
updated_at: 2026-04-22T16:37:51Z
---

## Description

Today compaction is a tool-free LLM call that just summarizes the old transcript. This bead enhances compaction to a single combined turn: the agent gets memory_write (and optionally memory_get / memory_search) in the request, plus a dual-purpose prompt asking to both (a) call memory_write for durable facts and (b) return a summary as its final text output. The summary lands as the compaction entry's summary field.

Halves the per-compaction token cost vs OpenClaw's two-call pattern (memory flush turn + summary turn). Full transcript only traverses the LLM once.

Tool surface during the compaction turn is restricted to memory_* tools. Non-memory tools (read/write/exec/etc.) are NOT exposed even if the crew otherwise has them allowlisted — safety invariant, covered by unit spec.

See features/context/memory_flush.feature for the 2 @wip scenarios.

## Acceptance Criteria

1. Modify compaction to run a single tool-enabled turn with memory_* tools and a combined prompt.
2. Agent's final text is used as the compaction summary.
3. Memory tool calls during the turn persist via the normal memory_write path.
4. Tool surface is restricted to memory_* tools regardless of crew allowlist (unit spec).
5. Remove @wip from both scenarios in features/context/memory_flush.feature.
6. bb features features/context/memory_flush.feature passes.
7. bb features passes overall.
8. bb spec passes.

## Design

Implementation notes:
- Modify isaac.context.manager/compact! (and the chat-fn it calls) to:
  1. Pass tools: [memory_write, memory_get, memory_search] in the request (not tool-free).
  2. Use a combined prompt: 'Review this conversation. Call memory_write for anything durable the user will want later. Then produce a concise summary of what happened. Output only the summary, no preamble.'
  3. Capture the agent's final text output as the summary (existing path). Tool calls during the turn run through the normal tool dispatch (memory_write persists to disk).
- Only the memory tools are passed regardless of crew :tools :allow — deliberately narrower than the general allowlist.
- If the agent makes no memory_write calls, no memory file is created — that's fine; the summary still lands normally.
- Depends on isaac-1pqn for the memory tools to exist.

## Notes

Verification failed: Scenario 1 fails because memory_write is never executed during the compaction turn. Root cause: turn.clj:337 passes (partial dispatch/dispatch-chat ...) as the :chat-fn but dispatch-chat calls grover/chat with no tool loop — the LLM tool_call response is returned as data but never dispatched to tool-fn. Should use dispatch-chat-with-tools instead. The file assertion fails because the memory file is never written. Criterion 3 (memory tool calls persist) and criterion 6 (feature passes) are not met.

