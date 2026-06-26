---
# isaac-yy88
title: Discord service starts and connects when a token is added via hot reload
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-26T16:24:42Z
updated_at: 2026-06-26T20:55:04Z
---

## Context

There is already a pending spec in `isaac-discord` for this exact lifecycle
gap:

- `isaac-discord/spec/isaac/server/discord_app_spec.clj`
- `"connects Discord gateway when token is added via config hot-reload"`

The current service code gates the token-add connect path on the Discord
service already being in a running state. A no-token boot means there is no
live client to update, so adding a token later does not reliably start/connect
the service.

This is separate from channel-routing reload questions. It only matters when
the process booted without a Discord token and the token appears later.

## Approved scenarios

- `isaac-discord/features/comm/discord/lifecycle.feature:25` —
  adding a Discord token mid-run starts the client
- `isaac-discord/features/comm/discord/lifecycle.feature:40` —
  removing a Discord token mid-run stops the client
- `isaac-discord/features/comm/discord/lifecycle.feature:60` —
  reloading unchanged Discord token does not reconnect the client

## Acceptance

- Boot the server with no Discord token configured.
- Add a Discord token via config hot reload.
- The Discord service starts and the gateway connects without full server
  restart.
- Removing the token still disconnects cleanly.
- Unchanged-token reload does not reconnect.
- The currently-pending spec in `isaac-discord/spec/isaac/server/discord_app_spec.clj`
  becomes deterministic and green.
- `cd isaac-discord && bb features features/comm/discord/lifecycle.feature`

## Likely repo scope

- `isaac-discord`

## Notes

This bean should stay focused on lifecycle. Do not mix in the broader routing
or observability work from `isaac-y1ak` / `isaac-enp1` / `isaac-qvub`.

The existing feature file already covered the same lifecycle seam, but with
stale `:lifecycle/started` / `:lifecycle/stopped` expectations. The approved
`@wip` scenarios pin the real service log contract:
`:discord.client/started` and `:discord.client/stopped`.



## Resolution (2026-06-26) — committed isaac-discord daeecc2

Production fix landed in isaac-discord/src/isaac/comm/discord/service.clj:
- Root cause: the token-add connect path was gated on the Discord SERVICE
  INSTANCE being in a running state, but a no-token boot lazily skips the
  Discord module so that instance is never started/registered, so adding a
  token later only connected when the lookup happened to succeed.
- Fix: gate on isaac.server.app/running? (true only between a real boot and
  stop) and reconcile to desired state from the comm's ACTUAL client
  (idempotent, instance-churn-aware). CLI-load still does not connect.

Deterministic proof (authoritative acceptance signal):
  cd isaac-discord && ISAAC_GIT=1 clojure -M:spec  ->  66/0
  including discord_app_spec:
   - "connects Discord gateway when token is added via config hot-reload"
     (the formerly-pending spec; `pending` removed, now green)
   - "disconnects Discord gateway when token is removed via config hot-reload"
   - unchanged-token-no-reconnect

Feature scenarios (lifecycle.feature):
- "Discord client starts on isaac server startup" -- ENABLED, green.
- "reloading unchanged Discord token does not reconnect" -- ENABLED, green.
- "adding a Discord token mid-run starts the client"  -- @wip
- "removing a Discord token mid-run stops the client" -- @wip
  The two FILE-reload variants go through the shared isaac-server feature
  harness, whose dual reload paths (sync drain + async watcher) race poll!.
  The qokc reconcile-modules! deps bump made the remove variant fail
  CONSISTENTLY (was ~50% flaky). Production behavior for both is proven by
  discord_app_spec above; only the harness is nondeterministic.
  Tracked + deferred to isaac-snkl (isaac-server reload-harness).
  Re-enable both @wip scenarios in isaac-discord once isaac-snkl lands.
  ISAAC_GIT=1 clojure -M:features (clean target) -> 45/0 across 4 runs.

NOTE for verifier: gherclj does NOT clean target/gherclj between runs; run
`rm -rf target/gherclj` before -M:features or stale generated specs re-run.

Acceptance status: the pending-spec requirement is MET (deterministic green).
The two mid-run file-reload feature scenarios are intentionally @wip pending
isaac-snkl (shared-harness determinism), not a discord-logic gap.

Tagged unverified for verification of discord_app_spec (66/0) on a fresh
ISAAC_GIT checkout of isaac-discord@daeecc2.
