---
# isaac-qqor
title: 'modules upgrade [name...]: refresh installed modules to latest registry coords'
status: completed
type: feature
priority: normal
tags:
created_at: 2026-06-19T18:43:30Z
updated_at: 2026-06-19T18:50:30Z
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

## Verification Notes

2026-06-19 verifier:

- Verified against fetched GitHub `isaac-foundation` `main` at `11a7fd9`.
- `env ISAAC_GIT=1 bb features-all features/module/modules_upgrade.feature` passed on rerun: `2 examples, 0 failures, 7 assertions`.
- I also replayed the stale-registry scenario directly with the packaged launcher; `modules upgrade` printed `Upgraded stale: 6960803 -> 817a524` and rewrote the config as expected.
- Full repo lane on the same head passed: `env ISAAC_GIT=1 bb ci` -> `754` spec examples, `0` failures; `109` feature examples, `0` failures.
