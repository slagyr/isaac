---
# isaac-j2v0
title: 'Parallel tool batches: execute a response''s tool calls concurrently (bounded), results in batch order'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
    - agent
    - tool-loop
created_at: 2026-09-04T00:14:58Z
updated_at: 2026-09-04T07:04:50Z
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


## Decisions ratified (2026-09-03, Micah) — supersede the "proposed" section

Bounded concurrency (`tools.max-parallel`, default 4, 1 = serial); followup
order = batch order; transcript = all toolCalls persisted in batch order before
execution, each toolResult persisted on completion (completion order on disk,
paired by id); cancel mid-batch = in-flight calls run to their own
cancellation, queued calls never start and get on-tool-cancel; one call's
error is its own result. Batch independence is the model's contract.

**No wall-clock in the passing path.** Test-double tools block on conditions,
never sleeps; their one-second ceilings run only when the implementation is
wrong (rendezvous returns "alone", gate returns "gate never opened").

## Scenarios (committed @wip at slagyr/isaac-agent ce8138f)

`features/session/parallel_tool_batches.feature` — 6 scenarios:
1. rendezvous: two calls in one batch overlap (both tool-call events precede
   both tool-result events; both results "met").
2. batch order vs completion order: gated slow + quick; comm sees quick first,
   the followup request carries slow then quick.
3. transcript: both toolCall rows before any toolResult; results paired by id.
4. `tools.max-parallel` config knob, default 4 (`config get`).
5. cancel mid-batch at max-parallel 1: in-flight blocking tool and the queued
   tool both report tool-cancel; nothing after runs.
6. one error (fs__read missing file) + one success in the same batch.
Migrated @wip: `parallel_tool_calls.feature` la8h scenario → calls-first
transcript + id pairing.

## Unit-spec obligations (not Gherkin — no clean observable without timers)

`spec/isaac/llm/tool_loop_spec.clj`: a fake tool-fn blocking on a latch,
recording concurrent invocations —
- bound 2, batch of 3: `await-condition` until exactly 2 in flight, third not
  started; release; all 3 results in batch order.
- bound 1: never more than 1 in flight; results in batch order.
- errors in one call don't abort the others; cancellation flag stops queued
  calls (loop-level twin of scenario 5).

## Step ledger (final)

| step | status |
|------|--------|
| default Grover setup / built-in tools registered / sessions exist / queued `tool_calls` batch (la8h fixture) / user sends (sync via memory comm + async form) / memory comm has events matching / transcript matching + not matching / the last LLM request matches / the turn is cancelled … after N tool calls / the turn result is / config: / an Isaac root at / isaac is run with / stdout contains | reuse |
| **a rendezvous tool {name} is registered that returns {string} once {n:int} calls are in flight** | **NEW mock — counter of in-flight calls; waits (≤1s) for n; returns "alone" on ceiling** |
| **a gated tool {name} is registered that returns {string} once tool {other} has completed** | **NEW mock — latch tripped by the named tool's completion; ceiling returns "gate never opened"** |
| **a blocking tool {name} is registered that returns cancelled once the turn is cancelled** | **NEW mock — polls the bridge cancel flag for its session_key, returns `{:error :cancelled}`** |
| **every toolResult in session {key} pairs with a toolCall by id** | **NEW — each toolResult's toolCallId matches an earlier toolCall id in the transcript** |
| `tools.max-parallel` | config schema row (positive int, default 4) |

## Worker notes

- `tool-loop/run`: replace the serial `mapv` with bounded concurrent
  execution (futures + a semaphore or a fixed pool sized by the bound);
  gather in batch order. Cycles stay sequential.
- `drive/turn.clj record-tool-call!` splits into announce (on-tool-call +
  persist toolCall, serial, batch order) and execute/complete (per call,
  concurrent; persist toolResult + on-tool-result on completion). Keep the
  per-call cancel bookkeeping; add the queued-call path (never started →
  on-tool-cancel, no toolResult).
- Futures convey dynamic bindings in Clojure and bb; confirm nothing in
  `tool-registry/tool-fn` or the fs nexus assumes a single thread.
- The mocks live in `spec/isaac/tool/tools_steps.clj` beside `test__sounding`.
- Message indexes in scenarios 2 and 6 assume system, user, assistant-with-
  calls, tool, tool — fix the INDEX to the real request shape, never the
  behaviour. Same for the toolResult `message.content[0].text` path.
