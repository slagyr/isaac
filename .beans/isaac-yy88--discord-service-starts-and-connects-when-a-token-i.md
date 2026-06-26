---
# isaac-yy88
title: Discord service starts and connects when a token is added via hot reload
status: draft
type: bug
created_at: 2026-06-26T16:24:42Z
updated_at: 2026-06-26T16:24:42Z
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

## Draft acceptance

- Boot the server with no Discord token configured.
- Add a Discord token via config hot reload.
- The Discord service starts and the gateway connects without full server
  restart.
- Removing the token still disconnects cleanly.
- Unchanged-token reload does not reconnect.
- The currently-pending spec in `isaac-discord/spec/isaac/server/discord_app_spec.clj`
  becomes deterministic and green.

## Likely repo scope

- `isaac-discord`

## Notes

This bean should stay focused on lifecycle. Do not mix in the broader routing
or observability work from `isaac-y1ak` / `isaac-enp1` / `isaac-qvub`.
