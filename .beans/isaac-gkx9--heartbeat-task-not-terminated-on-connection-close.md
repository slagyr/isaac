---
# isaac-gkx9
title: 'Heartbeat task not terminated on connection close (IOException: Output closed loop)'
status: todo
type: bug
priority: high
created_at: 2026-06-29T14:50:59Z
updated_at: 2026-06-29T14:50:59Z
---

On zanebot, /tmp/isaac.log shows a scheduled task firing every ~41s with :scheduler/handler-error :error 'java.io.IOException: Output closed' (e.g. task :isaac.sched.auto/9e04f7c8..., 2026-06-29 14:31-14:37+). A periodic heartbeat-style task keeps writing to a WebSocket whose output is already closed because the heartbeat was not cancelled when the connection closed. Same CLASS as isaac-igs4 (heartbeat outliving its connection); igs4 fixed the leak-on-reconnect, this is the close path.

## Which component (confirm)
'Output closed' is a JDK java.net.http.WebSocket error. The ACP SERVER heartbeat (isaac-acp acp/websocket/heartbeat.clj) uses http-kit send! (returns false on closed, does NOT throw this). So the culprit is a JDK-ws CLIENT heartbeat: most likely the ACP proxy (acp --remote) or the discord gateway. Confirm from zanebot's log context around task 9e04f7c8 (what connection :close/:fatal precedes the first Output closed).

## Fix
Cancel/terminate the heartbeat scheduled task when its connection closes (the close/fatal path must deregister + stop the heartbeat) so it stops hammering the dead socket. Verify against the live zanebot /tmp/isaac.log (note: a local copy was a different test log).
