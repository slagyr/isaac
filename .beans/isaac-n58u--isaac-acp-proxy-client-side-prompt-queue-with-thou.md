---
# isaac-n58u
title: 'isaac-acp proxy: client-side prompt queue with thought-chunk feedback'
status: in-progress
type: feature
priority: normal
created_at: 2026-06-11T16:21:36Z
updated_at: 2026-06-11T16:43:52Z
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
