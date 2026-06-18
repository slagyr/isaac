---
# isaac-5h15
title: 'v0.1.0 module coords poisoned: foundation tag moved after release breaks transitive pins'
status: completed
type: bug
priority: normal
tags: []
created_at: 2026-06-18T22:48:02Z
updated_at: 2026-06-18T23:06:00Z
---

Installing a registry module then running isaac fails to compose the classpath:

  Error building classpath. Library io.github.slagyr/isaac-foundation has sha
  and tag that point to different commits

Hit on zanebot via `modules install isaac.server` -> `:isaac.server` in config
-> next run dies. Unblocked there by removing :isaac.server (comm modules are
fine).

## Root cause (verified 2026-06-18)

why8 MOVED foundation's v0.1.0 tag AFTER the v0.1.0 modules were released:
• isaac-foundation v0.1.0 -> fe83ebe3 (moved; was 3cc82a1a)
• isaac-foundation v0.1.1 -> 36e4a6f
The released v0.1.0 MODULE commits ship deps.edn that pin foundation at
{:git/tag "v0.1.0" :git/sha <old 3cc82a1a-era sha>}. tools.deps verifies tag+sha
agree; v0.1.0 now resolves to fe83ebe3 != the pinned sha -> hard reject. So the
whole v0.1.0 module set is poisoned for any tag+sha-verifying resolver.

The registry (modules.edn, isaac-xdg3) pins every module at :git/tag "v0.1.0",
so `isaac modules install <tag-bearing module>` reproduces this for anyone.
(zanebot's comm modules happened to resolve — sha-only / consistent foundation
pin at their release commit.)

## Fix

1. NEVER move a published tag (same lesson as isaac-7tle, applied to modules).
   v0.1.0 is poisoned in place — don't patch it, supersede it.
2. Coordinated v0.1.1 release across ALL module repos with foundation pinned
   CONSISTENTLY: either sha-only, or tag+sha where sha == the immutable tag's
   commit (foundation v0.1.1 = 36e4a6f).
3. Bump registry modules.edn to v0.1.1 coords — and prefer SHA-ONLY coords
   (drop :git/tag) to eliminate the whole tag/sha-consistency failure class.
4. Verify: `isaac modules install isaac.server` then `isaac --version` composes
   cleanly on a fresh root.

## Relationships

• Sibling of isaac-7tle (formula tag/tarball churn) — same don't-move-tags root.
• Fallout of why8 (the tag moves).
• isaac-xdg3 owns modules.edn (needs the v0.1.1 / sha-only bump).

## Verification notes

- Verification passed on 2026-06-18.
- [modules.edn](/Users/micahmartin/agents/verify/isaac/modules.edn:1) now uses sha-only coordinates for all eight installable modules; there are no remaining `:git/tag` entries to trigger tag/sha disagreement.
- The registry SHAs resolve to published immutable release tags, not arbitrary heads: `isaac-agent` `v0.1.2`, `isaac-server` `v0.1.3`, `isaac-acp` `v0.1.2`, and `isaac-cron`, `isaac-hail`, `isaac-hooks`, `isaac-discord`, `isaac-imessage` `v0.1.1`.
- End-to-end compose proof passed on fetched GitHub `isaac-foundation` `main` at `dfb3627`: `env ISAAC_GIT=1 bb features features/module/modules_registry_install.feature` → `1 examples, 0 failures, 4 assertions`. That scenario installs `isaac.server` from a sha-only registry entry and then runs `isaac --version`, covering the original classpath-compose failure mode directly.
