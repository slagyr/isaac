---
# isaac-xdg3
title: Create + seed the module registry (modules.edn) in the isaac repo
status: completed
type: task
priority: normal
tags: []
created_at: 2026-06-18T14:32:19Z
updated_at: 2026-06-18T18:28:00Z
---

Create github.com/slagyr/isaac/modules.edn — the catalog of installable modules that `isaac modules install`
and `isaac modules available` (isaac-dhzy) fetch via raw github. Format is defined by isaac-dhzy.

## Contents
- One entry per INSTALLABLE (bolt-on) module: agent, server, acp, cron, hail, hooks, discord, imessage.
  (foundation is the seed/base — always present, NOT an installable entry.)
- Each: {:coord {:git/url \"...\" :git/tag \"vX.Y.Z\"} :desc \"...\"} using the published tags.

## Hosting
- At the isaac repo root (the repo persists as beans/docs host; this is its registry role).
- Fetched via raw.githubusercontent.com/slagyr/isaac/main/modules.edn.

## Maintenance

When a module repo cuts a new release, bump that entry's `:git/sha` in
`modules.edn` on `isaac` `main` (sha-only coords — no `:git/tag`). Order: tag
the module repo, resolve the commit SHA, update the registry entry, push.
Published tags are immutable; supersede poisoned releases with a new tag/sha.

## Acceptance
- modules.edn exists with all installable modules + valid tagged coords (matches the format in isaac-dhzy).
- `isaac modules available` fetches + lists them; `isaac modules install <name>` resolves to the right coord.

## Implementation

- `modules.edn` at isaac repo root — eight modules at `v0.1.0`, keyed by manifest
  id (`:isaac.agent`, `:isaac.server`, `:isaac.comm.acp`, …). Coords include
  `:git/sha` alongside `:git/tag` (why8 pin pattern).
- Foundation `873dbb9`: `modules install` uses `modules["<id>"]` paths so qualified
  registry ids write flat `:modules` entries.

## Verification notes

- Verified on 2026-06-18. [modules.edn](/Users/micahmartin/agents/verify/isaac/modules.edn:1) now contains eight installable module entries with `:git/tag "v0.1.0"` plus matching `:git/sha` values.
- Direct GitHub tag lookups confirm the registry coordinates line up with the published tags for all eight entries: `isaac-agent`, `isaac-server`, `isaac-acp`, `isaac-cron`, `isaac-hail`, `isaac-hooks`, `isaac-discord`, and `isaac-imessage`.
- Foundation proof passed on current GitHub `main` (`36e4a6f`): `env ISAAC_GIT=1 bb features-all features/module/modules.feature features/module/modules_list.feature features/cli/init.feature` in `isaac-foundation` → `11 examples, 0 failures, 34 assertions`.
