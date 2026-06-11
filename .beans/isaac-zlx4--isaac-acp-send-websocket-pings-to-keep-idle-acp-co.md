---
# isaac-zlx4
title: 'isaac-acp: send WebSocket PINGs to keep idle ACP connections alive'
status: in-progress
type: bug
priority: normal
created_at: 2026-06-11T15:31:49Z
updated_at: 2026-06-11T15:38:59Z
---

ACP WebSocket connections drop every ~60 seconds when idle.
Diagnosed from zanebot's `/tmp/isaac.log` 2026-06-11: a long-running
tool call locked session `tidy-comet`; the client couldn't send any
useful traffic, the connection stayed idle, and got reaped with
`:status :going-away` like clockwork at the 60s mark. The next
reconnect did the same.

This is the classic signature of a missing WebSocket keepalive —
something along the path (httpkit's idle timeout, Tailscale's NAT
state, or any HTTP-layer proxy) closes idle TCP connections, and
neither end is sending PING frames to keep the link warm.

## Symptom timeline (from the smoking-gun log)

\`\`\`
15:16:41  exec xcodebuild test … (20-minute timeout) starts
15:17:40  acp-ws/connection-closed   :status :going-away
15:17:42  acp-ws/connection-opened   (reconnect)
15:17:42  frame-received session/prompt
15:17:47  dispatch/refused reason :session-in-flight tidy-comet
15:18:47  connection-closed :going-away
15:18:49  connection-opened
15:19:49  connection-closed :going-away
15:19:51  connection-opened
15:20:51  connection-closed :going-away
…
\`\`\`

After the initial session-in-flight refusal at 15:17:47, the
reconnects don't even send prompts — they just open, idle, and get
reaped 60s later. The pattern continues until the busy tool returns.

## Fix

Server-side: emit WebSocket PING frames at a regular interval while
the connection is open. ~30 seconds is the standard pick (well under
typical NAT/proxy idle timeouts which start at 60s in the worst
case).

Implementation lives in `isaac-acp`'s WebSocket handler
(`src/isaac/comm/acp/websocket.clj`). httpkit's `as-channel` returns
an httpkit channel; periodic sends can be scheduled via
`isaac.scheduler` or a per-channel future. httpkit auto-responds to
inbound PINGs with PONGs, but it doesn't proactively send PINGs —
that's the gap.

Also worth: handle inbound PONGs (or just observe their absence over
some grace window) to detect a dead peer and close the channel
deliberately, rather than letting kernel TCP timeouts handle it
later. Optional for v1.

## Acceptance

- `isaac.comm.acp.websocket` sends a WebSocket PING frame every N
  seconds (default 30) for as long as the channel is alive.
- Interval is configurable via cfg key (e.g.
  `:acp :ping-interval-ms`) or a runtime option, falling back to a
  sensible default.
- Existing ACP scenarios still pass.
- Manual verification: start a long-running tool call on a session,
  let it run > 90s, confirm the WebSocket stays connected the entire
  time (no `:going-away` cycle in `/tmp/isaac.log`).
- No regression in `bb features` / `bb spec` across isaac core +
  isaac-acp.

## Out of scope

- Client-side PING (the acp proxy on the caller's machine). httpkit
  auto-PONGs inbound; the server-side PING is enough to keep the
  link warm.
- Heartbeat at the application layer (session/heartbeat messages
  etc.). WebSocket PINGs sit at the protocol level and don't need
  app-layer involvement.
- Retry policy changes on the client. The bug is "connection dies
  when idle"; once the keepalive is in place, reconnect frequency
  drops to near-zero in normal use.

## Notes for the worker

- httpkit channel handle is what you write PINGs into. The library
  surface is `org.httpkit.server/send!` with a control frame flag,
  or `ping` if a higher-level wrapper exists in the version isaac
  pulls. Check the version in isaac-acp's deps for the exact API.
- The ping scheduler should be per-channel, not global. Easiest:
  spawn a future on `on-open` that loops sending PINGs and exits on
  `on-close`. Or schedule recurring task on isaac's scheduler,
  cancelled in `on-close`.
- The :going-away status code in the log entries is the *received*
  close code, so we can't tell from logs alone whether the client
  or the server initiated. Doesn't matter for the fix — pinging
  the channel keeps either side from giving up.
