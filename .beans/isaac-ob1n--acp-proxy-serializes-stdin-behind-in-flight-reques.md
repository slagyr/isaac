---
# isaac-ob1n
title: ACP proxy serializes stdin behind in-flight request — cancels can't reach server mid-turn
status: draft
type: bug
priority: high
created_at: 2026-05-13T19:04:09Z
updated_at: 2026-05-13T19:06:45Z
---

## Problem

The ACP CLI proxy (`isaac.comm.acp.cli`) serializes stdin reads behind the
response to an in-flight JSON-RPC request. When Toad sends `session/prompt`
(an id-bearing request), the proxy enters `await-response!` and stops
reading from stdin until the matching response arrives. Anything Toad
writes to the proxy during that window — including `session/cancel`
notifications — sits in the proxy's input queue and is only forwarded
after the prompt's response lands.

End-user symptom: pressing ESC in Toad during a long turn appears to do
nothing. The cancel JSON-RPC is generated and written to the proxy's
stdin immediately, but the remote isaac server doesn't receive it until
the turn is already over.

## Evidence

`tcpdump` capture on zanebot (`/tmp/acp-cancel.pcap`) for a 31-second
prompt with three ESC presses spaced across the turn:

```
Toad → proxy stdin: session/prompt {id:N}
  proxy → remote WS: same frame                   11:52:37.677
  proxy enters await-response!(id=N) — blocked
  proxy forwards every server session/update chunk to Toad
User presses ESC × 3, ~5–10s apart during the turn
  Toad → proxy stdin: 3× session/cancel           ← sit in input-queue
  proxy is still in await-response!, can't see them
remote → proxy: streaming completes               11:53:07.706
remote → proxy: final {id:N stopReason}           11:53:08.428
  await-response! returns, remote-proxy-loop polls input-queue
proxy → remote WS: cancel #1 (len=104)            11:53:08.453  +25ms
proxy → remote WS: cancel #2 (len=104)            11:53:08.466
proxy → remote WS: cancel #3 (len=104)            11:53:08.479
```

All three cancels cluster within 26ms, right after the final response
packet from the server. The wire timing matches the
`handle-input-line!` / `await-response!` structure exactly — this is
not Toad's bug, and not http-kit serialization on the server side.

## Code shape (current)

`src/isaac/comm/acp/cli.clj`:

- `handle-input-line!` (line 340): sends the line to the remote WS, then
  if the line has an `id`, calls `await-response!` and blocks.
- `await-response!` (line 313): loops on `remote-queue*` only. Drains
  notifications + non-matching responses to stdout. Returns when it
  sees a message whose id matches the awaited one.
- `remote-proxy-loop` (line 383): polls `remote-queue*` first, then
  `input-queue` — but only outside of `handle-input-line!`, so during
  an in-flight request both queues are effectively serialized through
  the response-awaiter.

## Fix options

### Option A — minimal: stop awaiting in the input path

Remove the `await-response!` call from `handle-input-line!`. The proxy
just forwards each stdin line to the remote and returns immediately.
Responses arrive via `remote-queue*` and are written to stdout by the
existing remote-event handler (`handle-remote-idle-event!`).

The id-matching loop in `await-response!` exists today partly to drive
the remote-event drain during a request. Once the drain runs in the
main loop unconditionally, that role disappears.

Open questions:

- **Reconnect-during-request semantics.** Today, if the connection
  drops mid-request, `await-response!` returns `::retry` and the input
  line is resent after reconnect. Without awaiting, we need to track
  pending request IDs as state and replay on reconnect. The existing
  `acp_reconnect` feature scenarios will pin this down.
- **Backpressure.** If the server is slow, stdin can outpace the
  remote. Probably fine in practice (Toad rarely generates more than
  one request per turn), but worth noting.

### Option B — cleaner: two independent forwarder threads

Split into:
- stdin → remote: read stdin in a loop, send to WS, log
- remote → stdout: read WS in a loop, write to stdout, log
- shared "pending requests" set, updated by the stdin thread and read
  by the reconnect path

Same external behavior as A, but the proxy code becomes a pair of
well-known pipe forwarders instead of a poll-loop state machine.

### Recommendation

Go with **B**. A WS proxy is two independent pipes; modelling it that
way matches the actual semantics. Option A keeps the existing polling
loop only because it's already there — that's a smaller diff, not a
better design. The reconnect machinery doesn't depend on serialized
polling; a shared atom of pending request IDs is enough.

## Acceptance scenarios (TBD)

To draft once we pick A vs B. Sketch:

```gherkin
Scenario: cancel sent mid-turn reaches the remote before the response completes
  Given an ACP proxy connected to a remote that delays its response by 5 seconds
  When the user sends a session/prompt request
  And the user sends a session/cancel notification 1 second later
  Then the remote receives the session/cancel within 100ms of it being sent
  And the cancel arrives before the prompt's final response
```

A second scenario should pin down the reconnect-during-request path so
we don't regress that behavior.

## Definition of done

- Cancel notifications written to proxy stdin during an in-flight
  prompt are forwarded to the remote within tens of milliseconds, not
  delayed until the response completes.
- New Gherkin scenarios in `features/acp/` (likely
  `proxy_pipelining.feature`) cover the pipelining contract.
- Existing `cli_proxy_reconnect_spec` and the acp_reconnect feature
  continue to pass.
- bb spec and bb features green.

## Related

- isaac-0c9x: the bridge `:hooks 0` cancel-stops-work bean. That bug is
  real and still needs fixing — once this proxy bug is fixed, cancels
  will reach the server mid-turn but still won't stop anything until
  0c9x lands. The two are independent fixes for the same end-user
  symptom.
- isaac-yr1x: the observability bean whose `:bridge/cancel-applied` /
  `:bridge/cancel-noop` logs made it possible to triangulate this.
