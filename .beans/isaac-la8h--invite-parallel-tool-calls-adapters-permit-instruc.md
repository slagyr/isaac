---
# isaac-la8h
title: 'Invite parallel tool calls: adapters permit, instructions encourage, loop already executes'
status: in-progress
type: feature
priority: normal
created_at: 2026-07-08T23:07:55Z
updated_at: 2026-07-08T23:13:20Z
---

## Goal

Models take one tool action per LLM round-trip in Isaac — transcript analysis on zanebot (2026-07-08) found **6,561 of 6,561** assistant tool messages carried exactly one tool call, across gpt-5.4 and grok-composer alike (the same composer batches aggressively in Cursor's harness). Isaac's tool loop already executes whole batches (`tool_loop.clj` maps over all calls in a response); nothing advertises or invites it. At ~488 round-trips per bean, batching is the single biggest wall-clock lever.

## Design

1. **Verify the adapters permit parallel calls**: responses + chat-completions request builds must not disable `parallel_tool_calls` (default is enabled on both OpenAI-dialect and xAI); confirm tool definitions serialize per spec.
2. **Invite it in the drive instructions**: one line in the standing turn instructions — "When tool calls are independent (reads, greps, separate files), batch them in a single response." Wording lives in one place; no per-crew config.
3. Tool loop: no change (already executes all calls per cycle, serially, results in order).

## Out of scope

Parallel *execution* of a batch (serial `mapv` stays); model-specific prompt tuning.

## Scenarios (worker writes; required coverage)

1. A grover-scripted response carrying TWO tool calls in one message: both execute in order, the follow-up request carries both results, transcript records both toolCalls + toolResults. (Grover scripting gains multi-call-per-response support — likely a row-grouping or a calls column.)
2. Wire shape: the outbound request does not set `parallel_tool_calls` false (responses + chat-completions).

## Acceptance

- [ ] Scenarios green
- [ ] One-time on zanebot: after deploy, batch-size distribution over a real composer bean shows >1-call responses occurring (re-run the transcript analysis; if composer still refuses to batch, record that finding — it's model habit, not harness)
