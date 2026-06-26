---
# isaac-97bf
title: Comm modules adopt :send-schema + migrate to namespaced record keys
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-23T22:04:23Z
updated_at: 2026-06-26T16:06:01Z
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

- **isaac-discord** `694e770`: manifest `:send-schema` with `:discord/target`; `send!` and `try-send-or-enqueue!` use namespaced keys; specs updated. This commit exists but is not on current `isaac-discord` `main`.
- **isaac-imessage** `8d322b5`: manifest `:send-schema` with `:imessage/target` + `:imessage/service`; `send!`, `imsg-params`, `dispatch-and-enqueue-reply!` migrated; specs added for manifest shape, reply enqueue, and Comm `send!`.
- `bb spec` green except one pre-existing flaky lifecycle spec (`comm-registry/comm-for` race on main before this change).

## Verification correction

Reopened 2026-06-26. Prior verification was wrong: it accepted `isaac-discord` commit `694e770` without confirming that the commit was actually contained by `origin/main`. Current real `isaac-discord` `main` does not contain `694e770` (`git branch -a --contains 694e770` returns nothing), still lacks a manifest `:send-schema` contribution, and [src/isaac/comm/discord.clj](/Users/micahmartin/agents/verify/isaac-discord/src/isaac/comm/discord.clj:181) still reads `(:target record)`. The `isaac-imessage` half is present on current `main`; the `isaac-discord` half needs to be recovered or re-landed from orphaned commit `694e770`.

## Implementation (work-2, 2026-06-26)

- **isaac-discord** `860af32` (vhyw) + `5914356`: `:send-schema` with `:discord/target` on main; `send!` and `rest/try-send-or-enqueue!` use namespaced keys; legacy `:target` read path removed. `bb spec` green (62 examples).
- **isaac-imessage** `8d322b5` on main: `:send-schema`, `send!`, `imsg-params`, `dispatch-and-enqueue-reply!` migrated; manifest/reply/send specs present. `bb spec` has one pre-existing lifecycle/registry failure unrelated to send-schema.
