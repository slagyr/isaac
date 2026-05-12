---
# isaac-6bfz
title: "WebSocket transport: handle pings, surface close codes, preserve errors"
status: completed
type: bug
priority: normal
created_at: 2026-04-30T05:22:13Z
updated_at: 2026-04-30T12:37:05Z
---

## Description

isaac.acp.ws/connect! is missing lifecycle handlers that cause
connections to stall and diagnostic data to be lost. Confirmed by
comparing against a working Discord gateway probe at
isaac-marvin/src/discord_probe.clj.

## Bug 1: missing onPing / onPong (the killer)

Java's WebSocket.Listener has default impls for onPing/onPong that
return null and DO NOT call \`.request\`. Per JDK docs and the probe's
own comment: \"Java's WebSocket listener is backpressure-aware. If
you don't call request, frames stop.\"

When Discord's gateway (fronted by Cloudflare) sends a WS-layer ping,
the JDK auto-responds with pong (correct) but never calls .request.
The connection stalls — no further frames arrive. Looks dead from
isaac's perspective.

Fix: override onPing and onPong, both must call \`(.request ws 1)\`
before returning.

## Bug 2: onClose discards status-code and reason

src/isaac/acp/ws.clj line 146-149:

  (onClose [_ _websocket _status-code _reason]
    (reset! closed? true)
    (queue-closed! incoming)
    (completed-future))

The status-code and reason are received but ignored. Callers see
only a closed sentinel that becomes nil. Already known to have
caused 'connected -> hello -> identify -> disconnected' debug
sessions where the actual close reason was invisible.

Fix: queue a structured close event {:type :closed :status-code N
:reason \"...\"} or expose them via a separate API. Update
isaac.comm.discord.gateway to handle the structured close.

## Bug 3: onError swallows the throwable

src/isaac/acp/ws.clj line 150-153:

  (onError [_ _websocket error]
    (reset! closed? true)
    (queue-message! incoming {:error error})
    nil)

The error is queued but never logged. The probe logs it and prints
the stack trace. Production errors are silent.

Fix: log via isaac.logger with the error as :throwable; queue the
structured event for callers but also surface to logs.

## Test strategy

Unit spec on the listener — construct it with mock websocket,
invoke onPing/onClose/onError directly, assert observable effects:

- onPing calls websocket.request(1)
- onClose enqueues a structured event with status-code and reason
- onError logs the throwable

Loopback transport doesn't simulate pings, so unit-level mocks are
the right surface.

## Definition of done

- onPing and onPong call .request(1)
- onClose surfaces status-code and reason to callers
- onError logs at error level with throwable
- isaac.comm.discord.gateway updated to handle structured close
  (probably routes via existing close-handling path with the new
  context)
- bb spec green; manual smoke: Marvin's Discord gateway stays
  connected for >5 minutes (Cloudflare pings every ~few minutes)

## Related

- isaac-???? (rename/move bead): isaac.acp.ws is used by Discord
  gateway too, so it doesn't belong in the acp namespace. Land
  the rename either before or after this fix; not blocking.

## Notes

Verification failed: src/isaac/util/ws_client.clj stores real close payloads as {:status-code ... :reason ...}, but src/isaac/comm/discord/gateway.clj close-status only checks :status and :code. That means real ws-close-payload values will not drive the resume/reidentify close-code handling the bead requires. Current gateway specs only cover callback payloads shaped as {:status ...}, so this path is still unverified and incorrect.
Verification failed: automated checks are now passing for this bead, but the definition of done also requires a manual Discord gateway smoke test (>5 minutes connected with real pings). That smoke is not evidenced here, so the bead cannot be closed yet.

