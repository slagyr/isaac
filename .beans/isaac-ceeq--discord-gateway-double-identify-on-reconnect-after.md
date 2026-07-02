---
# isaac-ceeq
title: Discord gateway double-IDENTIFY on reconnect after opcode 7
status: todo
type: bug
priority: normal
tags:
    - discord
created_at: 2026-07-02T16:59:59Z
updated_at: 2026-07-02T16:59:59Z
---

## Symptom
Discord gateway heartbeats stop being emitted (no :discord.gateway/liveness or :heartbeat) even though the overall isaac service is up. The client gets into a dead state after certain disconnects/reconnects. Seen on zanebot 2026-07-01 ~22:01Z and similar cycles on 06-30.

In logs:
- opcode 7 (reconnect-requested)
- disconnects (4000 + 1000)
- reconnect attempt (identify)
- hello + TWO identifies
- ready
- heartbeat-cancelled "Already authenticated."
- reader-loop-failed "Output closed"

Result: no more heartbeats until full restart of the service/client.

## Root cause (in isaac-discord)
In src/isaac/comm/discord/gateway.clj:

- do-reconnect! (called for opcode 7, close codes, etc.) does:
  start-reader-loop!
  send-resume! or send-identify!   <--- eager send

- handle-hello! (op 10, which Discord always sends immediately on connect) always does:
  schedule-heartbeats!
  send-identify!   <--- unconditional

Normal initial connect! path works (no pre-send, hello triggers the single identify).

Reconnect path (after opcode 7 or non-fatal closes) does the double send. Discord rejects the second auth.

See:
- handle-reconnect-op! + on-close! + attempt-reconnect! + do-reconnect!
- handle-hello! lines ~138-149
- send-identify! / send-resume!

## Scope
isaac-discord gateway reconnect logic. Affects resilience of Discord comm (heartbeats, liveness, session continuity).

## Acceptance criteria (runnable)
- [ ] Reproduce the failing sequence in gateway_spec or feature (opcode 7 or 1000 close while "connected" → reconnect) and observe exactly one auth payload (op 2 or 6) sent, not two.
- [ ] After the reconnect, the client reaches :ready, schedules heartbeats, and emits :discord.gateway/liveness (no "Already authenticated" cancel or reader-loop-failed).
- [ ] Initial connect path (HELLO → single IDENTIFY) is unchanged.
- [ ] All existing reconnect specs in gateway_spec.clj still pass (re-identify, RESUME for resumable codes, etc.).
- [ ] No duplicate auth messages in the sent payloads for reconnect cases.
- [ ] (stretch) Add a dedicated spec or feature step exercising the opcode-7-while-ready path.

## References
- Prod logs: server-20260701.log around 22:01:40 (reconnect-requested → double identify → Output closed)
- Similar pattern in earlier 06-30 cycles
- Code: isaac-discord/src/isaac/comm/discord/gateway.clj (do-reconnect!, handle-hello!, on-close!)
- Specs: isaac-discord/spec/isaac/comm/discord/gateway_spec.clj (reconnect tests)
- Feature: isaac-discord/features/comm/discord/gateway.feature
