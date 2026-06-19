---
# isaac-u129
title: 'imessage watch fails at boot: :imsg.watch/subscribe-failed Internal error (can''t receive)'
status: completed
type: bug
priority: high
tags:
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-19T23:15:00Z
---

On server boot, isaac.comm.imessage activates (:module/activated -> :comm/activated
{:comm "imessage"}) but then ERRORs:
  :imsg.watch/subscribe-failed {:error "Internal error"}
So imessage can SEND but the WATCH (receiving incoming messages) fails — the
iMessage assistant won't see inbound messages. Observed on zanebot foundation
server boot 2026-06-19 14:59:36.233.

"Internal error" is opaque. Fix: surface the REAL cause of the watch subscribe
failure (chat.db access / imsg binary / the watcher), and fix it. (isaac-imessage)

## Verification Notes

2026-06-19 verifier:

- Verified against fetched GitHub `isaac-imessage` `main` at `8c92287`.
- `env ISAAC_GIT=1 bb spec spec/isaac/comm/imessage_spec.clj spec/isaac/server/imessage_app_spec.clj` passed: `39 examples, 0 failures, 67 assertions`.
- The delivered change surfaces real `watch.subscribe` RPC detail, logs slice context (`:imessage/db-path`, `:imessage/bin`), validates `chat.db` readability before spawn, and covers those paths in the focused specs.
