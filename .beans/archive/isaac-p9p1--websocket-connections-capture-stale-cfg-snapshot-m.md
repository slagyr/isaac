---
# isaac-p9p1
title: "WebSocket connections capture stale :cfg snapshot, miss hot-reload"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T03:10:07Z
updated_at: 2026-04-28T19:06:32Z
---

## Description

Open WebSocket connections close over the :cfg value from connection
time and never re-read after a hot-reload. New tools.allow / soul /
model changes in a crew or model file are silently ignored on
already-open sockets until the server is restarted.

## Reproduction (observed on zanebot 2026-04-28)

1. Marvin connected via ACP WebSocket; tools.allow does not include
   :session_state.
2. Edit crew/marvin.edn to add :session_state.
3. Server logs :event :config/reloaded :path "crew/marvin.edn".
4. Ask Marvin "what tools do you see now?" — old list, no session_state.
5. Restart server. Marvin reconnects. session_state is now visible.

## Root cause

src/isaac/server/app.clj:84

  handler (http/wrap-logging
            (fn [request]
              (routes/handler (assoc opts :cfg @cfg*) request)))

The deref happens per HTTP request, but a WebSocket is one HTTP
request that upgrades — `routes/handler` calls into
`isaac.server.acp-websocket/handler`, which closes over the
snapshot. Subsequent frames go through `on-receive!` with the
captured opts, never the atom.

## Fix

Pass the cfg atom (or a thunk) through to long-lived handlers and
re-read on each frame. One option:

- routes/handler stops cooking :cfg into opts; instead injects
  :cfg-fn (fn [] @cfg*).
- Short-lived request handlers call (cfg-fn) once and proceed.
- acp-websocket calls (cfg-fn) inside on-receive! per message.

Audit other long-lived handlers (any future SSE / streaming) for the
same pattern while doing this fix.

## Spec — unit, not Gherkin

The bug is at the closure layer (routes/handler captures cfg once,
WS upgrade keeps that closure alive). Existing Gherkin ACP test
infrastructure dispatches at the JSON-RPC layer (dispatch-line),
bypassing routes/handler entirely — so it cannot reproduce this bug.
A real-WS @slow integration scenario would require multiple new
step types (open connection, hold, send-on-existing-connection)
when the actual bug is testable in three lines of clojure:

  1. construct routes/handler closure with cfg* atom
  2. swap! cfg* atom to a new value
  3. invoke the closure twice; assert each invocation reads the
     current atom value

Add unit spec at spec/isaac/server/app_spec.clj or
spec/isaac/server/routes_spec.clj.

## Definition of done

- Unit spec asserts routes/handler reads the cfg atom on every
  invocation, not at construction time
- Manual smoke test: open ACP WS, hot-reload tools.allow, send
  another prompt, observe new allow list honored without server
  restart
- bb features still green (no Gherkin scenario added; behavior
  validated at unit layer)

