---
# isaac-u129
title: 'imessage watch fails at boot: :imsg.watch/subscribe-failed Internal error (can''t receive)'
status: todo
type: bug
priority: high
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-19T22:22:26Z
---

On server boot, isaac.comm.imessage activates (:module/activated -> :comm/activated
{:comm "imessage"}) but then ERRORs:
  :imsg.watch/subscribe-failed {:error "Internal error"}
So imessage can SEND but the WATCH (receiving incoming messages) fails — the
iMessage assistant won't see inbound messages. Observed on zanebot foundation
server boot 2026-06-19 14:59:36.233.

"Internal error" is opaque. Fix: surface the REAL cause of the watch subscribe
failure (chat.db access / imsg binary / the watcher), and fix it. (isaac-imessage)