- Scenario 5 mixes the async send with the memory-comm event table; align to
  whichever harness the cancel step's channel-events read.

## Acceptance

Remove @wip from `features/session/parallel_tool_batches.feature` and the
migrated scenario in `parallel_tool_calls.feature`, then in isaac-agent:

    bb features features/session/parallel_tool_batches.feature features/session/parallel_tool_calls.feature
    bb spec spec/isaac/llm/tool_loop_spec.clj spec/isaac/drive
    bb features features/comm/scuttlebutt.feature features/llm/tool_loop_driver.feature features/bridge/cancel.feature

0 failures; full `bb spec` + `bb features` green; no `Thread/sleep` in the
new steps or specs. Hand off `--tag=unverified`, status stays in-progress.

## Work observations (2026-09-04, scrapper@isaac-work-1)

Implemented bounded concurrent execution for one response's tool-call batch on branch `bean/isaac-j2v0` @ `8a79a55` (base `origin/main@2277cb8`).

Delivered:
- `src/isaac/llm/tool_loop.clj`: bounded concurrent batch execution with `tools.max-parallel` defaulting to `4`, preserving followup `tool-results` batch order.
- `src/isaac/drive/turn.clj`: announce/persist all `toolCall` entries before execution, then persist/emit each `toolResult` on completion; preserve queued vs in-flight cancellation behavior.
- `src/isaac/tool/registry.clj`: raw `execute` result maps plus `present-result` normalization for transcript/model payloads.
- spec/feature harness updates for rendezvous, gated, and blocking mock tools; transcript pairing by id; config-default coverage.
- removed `@wip` from `features/session/parallel_tool_batches.feature` and the migrated scenario in `features/session/parallel_tool_calls.feature`.

Verification:
- `bb features features/session/parallel_tool_batches.feature features/session/parallel_tool_calls.feature` → `9 examples, 0 failures, 23 assertions`
- `bb features features/comm/scuttlebutt.feature features/llm/tool_loop_driver.feature features/bridge/cancel.feature` → green
- `bb spec spec/isaac/llm/tool_loop_spec.clj spec/isaac/drive` → green
- `bb spec` → `1625 examples, 0 failures, 3342 assertions, 3 pending`
- `bb features` is not suite-stable in this workspace: one full run passed (`762 examples, 0 failures, 2034 assertions`), but reruns fail intermittently outside this bean's surface at `features/episodes/live.feature:604` (`without embedding, drift is inert but the size cap still seals`, expected `1` scene got `0`). The same scenario passes in isolation with `bb features features/episodes/live.feature:577`.

Conclusion: implementation and focused acceptance are complete, but the bean's full-`bb features` gate is currently blocked by a pre-existing cross-feature suite-health / state-isolation flake unrelated to parallel tool batches. Returning to planner for acceptance adjustment or a split suite-health bean.


## Planner adjustment (2026-09-04, prowl@isaac-plan) — focused gates control; split the full-suite live-feature flake

Conflict resolved: this bean's product is bounded concurrent execution of one response's tool-call batch. The returned red is a **full-suite-only** failure in `features/episodes/live.feature:604` that passes in isolation and does not exercise the parallel tool-batch surface. This bean does not absorb it.

New suite-health owner: **isaac-tx3j** — `features/episodes/live.feature:604` (`without embedding, drift is inert but the size cap still seals`, expected `1` scene got `0`) flaking only in the full suite.

### Acceptance adjustment

Keep the focused and adjacent regression gates that actually measure this bean. Drop the requirement that full `bb features` be green for **isaac-j2v0**.

Controlling acceptance in `isaac-agent` is:

    bb features features/session/parallel_tool_batches.feature features/session/parallel_tool_calls.feature
    bb features features/comm/scuttlebutt.feature features/llm/tool_loop_driver.feature features/bridge/cancel.feature
    bb spec spec/isaac/llm/tool_loop_spec.clj spec/isaac/drive
    bb spec

0 failures on each, no `Thread/sleep` in the new steps/specs, and the focused feature/spec rows remain un-`@wip`.

### Full-suite note

If the worker has a clean full `bb features` run on the branch, record it as supporting evidence. But PASS for this bean must not be blocked on rerunning a suite-only `episodes/live.feature:604` flake that already passes in isolation.

Standing rule restated: do not weaken scenario intent to fit the suite; file ambient full-suite reds as dedicated suite-health work instead.
