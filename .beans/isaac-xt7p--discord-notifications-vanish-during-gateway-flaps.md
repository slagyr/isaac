---
# isaac-xt7p
title: Discord notifications vanish during gateway flaps — no queue, no retry
status: todo
type: task
created_at: 2026-08-23T17:12:07Z
updated_at: 2026-08-23T17:12:07Z
---

Observed 2026-08-23: n4f9/ek0r/x3vb completed 2026-08-22 19:08-19:27 with zero Discord reports; server.log shows discord.gateway reader-nil-message disconnect/reconnect cycles in that window (5 occurrences Aug 22-23). comm_send into a down gateway evaporates silently — notifications have no hails-never-die discipline. Expected: queue-and-retry until gateway ready, or loud failure surfaced to the sender. Diagnose-first: confirm the drop path in isaac-discord comm_send/gateway, then scenario the retry contract. Layer: isaac-discord / comm delivery — no overlap with recall (h5dk) or tool-permissions (da0r) work.
