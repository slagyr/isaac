---
# isaac-j2v0
title: 'Parallel tool batches: execute a response''s tool calls concurrently (bounded), results in batch order'
status: draft
type: feature
priority: normal
tags:
    - agent
    - tool-loop
created_at: 2026-09-04T00:14:58Z
updated_at: 2026-09-04T00:14:58Z
---

Repo: **isaac-agent** (`src/isaac/llm/tool_loop.clj`, `src/isaac/drive/turn.clj`
`record-tool-call!`, `features/session/parallel_tool_calls.feature`).
Planning 2026-09-03 (Micah + plan). Follow-on to isaac-la8h, which invited
batching but explicitly kept execution serial (`mapv`).

## Why

A batch of tool calls in one provider response is, by API contract, a set of
INDEPENDENT calls: the model gets every result back together and cannot see one
before issuing the next. Isaac honours the batch but runs it serially, so a
model that batches five greps waits for five round trips of wall clock instead
of one. At ~488 tool round-trips per bean (la8h analysis) this is the largest
remaining wall-clock lever in the loop.

## Design (proposed — settle before scenarios)

- **Execute a batch concurrently, bounded.** `tool-loop/run` executes the
  calls of one response through futures with a bound `:max-parallel-tools`
  (config `tools.max-parallel`, default 4; `1` = today's serial behaviour).
  Cycles remain sequential; only the calls WITHIN a cycle overlap.
- **Followup order = batch order.** `tool-results` is assembled in the order
  the model issued the calls, whatever the completion order, so
  `followup-fn` and the next request are unchanged and deterministic.
- **Transcript.** All toolCall entries persist in batch order BEFORE any
  execution starts (announce phase, serial). Each toolResult persists as it
  completes (keeps the l7lv mid-loop durability promise), so toolResult row
  order is completion order; consumers pair by tool-call id (the prompt
  builder already does). The la8h scenario's positional transcript assertion
  migrates to id-based matching.
- **Comm events.** on-tool-call in batch order (announce phase);
  on-tool-progress interleaves live; on-tool-result in completion order.
  ACP pairs by id; prompt_cli 🧰/← lines interleave (accepted).
- **Errors.** One call's error is its own result; the rest of the batch is
  unaffected (same as today).
- **Cancellation.** On cancel mid-batch: calls not yet started are never
  started and get on-tool-cancel; in-flight calls run to their own
  cancellation/return; the loop then raises cancelled as today.
- **Bindings.** Futures convey dynamic bindings (Clojure and bb), so nexus
  fs / test doubles carry into worker threads; `bridge/on-cancel!` and
  `tool-count` are atom-backed already. Verify `tool-registry/tool-fn`
  builds nothing per-call that assumes one thread.
- Side-effect independence within a batch is the MODEL's contract (as with
  every provider API), not the harness's problem — no dependency analysis.

## Scenario sketches (draft one at a time before @wip)

1. **Overlap is real:** two calls to a new *rendezvous* mock tool (returns
   only once N calls are in flight) complete in one cycle. Serial execution
   would deadlock/time out; this is the sharp assertion.
2. **Results in batch order:** call A (slow, streaming mock) + call B (fast):
   B finishes first; the followup request carries A's result then B's.
3. **Transcript:** both toolCall rows precede both toolResult rows; results
   match their calls by id.
4. **Bound:** three rendezvous calls with `tools.max-parallel 2` — the two
   rendezvous, the third runs after one completes; all three results present.
5. **Cancel mid-batch:** cancel while A is in flight; B (unstarted) gets
   on-tool-cancel and never runs; turn ends cancelled.
6. **One error, one success** in the same batch.
7. **Serial knob:** `tools.max-parallel 1` reproduces today's interleaved
   call/result event order (covers the config seam).
Migrate: la8h "runs both in order and persists both pairs" → id-based.

## Step ledger (preliminary)

| step | status |
|------|--------|
| default Grover setup / built-in tools registered / sessions exist / model responses queued (tool_calls JSON batch) / user sends / transcript matching / memory comm has events matching / last provider request | reuse |
| **a rendezvous tool {name} is registered that returns {string} once {n:int} calls are in flight** | **NEW mock (sibling of the streaming test__sounding mock)** |
| **the turn is cancelled while tool {name} is in flight** | **NEW — or reuse the cancellation.feature hook if it can target a tool** |
| config row `tools.max-parallel` | reuse (config: table) — needs schema entry |

Draft until the design decisions above are confirmed and scenarios are
committed @wip.
