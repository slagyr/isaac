---
# isaac-xibj
title: "Discord plugin lifecycle: hot-load on config add, stop on remove, no-op on change"
status: completed
type: feature
priority: normal
created_at: 2026-04-29T21:09:21Z
updated_at: 2026-05-01T23:00:56Z
---

## Description

The Discord comm adapter exists (src/isaac/comm/discord.clj with
gateway, REST, routing) but isaac.comm.discord/connect! is only
called from tests. Production startup never opens the Discord
gateway, so inbound Discord messages don't reach Isaac.

Outbound is wired via src/isaac/delivery/worker.clj. Inbound is
the gap.

## Goal

When Discord config is present at startup OR added at runtime,
Isaac connects. When config is removed, Isaac disconnects. When
config merely changes, Isaac does NOT bounce the connection —
runtime reads fresh cfg per inbound message, so updates take
effect without a restart.

## Plugin abstraction

A small generic protocol so future comms (Slack, etc.) plug in
without bespoke wiring:

  (defprotocol Plugin
    (config-path [this])
    (on-config-change! [this old new]))

Manager:
- Walks registered plugins on startup, calls
  (on-config-change! plugin nil current-slice) for each.
- On cfg* atom change, computes (get-in cfg path) for each plugin.
  If old != new, calls (on-config-change! plugin old new).
- On server stop, calls (on-config-change! plugin current-slice nil)
  for each.

Plugin decides what start/stop/no-op means. Discord plugin:
- nil -> X: start client
- X -> nil: stop client
- X -> Y (both present): no-op (runtime reads fresh cfg per message)

## Spec

features/comm/discord/lifecycle.feature has four @wip scenarios:
1. Discord starts on server boot when config is present
2. Discord starts when config is added mid-run
3. Discord stops when config is removed mid-run
4. Discord does NOT restart when its config changes

Domain-scoped log events:
:discord.client/started and :discord.client/stopped (each plugin
emits its own — no generic :plugin/started).

Two new step phrases needed:
- the isaac EDN file \"{path}\" is removed
- the Discord client is disconnected

## Implementation surfaces

- New: src/isaac/plugin.clj (Plugin protocol + registry +
  lifecycle manager)
- New: discord plugin registration (in src/isaac/comm/discord.clj
  or a new src/isaac/comm/discord/plugin.clj)
- src/isaac/server/app.clj start! / stop!: walk registered plugins,
  hook the plugin manager into the cfg* atom watch
- Two new step impls in spec/isaac/features/steps/server.clj or
  discord.clj

## Definition of done

- All four scenarios pass without @wip
- isaac server with Discord config opens the gateway connection on startup
- isaac server with no Discord config doesn't try (no startup error)
- Adding/removing config/comms/discord.edn at runtime starts/stops the client
- Editing the file (without removing) does not restart the client
- Server stop! disconnects the gateway cleanly
- bb features and bb spec green
- Manual smoke: real Discord token; Marvin receives and responds

## Out of scope (future)

- Multi-instance Discord (YAGNI for now; one bot identity per Isaac)
- Slack/Telegram/etc. plugins (will copy the pattern when needed)
- Plugin start/stop ordering (no ordering deps yet)

## Notes

Implemented the generic plugin abstraction requested by the bead. Added src/isaac/plugin.clj with Plugin protocol, builder registry, and lifecycle manager functions (build-all/start!/sync-config!/stop!). Moved Discord lifecycle onto a DiscordPlugin in src/isaac/comm/discord.clj and rewired src/isaac/server/app.clj to manage plugins generically instead of bespoke sync-discord! wiring. Added spec/isaac/plugin_spec.clj and kept app/Discord lifecycle specs/features green. Verified with bb spec spec/isaac/plugin_spec.clj spec/isaac/server/app_spec.clj, bb features-all features/comm/discord/lifecycle.feature features/comm/discord/gateway.feature, bb spec, and bb features. Manual smoke with a real Discord token is still recommended per bead definition of done.
Verification failed: automated specs/features for the plugin lifecycle pass, but the definition of done also requires a manual smoke with a real Discord token showing Marvin receives and responds. That smoke is not evidenced here, so the bead cannot be closed yet.
Re-verified: targeted automated checks pass (bb spec spec/isaac/plugin_spec.clj spec/isaac/server/app_spec.clj; bb features-all features/comm/discord/lifecycle.feature), @wip is removed, and the plugin abstraction/lifecycle wiring is present in src/isaac/plugin.clj, src/isaac/comm/discord.clj, and src/isaac/server/app.clj. Full bb spec and bb features are currently red, but the observed failures are in unrelated OpenAI/provider and server-status paths. The bead still cannot be closed because its definition of done requires a manual smoke with a real Discord token showing Marvin receives and responds, and that evidence is still not present.

