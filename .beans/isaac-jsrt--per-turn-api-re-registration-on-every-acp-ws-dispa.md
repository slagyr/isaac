---
# isaac-jsrt
title: Per-turn API re-registration on every ACP WS dispatch
status: draft
type: task
priority: normal
created_at: 2026-05-13T18:49:08Z
updated_at: 2026-05-13T18:49:08Z
---

## Observation

While reading Marvin's zanebot log to debug ACP cancel timing, noticed a
burst of `:api/registered` events firing AFTER a `session/prompt` frame is
received, but BEFORE the turn actually begins processing:

```
17:18:14.153  acp-ws/frame-received session/prompt
17:18:14.413  api/registered anthropic
17:18:14.413  api/registered anthropic-messages
17:18:14.417  api/registered claude-sdk
17:18:14.417  api/registered grover
17:18:14.419  api/registered ollama
17:18:14.429  api/registered openai-compatible
17:18:14.429  api/registered openai-completions
17:18:14.434  api/registered openai-responses
17:18:14.437  slash/registered ...     (several)
17:18:14.440  module/activated isaac.comm.acp/register-routes!
17:18:14.606  turn/accepted
```

That's ~260ms of registration work between frame arrival and turn start,
on every ACP WS dispatch.

## Likely cause

`isaac.comm.acp.websocket/dispatch-line` wraps `run!` in
`system/with-nested-system` (websocket.clj around line 137):

```clojure
(if-let [state-dir (:state-dir server-opts)]
  (system/with-nested-system {:state-dir state-dir} (run!))
  (run!))
```

The nested system bootstrap appears to re-run module activation, which
includes API/provider registration and slash command registration. None
of that needs to happen per-request — APIs and slash commands should be
registered once at process start.

## Why it matters

1. **Latency:** ~260ms added to every ACP turn. Not catastrophic but
   measurable, especially for fast small turns.
2. **Correctness risk:** repeatedly registering the same APIs into a
   global registry may stomp state or accumulate handlers. Worth
   verifying registries are idempotent.
3. **Log noise:** the same eight `:api/registered` log lines on every
   request clutter the log.

## Scope of investigation

1. Confirm the events do fire per-request (not just at startup —
   timestamps suggest per-request but verify by triggering two prompts
   in a row and checking both have the burst).
2. Trace what `system/with-nested-system` does — does it re-init the
   module loader, or does it just rebind state vars?
3. Identify which module/loader call is the source of the registration
   events. Likely `isaac.module.loader/activated!` or similar.
4. Decide the fix: skip re-registration when the registry is already
   populated, OR don't activate modules inside the nested system.

## Out of scope

The cancel-handling bug we found this morning (`:hooks 0` on cancel
applied) is separate — see isaac-0c9x. This bean is purely about the
per-request startup cost.

## Definition of done

Investigation complete: a short writeup (in this bean or a follow-up
implementation bean) describing what's actually happening and the
recommended fix. No code changes required to close this bean —
implementation can be a separate ticket.
