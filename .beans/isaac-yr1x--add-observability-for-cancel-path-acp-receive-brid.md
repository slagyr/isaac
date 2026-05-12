---
# isaac-yr1x
title: Add observability for cancel path (ACP receive + bridge apply)
status: draft
type: task
created_at: 2026-05-12T22:55:19Z
updated_at: 2026-05-12T22:55:19Z
---

## Problem

When a user hits ESC in Toad mid-turn and nothing visibly happens, we
currently have no way to tell whether:
- the cancel JSON-RPC message arrived,
- the bridge accepted the cancel,
- or the message was silently dropped (wrong method name, transport
  backpressure, unknown session).

Existing logs:
- `:acp-ws/session-cancel` (websocket.clj:80) at `:debug` — invisible at
  default info level.
- `session-cancel-handler` (server.clj:185-188) emits nothing.
- `bridge-cancel/cancel!` emits nothing.

## Proposed scope

- `log/info :acp/session-cancel-received` in `session-cancel-handler`,
  including the session-id and raw params.
- `log/info :bridge/cancel-applied` inside `bridge-cancel/cancel!`,
  including the session-key and count of on-cancel hooks fired.
- `log/info :bridge/cancel-noop` when `cancel!` is called for an unknown
  session-key (separate event so we can tell "session not running" from
  "transport never delivered").
- Bump `:acp-ws/session-cancel` from debug to info? Not necessarily —
  the handler-side log will cover the in-flight case. Keep ws-level
  debug for now.

Spec coverage targets: `spec/isaac/comm/acp/server_spec.clj` for the
handler log, and a new test (or extension) in
`spec/isaac/bridge/cancellation_spec.clj` for the bridge log shapes.

## Acceptance scenarios (TBD)

This bean is in `draft` because the acceptance scenarios haven't been
written yet. To promote to `todo`:
- Draft test scenarios (specs or gherkin) covering the three log events.
- Commit them.
- Then update this bean's body to reference the scenario locations and
  flip status to `todo`.

## Sibling bean

Pairs with the "actually stop turn work on cancel" bean. This one is
strictly observability — it lets us confirm whether the cancel signal
is even arriving before doing the bigger work.
