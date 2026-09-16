---
# isaac-lnf7
title: Discord gateway liveness log includes heartbeat RTT
status: in-progress
type: task
priority: normal
tags:
    - discord
    - unverified
created_at: 2026-09-16T14:47:41Z
updated_at: 2026-09-16T14:54:33Z
---

## Problem
Every Discord Gateway heartbeat interval (~41s on zanebot) writes three log lines: info `:discord.gateway/liveness`, debug `:discord.gateway/heartbeat` (opcode 1 send), debug `:discord.gateway/heartbeat-ack` (opcode 11). The ack is another frame on the same WebSocket, not an HTTP response. Operators want one pulse that includes how long the ack took.

## Design (2026-09-16, Micah)
Not request/response. Stamp send time on opcode 1. Opcode 11 stamps ack time. The heartbeat scheduler task fires once per Discord `heartbeat_interval` (HELLO). On that tick, emit **one** info `:discord.gateway/liveness` with `:status`, `:sequence` (last acked), `:rtt-ms` (ack − sent), `:last-ack-ms-ago`. Drop both debug events. Keep warn paths (`heartbeat-ack-timeout`, `liveness-stale`, `heartbeat-cancelled`, `heartbeat-failed`).

Timestamps use the gateway scheduler clock so tests and interval math share a time domain.

## Likely repo scope
isaac-discord (`src/isaac/comm/discord/gateway.clj`)

## Acceptance
- [ ] `bb features features/comm/discord/gateway.feature:55`
- [ ] `bb jvm-spec spec/isaac/comm/discord/gateway_spec.clj`
- [ ] No `:discord.gateway/heartbeat` or `:discord.gateway/heartbeat-ack` log events on the healthy path

## Done (2026-09-16, plan@isaac-lnf7)
Stamp `:last-heartbeat-sent-at-ms` from the gateway scheduler clock. Opcode 11 stamps ack time. Next heartbeat tick emits one info `:discord.gateway/liveness` with `:status`, `:sequence`, `:rtt-ms`, `:last-ack-ms-ago`. Dropped debug send/ack events. Warn paths unchanged. Branch `bean/isaac-lnf7`. `bb ci` green (3 pre-existing pending episode scenarios).

## Next
Verify on isaac-discord `bean/isaac-lnf7`:
```
bb features features/comm/discord/gateway.feature:55
bb jvm-spec spec/isaac/comm/discord/gateway_spec.clj
```
