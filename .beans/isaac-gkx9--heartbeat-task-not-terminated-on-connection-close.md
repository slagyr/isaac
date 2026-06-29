---
# isaac-gkx9
title: 'Heartbeat task not terminated on connection close (IOException: Output closed loop)'
status: todo
type: bug
priority: high
created_at: 2026-06-29T14:50:59Z
updated_at: 2026-06-29T15:08:47Z
---

On zanebot, /tmp/isaac.log shows a scheduled task firing every ~41s with :scheduler/handler-error :error 'java.io.IOException: Output closed' (e.g. task :isaac.sched.auto/9e04f7c8..., 2026-06-29 14:31-14:37+). A periodic heartbeat-style task keeps writing to a WebSocket whose output is already closed because the heartbeat was not cancelled when the connection closed. Same CLASS as isaac-igs4 (heartbeat outliving its connection); igs4 fixed the leak-on-reconnect, this is the close path.

## Which component (confirm)
'Output closed' is a JDK java.net.http.WebSocket error. The ACP SERVER heartbeat (isaac-acp acp/websocket/heartbeat.clj) uses http-kit send! (returns false on closed, does NOT throw this). So the culprit is a JDK-ws CLIENT heartbeat: most likely the ACP proxy (acp --remote) or the discord gateway. Confirm from zanebot's log context around task 9e04f7c8 (what connection :close/:fatal precedes the first Output closed).

## Fix
Cancel/terminate the heartbeat scheduled task when its connection closes (the close/fatal path must deregister + stop the heartbeat) so it stops hammering the dead socket. Verify against the live zanebot /tmp/isaac.log (note: a local copy was a different test log).

## CONFIRMED from zanebot log (2026-06-29): it's the DISCORD gateway, not ACP

Sequence before the first Output closed (zanebot /tmp/isaac.log):
  22:05:28 :discord.gateway/heartbeat :sequence 1
  22:06:06 :ws/error + :discord.gateway/error IOException "Can't assign requested address"
  22:06:06 :discord.gateway/disconnected :reason "closed"
  22:06:09 :scheduler/handler-error "Output closed" -> 3581 times since, ~every 41s (~1.7 days)

Root cause: a network blip ("Can't assign requested address") dropped the discord gateway ws; the gateway disconnected and did NOT reconnect; its heartbeat scheduled task was never cancelled, so it keeps sending a heartbeat into the dead JDK WebSocket every ~41s -> IOException: Output closed.

Same CLASS as isaac-igs4 but a DIFFERENT gap: igs4 cancels the prior heartbeat on RE-SCHEDULE (reconnect). Here there was NO reconnect, so nothing cancelled it — the DISCONNECT/on-close path (isaac-discord gateway.clj on-close!/disconnected) does not cancel the heartbeat task.

## Fix
Cancel the heartbeat scheduled task on disconnect (in the gateway on-close!/disconnected path), independent of reconnect — so a disconnect that does not reconnect doesn't leave the heartbeat firing into a dead socket. (Earlier ACP guess was wrong; server ACP heartbeat cancels cleanly, proxy uses a retry task — neither is the culprit.)

## Regression scenario
Given the discord gateway connected with a scheduled heartbeat
When it disconnects (ws closed / network error) without reconnecting
Then the heartbeat task is cancelled — no further heartbeats, no :scheduler/handler-error "Output closed".
