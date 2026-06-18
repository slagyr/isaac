---
# isaac-xdg3
title: Create + seed the module registry (modules.edn) in the isaac repo
status: in-progress
type: task
priority: normal
tags: []
created_at: 2026-06-18T14:32:19Z
updated_at: 2026-06-18T17:48:30Z
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

When a module repo cuts a new release tag, bump that entry's `:git/tag` and
`:git/sha` in `modules.edn` on `isaac` `main`. Order: tag the module repo,
resolve the tag SHA (`gh api repos/slagyr/<repo>/git/ref/tags/<tag>`), update
the registry entry, push.

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

- Verification failed on 2026-06-18. The module CLI behavior is green, but the published registry data is not internally consistent, so acceptance is not met.
- Foundation proof passed on current GitHub `main` (`36e4a6f`): `env ISAAC_GIT=1 bb features-all features/module/modules.feature features/module/modules_list.feature features/cli/init.feature` in `isaac-foundation` → `11 examples, 0 failures, 34 assertions`.
- The problem is `modules.edn` itself. The `:git/sha` values for at least `:isaac.agent` and `:isaac.server` do not match the published GitHub `v0.1.0` tags. Current file values are [modules.edn](/Users/micahmartin/agents/verify/isaac/modules.edn:1) `a795402548c662c320ef54f83eb2e448fb1f88f8` for agent and [modules.edn](/Users/micahmartin/agents/verify/isaac/modules.edn:5) `6364564cc1cc7c688795b689c749e650ad6a290d` for server.
- Direct GitHub tag lookups show `v0.1.0` currently resolves to `dc93739dfa09a1804019aed71f41d2fc76986553` for `isaac-agent` and `d3ffd7f1910824f436b104af60af3290df09ae5e` for `isaac-server`.
- The other six registry entries I checked match their `v0.1.0` tags. The bean stays `in-progress` because the seeded registry does not yet contain uniformly valid tagged coordinates.
