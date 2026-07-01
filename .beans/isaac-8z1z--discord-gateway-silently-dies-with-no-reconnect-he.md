---
# isaac-8z1z
title: Discord gateway silently dies with no reconnect (heartbeat scheduler task stops; op7/op9 unhandled; no ack watchdog)
status: todo
type: bug
priority: high
tags:
    - discord
    - comm
created_at: 2026-07-01T15:50:51Z
updated_at: 2026-07-01T15:50:51Z
---

## Symptom (production, zanebot)

Discord went offline **silently**. On the boot at `2026-06-30T22:53Z` (pid 77948, discord
`e914a6d`) the gateway connected fine — `gateway/ready` `22:53:53`, session `748b65f9`.
**Last heartbeat + ack: `2026-07-01T01:34:03Z`.** Then **zero gateway activity for ~14h** —
no heartbeat, no ack, no `gateway/close`, no reconnect, no error. `comm_send` dead-letters;
no inbound. The server itself stayed healthy (processing chat turns as late as `14:32Z`).

## The scheduler is fine — the heartbeat TASK stopped

Heartbeats are a **recurring task on the shared scheduler** (`isaac.scheduler.runtime` via
`scheduler/every!`, `nexus/get :scheduler` — gateway.clj:90–106), **not** a dedicated
thread. The scheduler kept firing *other* tasks long after 01:34 (cron heartbeat prompt at
`05:00Z`). So the scheduler is intact; only the discord heartbeat task stopped executing.

## Why there's no error

The heartbeat task's `try/catch` logs `:discord.gateway/heartbeat-failed` + calls `on-close!`
on a send exception (gateway.clj:96–105) — that log is **absent**, so `send-heartbeat!` did
not throw. The scheduler logs handler errors (`scheduler/handler-error` is present for other
tasks) — **none** for the heartbeat. So the task stopped **without an exception** — consistent
with a silent `cancel-heartbeat!` (from `on-close!`/`stop!`) whose reconnect never engaged.

## Concrete gaps in gateway.clj (e914a6d)

1. **Unhandled control opcodes.** `handle-frame!`'s `case (:op message)` handles only op 10
   (hello) / 11 (ack) / 0 (dispatch); **no case for op 7 (Reconnect) or op 9 (Invalid
   Session)** → they fall through to `nil` and are **silently ignored**. A Discord-initiated
   reconnect/resume request is dropped with no trace.
2. **No zombie detection.** `:last-heartbeat-ack-sequence` is recorded on ack (line ~181)
   but never **compared** against the sent sequence before the next heartbeat — so a
   missed ack (dead connection) is never detected, and no reconnect is triggered.
3. **Reconnect never fired.** `schedule-reconnect!` exists and is robust (backoff,
   `retry-attempts Long/MAX_VALUE`), but no `:discord.gateway/reconnect-attempt` was logged.
   Investigate whether the reader loop died without calling `on-close!`, or `on-close!`
   cancelled the heartbeat without scheduling a reconnect.

## Impact

Discord silently goes offline and never recovers until a manual restart. **Distinct from
isaac-86qy** (which leaked a heartbeat that spammed "Output closed" errors); here there is
no error at all — the failure is invisible.

## Fix direction

- Handle op 7 / op 9 in `handle-frame!` → trigger reconnect (resume / identify).
- Add a heartbeat-ack **watchdog**: if the prior heartbeat wasn't ack'd by the next
  interval, treat the connection as a zombie → `on-close!` → reconnect.
- Guarantee every path that cancels the heartbeat (`on-close!`, reader-loop death) also
  **schedules a reconnect and logs it**.
- Emit a liveness warning when no ack arrives within N intervals, so this is never silent again.

## Related

- isaac-86qy — earlier reconnect / leaked-heartbeat issue (different symptom).
- The reconnect machinery already present in gateway.clj (`schedule-reconnect!`).
