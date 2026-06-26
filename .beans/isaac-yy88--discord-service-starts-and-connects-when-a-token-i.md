---
# isaac-yy88
title: Discord service starts and connects when a token is added via hot reload
status: todo
type: bug
created_at: 2026-06-26T16:24:42Z
updated_at: 2026-06-26T17:38:14Z
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
