---
# isaac-igs4
title: Discord heartbeat-task leak on reconnect (3s heartbeats + 'Send pending' IllegalStateException)
status: completed
type: bug
priority: high
tags: []
created_at: 2026-06-26T14:18:22Z
updated_at: 2026-06-26T14:27:42Z
---

Regression from isaac-dcr1 (reconnect after 1006), deployed in isaac-discord 2b6842e. Observed live on zanebot: discord heartbeats every ~3s (should be ~41s) and a recurring `java.lang.IllegalStateException: "Send pending"` logged as :scheduler/handler-error (scheduler/runtime.clj:294).

## Root cause: heartbeat task leaked on every reconnect
isaac-discord src/isaac/comm/discord/gateway.clj:
- `schedule-heartbeats!` (~81) starts a recurring task via scheduler/every! and stores :heartbeat-task-id, but NEVER cancels a prior task.
- `handle-hello!` (~88) calls schedule-heartbeats! on EVERY hello; `reconnect!` (~167) re-establishes the transport WITHOUT cancelling the running heartbeat task.
- The only cancel is in stop! (~294) — full shutdown only. And it can only cancel the LAST :heartbeat-task-id (prior ids were overwritten in state), so every earlier loop is orphaned and keeps firing.
- Net: each reconnect leaks one heartbeat loop. dcr1's auto-reconnect-on-1006 makes this accumulate over hours.

## Why the two symptoms
- Frequent heartbeats: N overlapping loops fire staggered -> effective interval ~41s/N. ~14 leaked loops -> ~3s (matches the log; :sequence stuck at 1, heartbeat every ~3s).
- "Send pending" IllegalStateException: the JDK WebSocket allows ONE outstanding send; concurrent heartbeat sends from multiple loops -> sendText while a prior send is pending -> throws.

## Fix
Make heartbeat scheduling idempotent — cancel the existing task before (re)scheduling:
- In schedule-heartbeats! (or at the top of handle-hello!/reconnect!): if (:heartbeat-task-id @state) present, (scheduler/cancel! sch task-id) using the stored :heartbeat-scheduler, THEN schedule the new one. Exactly one heartbeat loop at a time.
- This alone fixes BOTH symptoms (interval returns to ~41s; no concurrent sends).
- Optional defense-in-depth: serialize WebSocket sends (await the previous send's CompletableFuture) so any future overlap can't throw "Send pending".

## Scenario (draft — review/implement)
isaac-discord features/spec. A gateway that has connected, then reconnects (receives a second hello), should have exactly ONE active heartbeat task — i.e. handle-hello! cancels the prior :heartbeat-task-id before scheduling. Assert: after two hellos, only one scheduler task id is active (the prior was cancelled), heartbeat interval == the hello's heartbeat_interval. (Mirror existing gateway specs that drive hello/heartbeat.)

## Acceptance
- After any number of reconnects/hellos, exactly one heartbeat loop runs at Discord's heartbeat_interval (~41s).
- No recurring "Send pending" IllegalStateException.
- Spec covers reconnect -> single heartbeat task; verified green.
- Deploy to zanebot; confirm `isaac logs` shows ~41s heartbeats and no :scheduler/handler-error Send pending.

## Notes
Surfaced 2026-06-26 from zanebot logs (Micah). Not breaking (heartbeats still ack, discord works) but wasteful, log-spammy, and could spiral (a dropped heartbeat from Send pending -> 1006 -> reconnect -> more leaked loops). Stopgap applied 2026-06-26: restarted the service to reset to one loop (loops re-accumulate until this ships).

## Verification
Verified on fetched GitHub `isaac-discord` `main` at `769feef`. `schedule-heartbeats!` now cancels any existing `:heartbeat-task-id` via the stored `:heartbeat-scheduler` before scheduling the replacement task, so reconnect/second-HELLO leaves one active loop. `bb spec spec/isaac/comm/discord/gateway_spec.clj` passed: `57 examples, 0 failures, 121 assertions, 1 pre-existing pending` (pending in `discord_app_spec.clj`, unrelated to this bug).
