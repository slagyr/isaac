---
# isaac-n58u
title: 'isaac-acp proxy: client-side prompt queue with thought-chunk feedback'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-11T16:21:36Z
updated_at: 2026-06-11T16:52:23Z
---

When a session has a turn in flight, the isaac-acp proxy (the
`isaac chat --remote` / `isaac acp --remote` process bridging stdio
to a remote WebSocket) should hold new user prompts on a per-session
local FIFO queue and surface the queued state via a thought chunk —
instead of forwarding the prompt to the server (which silently
refuses it today and gives the client no feedback).

When the in-flight turn's `stopReason: end_turn` arrives, the proxy
pops the next queued prompt and forwards it automatically.

## Why client-side, in the proxy

- Server stays simple — no bridge/queue surface to add, no per-session
  state for queued prompts on the server, no interaction with the
  dead-comm-syndrome problem.
- Queue lives where the user is. No network round-trip for queue
  operations.
- Every ACP client that uses the isaac-acp proxy gets this UX for
  free; custom clients that talk the WebSocket directly can implement
  their own queue if they want.

## Configuration

- Queue depth cap: **10 prompts per session**. Over-cap prompts are
  rejected with a distinct thought chunk and NOT queued.
- Cancellation:
  - First `session/cancel` → forwarded to server as today (cancels
    the live turn).
  - Second `session/cancel` while the queue has prompts → proxy
    clears its local queue locally with a "queue cleared" thought
    chunk; NOT forwarded to the server.
- Connection drop handling: auto-flush. If the server WebSocket
  reconnects, the proxy continues with its existing queue. When the
  in-flight turn ends (or the reconnect surfaces an end_turn for the
  in-flight turn), the queue drains as normal.

## Implementation seam

`isaac-acp/src/isaac/comm/acp/cli.clj` — `run-remote` already carries
per-invocation atoms (`session-id*`, `pending-request*`, etc.).
Add:

- Per-session map `:in-flight?` (bool) + `:queue` (vector of charges
  / pending forward payloads).
- The stdin-reader thread, when it sees a `session/prompt`, checks
  the per-session in-flight flag:
  - Not in-flight → forward; set in-flight true.
  - In-flight, queue under cap → push to queue + emit thought chunk
    via stdout; do NOT forward.
  - In-flight, queue at cap → emit "queue full" thought chunk; do NOT
    forward.
- The websocket-reader thread, when it sees a notification carrying
  `stopReason: end_turn` (or `cancelled`), clears the in-flight flag,
  pops the next queued prompt, and forwards.
- The stdin-reader, when it sees `session/cancel`, checks: if
  there's a queue AND a previous cancel within ~Nms (whatever feels
  right; or just "the queue is non-empty"), treat this one as
  "clear queue" — emit "queue cleared" thought chunk; do NOT forward.
  Otherwise (first cancel of the round) → forward to server.

## Feature

`features/proxy/queue.feature` — four `@wip` scenarios:

- happy path: single prompt queued during in-flight; auto-forwards on
  end_turn.
- multiple queued prompts drain FIFO.
- queue at cap rejects the over-cap prompt with a thought chunk.
- double `session/cancel`: first cancels live turn, second clears
  the queue locally.

## Acceptance

- Remove `@wip` from `features/proxy/queue.feature`.
- `bb features features/proxy/queue.feature` passes.
- isaac-acp's existing spec + features still pass; no regression.
- Manual smoke from Micah's `zane-chat`: type a prompt while marvin's
  mid-task; see a "queued" thought chunk in the UI; when marvin
  finishes the current turn the queued prompt fires automatically.

## Out of scope

- Server-side queueing or dead-comm-syndrome fix. Different concern,
  filed separately if/when needed.
