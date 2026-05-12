---
# isaac-qzn0
title: "Port Discord to a module subproject"
status: completed
type: epic
priority: low
created_at: 2026-04-30T22:37:02Z
updated_at: 2026-05-05T18:47:38Z
---

## Description

Why: Discord is the milestone target for module loading. Once Phase 1 (discovery) and Phase 2 (lazy activation) plus the module-public api.* surface land, Discord becomes the first real module — proves the layout end-to-end and unblocks third-party modules.

## Scope

- Create modules/isaac.comm.discord/ subproject
  - module.edn manifest with :id, :version, :entry, :extends
  - :extends.comm.discord declares Discord's config keys (:token, :crew, :message-cap, :allow-from) — moved from src/isaac/config/schema.clj's static :discord def
  - Source layout following module convention (src/isaac/comm/discord{,.gateway,.rest}.clj)
- Discord's namespace migrates internal :requires to isaac.api.* where available:
  - isaac.comm.registry      -> isaac.api.registry  (5i8v)
  - isaac.lifecycle           -> isaac.api.lifecycle (5i8v)
  - isaac.logger              -> isaac.api.logger    (5i8v + ex defect)
  - isaac.drive.turn          -> isaac.api.turn      (d00u)
  - isaac.session.storage     -> isaac.api.session   (55tb)
  - isaac.config.loader       -> NOT migrated; flagged as internal coupling debt
  - isaac.comm                -> stale require; delete it
- Discord's static :discord schema entry is removed from core (now lives in module manifest as :extends fragment)
- features/modules/discord.feature exercises the bootstrap path (module activation -> client started)
- All 11 existing features/comm/discord/*.feature scenarios continue to pass

## Out of scope

- ACP/CLI comms migration (different beads)
- isaac.config.loader as a public facade (separate bead when the migration surfaces concrete shape needs)
- Removing Discord's legacy :channels.discord cfg path (separate bead)

## Implicit prereqs (tracked in notes since epics can't depend on tasks)

- isaac-bnv0  (lazy module activation; Phase 2 first cut)
- isaac-5i8v  (isaac.api.{registry,lifecycle,logger})
- isaac-d00u  (isaac.api.turn)
- isaac-55tb  (isaac.api.session)

## Acceptance

- features/modules/discord.feature scenarios pass
- All existing features/comm/discord/*.feature scenarios pass
- src/isaac/config/schema.clj no longer carries the static :discord def
- Discord's source lives under modules/isaac.comm.discord/

## Notes

Verification failed: automated checks are green (bb spec passes; features/modules/discord.feature and all features/comm/discord/*.feature pass; src/isaac/config/schema.clj no longer carries a static discord schema entry; Discord source lives under modules/isaac.comm.discord/). However, the module port still directly requires isaac.comm in modules/isaac.comm.discord/src/isaac/comm/discord.clj and implements comm/Comm there. This conflicts with the bead scope, which explicitly called isaac.comm a stale require to delete, while only isaac.config.loader was allowed to remain as internal coupling debt. Because that internal coupling remains, the bead is not fully complete.

