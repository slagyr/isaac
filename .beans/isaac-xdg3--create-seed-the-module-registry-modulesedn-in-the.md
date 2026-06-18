---
# isaac-xdg3
title: Create + seed the module registry (modules.edn) in the isaac repo
status: in-progress
type: task
priority: normal
tags:
    - unverified
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