- Queue position updates as the queue drains ("3 ahead → 2 ahead →
  …"). Start with single "queued" thought; iterate later if it's
  worth it.
- Persistence across proxy process restarts. If the proxy dies, the
  queue dies. Users retype. Probably fine.

## Dependencies

- None hard. The proxy already has the threading scaffolding; this
  is purely behaviour on top.
- The WebSocket-keepalive bean (isaac-zlx4) is independent — even
  with no keepalive, the proxy's queue stays intact across server
  drops because it's in-process state, not on the wire.

## Notes for the worker

- The thought-chunk shape isaac-acp already emits elsewhere (see
  `acp-comm/thought-update` or similar). Reuse that helper rather
  than handcrafting JSON.
- Pay attention to ordering when multiple prompts arrive faster than
  the proxy can write thought chunks back — FIFO must hold strictly.
- The `session/cancel` is currently forwarded blindly; the new logic
  inspects a small bit of proxy state to decide. Keep that decision
  local to the proxy; don't add bridge-level coordination.

## Exceptions

### `@wip` left on `features/proxy/queue.feature`; no step definitions added

The bean's acceptance specifies removing `@wip` from `features/proxy/queue.feature` and getting `bb features features/proxy/queue.feature` green. Both are blocked by the **same pre-existing isaac-acp ↔ isaac SHA drift documented under isaac-zlx4 and tracked by isaac-lyg0:** the wider acp spec/feature suites abort at load time (`Protocol not found: clojure.lang.IHashEq` in `instaparse/auto_flatten_seq.clj`, plus stale namespace refs against the pinned isaac SHA). The `bb features` runner doesn't reach scenario execution today regardless of any changes I make to this feature file.

Rather than:
- (a) remove `@wip` (creates a scenario that's red for a known-pre-existing reason), or
- (b) hand-write step definitions I can't run end-to-end (likely to ship with subtle bugs),

I'm leaving the feature `@wip`-tagged with the queue logic + proxy wiring landed. When isaac-lyg0 lands and the acp suite comes back, removing `@wip` and writing the step definitions should be a focused follow-up (the proxy already exposes `:acp-proxy-queue-state*` as an opts seam so tests can pre-populate state, and the loopback transport in `isaac.util.ws-client` already gives the acp_steps file what it needs).

The bean's behavioral contract is exercised today by the **focused unit spec** at `isaac-acp/spec/isaac/comm/acp/cli/queue_spec.clj` (13/13, 38 assertions) which covers all four feature scenarios at the state-transition level.

## Summary of Changes

### `isaac-acp/src/isaac/comm/acp/cli/queue.clj` (new)

Per-session prompt queue state machine, isolated from the proxy's threading model so it's trivially testable:

- `fresh-state` / `session-state` / `pending-count` / `in-flight?` — read views.
- `handle-prompt` — decides `:forward` (no in-flight turn for this session), `:queue` (in-flight + under cap), or `:reject` (in-flight + at cap). Mutates the queue atom and returns the decision + the original line.
- `handle-cancel` — decides `:forward` (first cancel of a round, OR queue empty) or `:clear-queue` (>= 2nd consecutive cancel with non-empty queue). Resets the cancel counter on clear.
- `handle-turn-end` — called when the server returns `stopReason: end_turn`. Returns `[:idle]` (no queued prompts; session goes idle) or `[:drain next-line]` (head of FIFO popped, session stays in-flight).
- Frame predicates: `prompt-line?`, `cancel-line?`, `turn-end?`, `message-session-id`.
- `queue-cap` (= 10) plus the canonical thought-chunk text strings (`queued: held until the current turn finishes`, `queue full: prompt dropped (cap is 10)`, `queue cleared`).

### `isaac-acp/spec/isaac/comm/acp/cli/queue_spec.clj` (new)

13 examples, 38 assertions, all green. Covers all four feature scenarios at the state-transition level:

- **happy path** — first prompt forwards + sets in-flight; subsequent prompts queue in FIFO.
- **multi-drain** — handle-turn-end pops the FIFO head each call and stays in-flight until empty, then goes idle.
- **cap** — 11th prompt returns `:reject` and the queue stays at 10 (over-cap prompts aren't queued).
- **double-cancel** — first cancel `:forward`s + leaves queue intact; second cancel with non-empty queue `:clear-queue`s without forwarding; second cancel with empty queue `:forward`s as a normal cancel.
- Plus per-session isolation, frame-predicate edge cases, and session-id extraction from both `params` and `result`.

### `isaac-acp/src/isaac/comm/acp/cli.clj` (wired)

- Required the new `isaac.comm.acp.cli.queue` ns.
- Added `write-thought-chunk!` — direct session-id'd thought-chunk emit (the existing `write-status-notification!` resolves session-id from a cached atom, which is the wrong shape for queue callbacks that know exactly which session the message is for).
- Added `handle-stdin-line!` — pre-forward interceptor for `session/prompt` and `session/cancel` frames. Anything else (initialize, session/new, …) flows straight through to the original `forward-input-line!` path. Prompts dispatch via `queue/handle-prompt`; cancels via `queue/handle-cancel`. Non-forward decisions emit the canonical thought-chunk via stdout.
- Added `drain-queue-on-turn-end!` — called from the remote-thread for every inbound frame. Checks `queue/turn-end?`, looks up the session id (preferring the explicit one in the response, falling back to `session-id*` for the common single-session case), signals the queue, and forwards the popped line if any.
- `run-stdin-thread!` / `run-remote-thread!` / `run-remote` now thread `queue-state*` through their arglists. `run-remote` seeds the atom (or uses `opts :acp-proxy-queue-state*` when tests inject one).
- The remote-thread continues to write inbound frames straight to stdout BEFORE consulting the queue — the user sees the `end_turn` response unchanged, and the queued prompt fires immediately after.

### Acceptance checks

- `bb spec spec/isaac/comm/acp/cli/queue_spec.clj`: 13 examples, 0 failures, 38 assertions.
- The queue ns + proxy wiring compile cleanly under bb (verified standalone `require`).
- Feature scenario acceptance: deferred to the isaac-lyg0 unblock (see Exceptions). Documented for re-handoff once the suite is runnable.
