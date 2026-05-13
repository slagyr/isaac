---
# isaac-0c9x
title: Make cancel actually stop in-flight LLM, tools, exec, and slash work
status: todo
type: feature
priority: normal
created_at: 2026-05-12T22:55:39Z
updated_at: 2026-05-13T03:25:01Z
blocked_by:
    - isaac-yr1x
    - isaac-y0s2
---

## Problem

Pressing ESC in Toad mid-turn currently sets the `bridge/cancel!` flag,
but nothing in the turn pipeline reads it until the *whole tool-loop
returns*. Marvin's recent long turn ignored ESC for minutes because:

1. `src/isaac/llm/tool_loop.clj` has no cancellation check between
   chat/tool cycles.
2. `record-tool-call!` (`drive/turn.clj:638`) registers an on-cancel hook
   that only emits `comm/on-tool-cancel` — it does NOT signal the
   running tool to bail.
3. `chat-fn` / SSE streaming never aborts mid-response. We always read
   to completion.
4. Slash command handlers run synchronously with no opportunity to
   check `bridge/cancelled?`.

## Design contract

**Cancel stops *further* work, not *current* work.** Hard-killing a
tool mid-stride is dangerous — half-written files, broken `git`
rebases, locked databases, partial subprocess state. The safe model is
cooperative:

- Cancel sets a flag.
- The tool-loop checks the flag between iterations — this is the
  primary cancellation seam.
- Individual tools run to completion. Cancel takes effect when control
  returns to the tool-loop.
- Tools that *can* abort safely (HTTP requests, LLM SSE streams)
  optionally check the flag at safe points. This is opt-in per tool,
  not a framework requirement.
- `exec` does NOT get a forced subprocess kill. A subprocess might be
  midway through anything; the tool waits for it to exit. If the user
  needs to force-kill, that's a separate, explicit gesture (e.g., a
  `force-cancel`), not the default ESC.

## Proposed scope

### 1. Tool-loop cancellation (cheapest, biggest win)

`tool-loop/run` accepts a `:cancelled?` option `(fn [] -> bool)`. At the
top of each iteration (after `chat-fn` returns, before invoking
`tool-fn` for the batch), check it; if cancelled, return
`{:response last-response :tool-calls all-tools :token-counts ...
:cancelled? true}` and stop.

`execute-llm-turn!` passes `#(bridge/cancelled? session-key)` in. The
existing cond at `turn.clj:689-693` already maps `:cancelled?` →
`bridge/cancelled-result`.

This is the headline change. It alone resolves Marvin's "ESC did
nothing for minutes" case because each tool batch is bounded; cancel
lands within one iteration.

### 2. LLM SSE stream abort + remove leaky test workaround

`llm-http/post-sse!` plumbs a `cancelled?` token. The SSE read loop
breaks out when the token flips; the underlying http-kit connection
is closed, which cascades to the upstream LLM provider. Wire this
through `chat-stream` for each provider's `Api` implementation.

Safe to abort mid-stride: closing the SSE connection just discards
in-flight tokens. No local state corruption.

**Test-double cleanup:** `spec/isaac/features/steps/session.clj`'s
`turn-cancelled` step currently calls `(grover/release-delay!)`
between `bridge-cancel/cancel!` and `await-turn!`. That's a
provider-specific workaround that bypasses the bridge's cancellation —
the existing scenario at `features/bridge/cancel.feature:23` ("cancel
interrupts a running LLM request") only passes because the test
manually unsticks the LLM mock, not because the bridge actually
unwinds the chat call.

The fix:

- Make grover's `chat-fn` honor `bridge/cancelled?` (or whatever
  predicate the SSE abort wiring exposes) and short-circuit its
  delayed-response simulation from inside the chat-fn. This is the
  test-double's mirror of what real provider streams must do.
- Remove the `(grover/release-delay!)` call from `turn-cancelled`.
  The step becomes purely provider-agnostic: fire bridge cancel, then
  await the turn future.
- Audit other provider test doubles for the same kind of leak.

### 3. Opt-in cooperative cancellation for safe tools

`record-tool-call!` makes a `cancelled?` predicate available to tool
handlers (via runtime-injected argument or thread-local). Tools that
can safely abort opt in:

- **`web_fetch` / `web_search`**: HTTP requests are safe to cancel;
  the only side effect is "tokens-billed-but-discarded" on the
  upstream service. Use http-kit async with cancellable deferred.

Tools that are NOT modified:

- `read`, `write`, `edit`, `grep`, `glob`, `memory_*`: fast enough that
  cancellation between tool-loop iterations is sufficient.
- `exec`: deliberately not cancelled mid-flight (see Design contract
  above).

### 4. Slash command hooks

Slash handlers should be able to check `bridge/cancelled?` and bail at
safe points. Audit built-in slash handlers — most are sync and trivial.
Document the contract in `isaac.slash.registry` so module-contributed
slash commands can opt in.

### 5. Async compaction is already lifecycle-managed

Out of scope — has its own future/lock and isn't turn-bound.

## Acceptance scenarios

Committed under `@wip`:

- `features/bridge/cancel_aborts_work.feature:20` — "cancel between tool-loop iterations skips the next chat call"
- `features/bridge/cancel_aborts_work.feature:37` — "session remains usable after a cancel mid-loop"
- `features/bridge/cancel.feature:23` (existing) — "cancel interrupts a running LLM request" becomes a meaningful test once `grover/release-delay!` is removed from the `turn-cancelled` step. No `@wip` here because the scenario already exists and passes (tautologically); the change is removing the test workaround so it tests real behavior.

Run with: `bb features features/bridge/cancel_aborts_work.feature features/bridge/cancel.feature`

Definition of done: both new scenarios pass; the LLM-cancel scenario
still passes after the `grover/release-delay!` workaround is removed;
`@wip` tag removed from `cancel_aborts_work.feature`.

Deferred to follow-up beans:
- `web_fetch` honors cancel mid-request (needs HTTP-mock infra).
- Long-running slash command checks the flag (needs synthetic
  slash handler registration in the step file).

## Depends on

- isaac-yr1x (observability) should land first so we can confirm
  Toad's ESC is reaching the server.
