---
# isaac-97bf
title: Comm modules adopt :send-schema + migrate to namespaced record keys
status: completed
type: feature
priority: normal
tags: []
created_at: 2026-06-23T22:04:23Z
updated_at: 2026-06-26T16:14:40Z
blocked_by:
    - isaac-2s0b
---

Follow-up to isaac-2s0b (comm_send tool). 2s0b builds the generic mechanism (the :isaac.server/comm :send-schema berth field, comm_send composing its schema, and the telly fixture's :send-schema). THIS bean migrates the REAL comm modules so comm_send can actually deliver to them, and enforces the one namespaced record contract.

## Background
- Module-contributed outbound keys MUST be namespaced by comm type (decision in isaac-2s0b): :discord/target, :imessage/target, :imessage/service, etc. Framework keys (:comm, :content) stay bare.
- comm_send writes the delivery record verbatim (no normalizer), so the namespaced :send-schema keys ARE the record keys.

## Work
### discord (isaac-discord)
- Add :send-schema under its :isaac.server/comm contribution: {:discord/target {:type :string :description "...channel id..."}} (+ any extras).
- Migrate send! (discord.clj ~181): (:target record) -> (:discord/target record). :content stays bare.

### imessage (isaac-imessage)
- Add :send-schema: {:imessage/target {:type :string :description "...handle..."} :imessage/service {:type :string :validations [[:one-of? ...]]}}.
- Migrate send! (imessage.clj ~23, ~362-366): (:target record) -> (:imessage/target record); (:service record) -> (:imessage/service record). :content stays bare.

### reply-enqueue path (turn -> comm)
- The existing path that enqueues outbound REPLIES for discord/imessage must emit the namespaced keys too, so there is exactly ONE record contract (comm_send and replies write identical shapes). Find every site that builds a delivery record with :target/:content/:service for these comms and namespace the per-comm keys. (This is the ve2a delivery path.)

### berth schema
- Ensure :send-schema is validated on the :isaac.server/comm berth (done in 2s0b; confirm and reuse).

## Features / specs
- Per-module feature/spec asserting each comm's :send-schema contribution and that send! reads its namespaced keys (e.g. discord send! reads :discord/target).
- A delivery/round-trip spec: a record with namespaced keys is delivered by the matching comm.

## Acceptance
- discord & imessage declare namespaced :send-schema; their send! read the namespaced keys; :content stays bare.
- The reply-enqueue path emits namespaced keys — one record contract across comm_send + replies.
- comm_send (2s0b) composes real discord/imessage fields into its tool schema from these contributions (no isaac-agent changes needed).
- No un-namespaced :target/:service remain in these comms' read or write paths.

## Notes
Breaking change to the delivery-record shape for discord/imessage (:target -> :discord/target etc.). Coordinate so send! reads and ALL writers (comm_send + reply path) flip together. Surfaced 2026-06-23 during comm_send planning.

## Implementation (work-3)

- **isaac-discord** `694e770`: manifest `:send-schema` with `:discord/target`; `send!` and `try-send-or-enqueue!` use namespaced keys; specs updated. Current `isaac-discord` `main` now contains that migration plus follow-up `5914356`, which drops the legacy bare `:target` read entirely.
- **isaac-imessage** `8d322b5`: manifest `:send-schema` with `:imessage/target` + `:imessage/service`; `send!`, `imsg-params`, `dispatch-and-enqueue-reply!` migrated; specs added for manifest shape, reply enqueue, and Comm `send!`.
- `bb spec` green except one pre-existing flaky lifecycle spec (`comm-registry/comm-for` race on main before this change).

## Verification

Re-verified on the true current heads after the Discord recovery landed: fetched GitHub `isaac-discord` `main` at `5914356` and `isaac-imessage` `main` at `8d322b5`. Current manifests declare namespaced `:send-schema` in both comm modules, Discord outbound send now reads only `:discord/target`, iMessage send/reply paths read and write `:imessage/target` / `:imessage/service`, and there are no remaining bare `:target` / `:service` reads in these comms' source paths. Proofs: `isaac-discord` `bb spec spec/isaac/comm/discord_spec.clj spec/isaac/comm/discord/rest_spec.clj` passed (`62 examples, 0 failures, 127 assertions, 1 pre-existing pending`), and a dynamic agent-side composition check on current `isaac-agent` showed `comm_send` parameters include `discord.target`, `imessage.target`, and `imessage.service` from the two live manifests without any agent changes. The full iMessage spec alias still pulls in broader lifecycle/server files with pre-existing flake/sandbox issues, but the 97bf send-schema and delivery-record slice itself is present and checks out on current head.
