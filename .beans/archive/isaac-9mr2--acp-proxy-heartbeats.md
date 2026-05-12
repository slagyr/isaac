---
# isaac-9mr2
title: "ACP proxy heartbeats"
status: scrapped
type: feature
priority: deferred
created_at: 2026-04-30T05:22:24Z
updated_at: 2026-04-30T12:20:02Z
---

## Description

The ACP proxy (isaac acp --remote, src/isaac/cli/acp.clj +
isaac.acp.*) currently doesn't send keepalive heartbeats to the
remote ACP server's WebSocket. Long-idle connections may be reaped
by intermediate proxies (Cloudflare, NATs, load balancers).

## Goal

Periodically send a heartbeat frame to keep the WS connection
alive when the user isn't actively prompting. Either:
- Application-layer heartbeat (a JSON-RPC ping method we define)
- WebSocket-level pings (RFC 6455)

Discord's reference probe sends application-level heartbeats via
op 1 / op 11 on a fixed interval received from the server's HELLO.
ACP doesn't have an analog yet.

## Status

Deferred. Real impact: idle ACP sessions might disconnect
silently. Once the lifecycle-fix bead lands, the existing
reconnect logic should recover, but heartbeats would prevent
the disconnect in the first place.

## Related

- (sibling fix) WebSocket transport: handle pings, surface close
  codes, preserve errors
- isaac-i5i3 / isaac-pr5a (closed): ACP reconnect work

