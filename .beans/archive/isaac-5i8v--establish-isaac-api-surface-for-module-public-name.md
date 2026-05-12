---
# isaac-5i8v
title: "Establish isaac.api.* surface for module-public namespaces"
status: completed
type: task
priority: normal
created_at: 2026-05-05T16:42:58Z
updated_at: 2026-05-05T16:48:55Z
---

## Description

Why: as we port Discord (and eventually other comms/providers/tools) to modules, modules need a stable public surface to :require. Reaching directly into isaac.* internals couples modules to refactors and blurs the public/private line. The isaac.api.* prefix gives modules a curated, documented surface; internal refactors don't break modules.

## Scope

Foundation pass — establish the api.* prefix and re-export the symbols a module actually needs today. Discord's current :requires inform the surface.

Three target namespaces:

- isaac.api.registry — comm/provider/tool registries (register!, lookup, unregister! as appropriate)
- isaac.api.lifecycle — the Lifecycle protocol modules implement
- isaac.api.logger — structured logging facade (log/info, log/warn, log/error, log/debug)

Implementer picks the exact symbols based on what Discord (and existing reconciler tests) actually use.

## Out of scope

- Public facades for the gnarly middle (isaac.drive.turn, isaac.session.storage, isaac.comm) — separate beads, designed when Discord-as-module discovers what shape they should take.
- Lint or enforcement that modules ONLY :require from isaac.api.* — convention for now; enforcement later if drift becomes a problem.

## Acceptance

- isaac.api.{registry,lifecycle,logger} namespaces exist and re-export the obvious public symbols.
- A module CAN :require these instead of reaching into isaac.comm.registry / isaac.lifecycle / isaac.logger directly.
- Existing core code (which still uses the original namespaces directly) continues to work — this is purely additive.

## Acceptance Criteria

isaac.api.registry, isaac.api.lifecycle, isaac.api.logger exist and re-export the public surface. Existing core code unchanged. A module can :require from isaac.api.* in place of the corresponding internal namespace.

