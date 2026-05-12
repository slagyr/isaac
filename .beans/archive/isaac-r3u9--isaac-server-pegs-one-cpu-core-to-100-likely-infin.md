---
# isaac-r3u9
title: "'isaac server' pegs one CPU core to 100% — likely infinite loop"
status: completed
type: bug
priority: high
created_at: 2026-04-24T00:21:31Z
updated_at: 2026-04-24T00:48:45Z
---

## Description

On zanebot (macOS), starting 'isaac server' spawns a bb process that immediately consumes 100% of one CPU core indefinitely. Fan goes to full speed. Observed via Activity Monitor.

Suspected: an unbounded loop somewhere in the server startup path or a background thread (cron scheduler, delivery worker, ACP websocket handlers, server app state watchers). The server otherwise functions — it accepts connections and responds — so the hot loop is background, not request-serving.

Investigation starters:
- jstack / JFR on a running bb process to find the spinning thread
- Recent commits touching scheduler/worker/watcher code
- Check for Thread/sleep missing from a polling loop (common cause)
- Check for a tight (loop ...) that doesn't block on anything

Likely candidates to inspect first:
- src/isaac/cron/scheduler.clj (polling loop?)
- src/isaac/delivery/worker.clj
- src/isaac/server/app.clj startup code

Acceptance:
1. Root cause identified in a specific file/fn
2. Fix applied; idle 'isaac server' uses near-zero CPU on macOS
3. Feature or unit spec guards against reintroducing the hot loop

