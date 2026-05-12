---
# isaac-v0uu
title: "Lazy module activation"
status: completed
type: epic
priority: low
created_at: 2026-04-30T22:36:38Z
updated_at: 2026-05-05T22:23:44Z
---

## Description

Once Phase 1 (discovery) lands, plugins are known but not loaded.
Phase 2 activates them on first use — when something asks for an
api/tool/etc. the plugin contributes.

## Will be split into concrete beads when Phase 1 lands

Sketch:

- P2.1: isaac.plugin.loader/activate! — given a plugin-id, require
  its :entry namespace. The require triggers register! calls inside
  the plugin's code.
- P2.2: dispatch.clj/make-provider, on registry miss, looks up which
  plugin extends that api (from manifest :extends), activates it,
  retries. Real error if still missing.
- P2.3: same lazy-activation hook for tool registry.
- P2.4: schema composition — plugin :schema fragments merge into
  core schema at activation time. Cross-plugin validation needs
  validator-by-symbol resolution in c3kit (see isaac-x3lp / P0.2).
  Until upstream c3kit ships, plugin :schema fragments can compose
  but only with type-level validation; symbol-resolved validators
  defer to whichever fix lands first.

## Why epic for now

Detail depends on what Phase 1 reveals. Splitting is cheaper after
P1.3/P1.4 are in.

## Depends on

- P1.4 (config wiring)
- P0.2 (isaac-x3lp, upstream c3kit) — only blocks P2.4's full
  cross-plugin validation; the rest of P2 can land without it.

## Notes

Verification failed again: bb spec is now green, but bb features still fails in epic-scope module behavior. Current feature failures include features/modules/discord.feature ('Discord activates from a module declaration' log matching is wrong: :module/activated is appearing where :discord.client/started is expected), multiple Discord lifecycle feature failures in the module-migrated Discord suite, and features/modules/schema_composition.feature scenario 'Without the module declared, extended keys are unknown' still failing. Since the feature suite is still red in module activation/schema paths, the epic cannot be closed.

