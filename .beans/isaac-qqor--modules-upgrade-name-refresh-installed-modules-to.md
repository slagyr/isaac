---
# isaac-qqor
title: 'modules upgrade [name...]: refresh installed modules to latest registry coords'
status: todo
type: feature
created_at: 2026-06-19T18:43:30Z
updated_at: 2026-06-19T18:43:30Z
---

The fix for stale config coords (config snapshots don't auto-update when the
registry bumps — caused zanebot's comms to sit at 0.1.0 after v0.1.2). Mirrors
`brew upgrade`.

## Behavior

`isaac modules upgrade [name...]` — re-fetch the registry; for each
REGISTRY-SOURCED module in :modules, rewrite its coord to the latest registry
coord; report old -> new per module. Leave :local/root and ids-not-in-registry
UNTOUCHED. No args = upgrade all; names = selective. Nothing to do -> "up to
date".

## Scenarios -> features/module/modules_upgrade.feature (@wip)

1. stale module rewritten to the latest registry coord (config gets the new sha;
   stdout reports it).
2. :local/root / non-registry modules left untouched; "up to date".

## Relationships

• Mirrors brew upgrade; pairs with modules install/available (dhzy).
• Uses :module-registry seam (test) / raw-github (real).
• Selective `upgrade <name>` and a summary of changes.
