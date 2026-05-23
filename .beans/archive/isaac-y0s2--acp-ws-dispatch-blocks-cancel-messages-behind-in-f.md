---
# isaac-y0s2
title: ACP WS dispatch blocks cancel messages behind in-flight session/prompt
status: completed
type: bug
priority: normal
created_at: 2026-05-13T03:24:53Z
updated_at: 2026-05-14T16:29:38Z
---

## Symptom

A user pressing ESC in Toad during a long Marvin turn observed:

- Five `session/cancel` messages sent during a 32-second turn.
- All five `:acp/session-cancel-received` log entries appeared in a
  60ms burst **after** `:session/stream-completed` — i.e., **after
  the turn ended**.
- `:acp-ws/session-prompt` (the dispatch log for the originating
  prompt) also fired post-turn, ~600ms after stream-completed.
- Every applied cancel logged `:hooks 0` — nothing in the turn was
  listening anyway, but they could not have acted on the signal even
  if they were.

Reproduced in zanebot's `/private/tmp/isaac.log` for the turn at
`2026-05-13T03:21:08Z` (turn/accepted) through `03:21:40.330Z`
(stream-completed). Cancel burst: `03:21:40.939`–`03:21:40.996`.

## Root cause

`src/isaac/comm/acp/websocket.clj:172` wires http-kit's `:on-receive`
directly to `(on-receive! opts request channel line)` which calls
`dispatch-line` **synchronously** on the worker thread:

```clojure
:on-receive (fn [channel line]
              (on-receive! opts request channel line))
```

http-kit serializes per-connection messages on a single worker thread.
A `session/prompt` handler that drives a full turn loop (LLM calls,
tools, streaming) holds that thread for the entire turn duration. No
other messages on that connection — including `session/cancel` —
can be dequeued until the handler returns.

The current observability logs (`:acp/session-cancel-received`,
`:bridge/cancel-applied`, etc.) only fire **after** the message
reaches the handler. The cancel can sit in http-kit's buffer for
the full turn duration before being seen.

## Why this blocks all cancel work

`isaac-0c9x` proposes a tool-loop cancellation check that reads
`bridge/cancelled?` between iterations. That works if the cancel flag
is set during the turn. Today, the flag cannot be set during the turn,
because the cancel handler that sets it is queued behind the prompt
handler that's running the turn. **No amount of in-turn polling helps
if the polled flag can never be set.**

## Proposed fix

Run long-running ACP handlers (at minimum `session/prompt`) on a
background thread so the WS receive loop returns promptly and can
process subsequent inbound messages — `session/cancel` in
particular — in real time.

Sketch:

```clojure
:on-receive (fn [channel line]
              (future (on-receive! opts request channel line)))
```

…with the necessary refinements:

- Bound thread pool so we don't unbounded-thread on misbehaving
  clients.
- Result writes (`send-dispatch-result!`) still need to be
  serialized per-connection to avoid interleaved JSON frames on the
  wire. Channel writes are already thread-safe in http-kit, but the
  outputs from a streaming response must arrive in order — confirm
  the existing send-line! ordering survives the move to background
  threads.
- Errors thrown in the future need to land in the log (currently
  `dispatch-line` catches and turns into JSON-RPC errors; verify
  that still happens off-thread).

Alternatively, only `session/prompt` is dispatched async and other
methods stay synchronous — but that adds complexity for little
benefit, since cancel and prompt are the only realistic interleaving.

## Acceptance scenarios (TBD)

This bean needs scenarios before promotion-to-implementation:

- Scenario A: while a `session/prompt` is in-flight (turn delayed),
  send `session/cancel`; assert the cancel-received log fires
  *during* the turn, not after.
- Scenario B: a `session/prompt` that takes N seconds does not delay
  a subsequent `session/cancel` notification by more than ~100ms.

These belong in `features/acp/cancellation.feature` or a sibling
features/acp/concurrency.feature. Both can be written as @wip and
committed before this bean is promoted from todo to in-progress.

## Relationship to other beans

- **Blocks isaac-0c9x** (make cancel actually stop in-flight work).
  The tool-loop check there is correct but unreachable until WS
  dispatch is async. Mark isaac-0c9x as blocked-by this bean.
- **Sibling of isaac-yr1x** (cancel observability) — that bean
  shipped and the logs are working; this bug exists because the
  logs revealed it.
- **Resolves the user-observable in isaac-q9b0** ("ACP turn
  cancellation does not work") more directly than the tool-loop
  fix does. With WS dispatch async, the existing
  `record-tool-call!` on-cancel hook (which currently just emits
  `comm/on-tool-cancel` notifications) would at least fire in real
  time, giving the UI a signal even before tool-loop check ships.



## Verification failed

The implementation commit for this bean (8ff11122) only changed `src/isaac/comm/acp/websocket.clj` and its unit spec. The bean body explicitly says it needed acceptance scenarios before promotion to implementation and names `features/acp/cancellation.feature` or a sibling concurrency feature, but no corresponding feature coverage landed with the bean. I am reopening it because the promised scenario-level verification for async WS dispatch is missing.



## Verification failed

The async websocket dispatch code and its unit spec are present, and `bb spec` plus `bb features features/acp/cancellation.feature` are green. But this bean's own acceptance text required scenario-level coverage proving `session/cancel` is observed during an in-flight `session/prompt` and not delayed behind it. No such feature scenario landed: `features/acp/cancellation.feature` still covers eventual cancellation and info-level logging, not the during-turn / <=100ms concurrency behavior promised by the bean. Reopening for the missing acceptance scenarios.
