---
# isaac-5n68
title: Batching reminder is stranded in the cached system prefix; move it beside the tool results
status: todo
type: task
priority: normal
created_at: 2026-09-21T04:58:11Z
updated_at: 2026-09-21T04:58:11Z
---

## The diagnosis (GLM's, corroborated)

`parallel-tool-calls-hint` (`isaac-agent/src/isaac/llm/turn_instructions.clj`)
is appended by `build-system-text` — the cached system prefix. In a 100K-token
tool loop the model reads it once, ~100K tokens before the moment it decides
what to call next.

Evidence that placement, not capability, is the constraint:

| instruction placement | batched |
|---|---|
| in the user message, one step from the decision (4 probes) | 4 of 4 |
| **in the system prefix, real bean work (cgxa + nq4c)** | **0 of 418** |

`parallel_tool_calls: true` is now on the wire (isaac-rr7u) and made no
difference to real work. It is permission; it is not a reminder. The
accumulator (isaac-zg3t) and `execute-tool-batch` already handle batches.

## Why the crew soul is NOT the fix

The obvious cheap move — add the rule to scrapper's soul — puts it in the same
place. `soul` is the FIRST element `build-system-text` joins, so a soul rule
lands in the identical cached prefix the hint is stranded in. It is free to
try, but it does not test the hypothesis: if churn continues you have learned
nothing.

## Where the reminder actually belongs

Isaac already has a per-turn framing slot, `inject-turn-framing` in
`prompt/builder.clj` — but it attaches the block to the LAST USER MESSAGE and
only fires when `guidance` is present. In a tool loop that is the wrong end:
every cycle appends assistant + tool-result messages after it, so the framing
drifts further from the decision with each round trip.

The proximate slot is the tool result. `isaac.llm.followup/append-followup-messages`
is shared by the chat-completions, messages and responses adapters — one place
covers every provider.

## Done when

- a short reminder ("Issue all independent tool calls in this response
  together; wait only when one call's output feeds the next") rides the last
  tool result of each cycle
- it is one line, not a restatement of the whole tool-discipline block, and it
  does not accumulate — one copy per cycle, no stacking across cycles
- it is suppressible, so a provider that already batches well is not nagged
- measured: batching rate on a real GLM bean before and after, from
  `:tool-calls-count` in server.log, not from a probe

## Also worth trying, independently

A/B `reasoning_effort` on the glm-5-3 model entry. High effort plans
fine-grained (call, observe, decide); medium may plan coarser and batch more.
Config-only, hot-reloads, and the session gauge can now measure the difference
(isaac-f5tn).
