---
# isaac-xt7p
title: Discord notifications vanish during gateway flaps — no queue, no retry
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-08-23T17:12:07Z
updated_at: 2026-08-23T17:35:00Z
---

Observed 2026-08-23: n4f9/ek0r/x3vb completed 2026-08-22 19:08-19:27 with zero Discord reports; server.log shows discord.gateway reader-nil-message disconnect/reconnect cycles in that window (5 occurrences Aug 22-23). comm_send into a down gateway evaporates silently — notifications have no hails-never-die discipline. Expected: queue-and-retry until gateway ready, or loud failure surfaced to the sender. Diagnose-first: confirm the drop path in isaac-discord comm_send/gateway, then scenario the retry contract. Layer: isaac-discord / comm delivery — no overlap with recall (h5dk) or tool-permissions (da0r) work.


## Diagnosis

Drop path is `Comm/send!` (queued `comm_send` delivery), not inbound routing.

- REST POST `/channels/{id}/messages` is independent of the Gateway websocket. `send!` did not inspect gateway liveness.
- Success was `(:status response 0) < 400`, so a missing/zero status counted as delivered. The worker then deleted the pending file — silent vanish.
- Transient failures burned the 5-attempt backoff (~2.6 min) then dead-lettered. Multi-minute `reader-nil-message` flaps outlasted that window.
- `on-turn-end` already uses `try-send-or-enqueue!`. Outbound `comm_send` goes through the delivery worker + `send!`.
- Existing `comm_send_target` scenarios register outbound comm with `conn` nil (no gateway). REST must still POST in that case.

## Contract

- No gateway client on the integration → REST as before.
- Gateway client present and not `gateway/connected?` (`:status` ≠ `:ready`) → no HTTP, `{:ok false :transient? true :defer? true}`, log `:warn :discord.send/gateway-unavailable`.
- HTTP success is 2xx only. Status 0/missing is transient.
- Worker `:defer?` leaves the pending file unchanged (no attempt increment, no dead-letter) and logs `:info :comm.delivery/deferred`.

## Acceptance

- `isaac-discord` `bb spec spec/isaac/comm/discord_spec.clj` — send! defer / ready / missing-status
- `isaac-discord` `bb features features/comm/discord/send_during_flap.feature`
- `isaac-agent` `bb spec spec/isaac/comm/delivery/worker_spec.clj`
- `isaac-agent` `bb features features/comm/delivery/queue.feature:128`
