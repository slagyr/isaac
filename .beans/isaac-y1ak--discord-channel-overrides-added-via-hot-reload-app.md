---
# isaac-y1ak
title: Discord channel overrides added via hot reload apply to both inbound routing and outbound replies
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-26T16:24:25Z
updated_at: 2026-06-26T17:26:31Z
---

## Context

Observed on zanebot while editing `~/.isaac/config/isaac.edn` live: a new
Discord channel mapping was added under `:comms.discord.discord/channels`, but
the running bot did not reply in the newly-mapped session/channel shape.

This is not just a watcher question. The Discord module currently splits its
config sources:

- inbound routing re-loads config from disk per message in
  `isaac-discord/src/isaac/comm/discord.clj` `effective-config` /
  `process-message!`
- outbound reply routing uses the live comm atom `@cfg` in `on-turn-start` /
  `on-turn-end`

That means a hot-reloaded channel/session override can be visible to inbound
routing and still be invisible to outbound reply routing until restart.

## Why separate from the logging beans

This is a behavior bug, not just missing observability. Even with perfect logs,
the runtime can still route inbound and outbound using different config views.

## Draft acceptance

- Start with Discord connected and a working baseline config.
- Add a new per-channel override via config hot reload:
  - channel id -> `:session "discord-isaac"` (and optionally crew/model override)
- The next accepted message on that channel routes into session
  `discord-isaac` without restart.
- The reply posts back to that same channel without restart.
- No `:discord.client/started` or `:discord.client/stopped` log entries occur
  when only channel/session routing config changed.
- Existing string-key channel override behavior remains green.

## Likely repo scope

- `isaac-discord`
- possibly a small supporting assertion in `isaac` if a server-level feature is
  the best place to demonstrate the full hot-reload round trip

## Notes

Related but distinct:
- `isaac-o0xj` fixed numeric channel keys
- `isaac-tw3m` fixed allow-from reload without reconnect
- `isaac-yy88` below is the separate token-add lifecycle gap

## Approved scenarios

- `isaac-discord/features/comm/discord/routing.feature:69`
  - `a hot-reloaded channel session override applies to both inbound routing and outbound reply`
- `isaac-discord/features/comm/discord/routing.feature:171`
  - `a hot-reloaded channel crew override applies without reconnecting the Discord client`

## Decision (2026-06-26, Micah)

Keep this bean focused on the split-brain config bug in Discord routing:

- inbound routing and outbound reply must honor the same hot-reloaded channel
  override view
- no new steps
- Marigold-themed routing scenarios in `routing.feature`
- no reconnect assertions stay here because they are part of the operator-facing
  contract for channel override edits

Observability work stays in:
- `isaac-qvub` server reload visibility
- `isaac-enp1` Discord routing/reply-path logs

## Acceptance commands

- `cd isaac-discord && bb features features/comm/discord/routing.feature`

## Verification

Verified on fetched GitHub `isaac-discord` `main` at `d05a429`. The touched spec slice is green: `bb spec spec/isaac/comm/discord_spec.clj` passed with `63 examples, 0 failures, 128 assertions, 1 pre-existing pending`. The bean is not complete because its own acceptance command is red on current head: `bb features features/comm/discord/routing.feature` fails `2/8` scenarios before the new routing behavior is exercised. Both approved hot-reload scenarios fail during server startup with `Unconformable entity #:discord{:token "test-token", :allow-from {:users ..., :guilds ...}}`; the feature background still seeds `comms.discord.discord/allow-from.users` and `.guilds` as bare scalars (`123`, `G789`) while current schema expects string sequences. Until the routing feature is green with valid Discord allow-from fixture data, the handoff is not verifier-ready.
