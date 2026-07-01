---
# isaac-lyg0
title: Fix isaac-acp chat_cli stale paths/default-state-dir reference
status: completed
type: bug
priority: normal
created_at: 2026-06-05T06:49:49Z
updated_at: 2026-06-11T15:01:40Z
---

isaac-acp/src/isaac/comm/acp/chat_cli.clj line 62 references paths/default-state-dir which no longer exists in isaac.config.paths (was renamed/removed by an earlier state-dir bean). This breaks the entire isaac-acp spec suite at SCI analysis time — bb spec in isaac-acp blows up before any test runs.

Surfaced while doing isaac-8v1n. Not in scope there; logging here so it gets fixed before the next berth migration touches acp.

## What to do

- Decide what state-dir resolution acp's chat command should use today. Check isaac core's current paths/* API.
- Update chat_cli.clj's state-dir / home-dir helpers.
- Re-run bb spec in isaac-acp until green.


## Summary of Changes

Fixed in isaac-acp commit b76191a (2026-06-11) — same change set that migrated isaac-acp's `:route` + `:cli` to the new berth contribution shape.

`isaac.comm.acp.chat-cli` swapped its stale `paths/default-state-dir` calls for `isaac.root/current-root`. Surfaced because isaac core's new `:cli` berth does `requiring-resolve` on each contribution's `:run-fn` at registration time, which loaded the chat-cli namespace, which threw on the stale path reference — so `chat` failed to register while `acp` (clean namespace) succeeded.
