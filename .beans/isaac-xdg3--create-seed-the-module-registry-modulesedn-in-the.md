---
# isaac-xdg3
title: Create + seed the module registry (modules.edn) in the isaac repo
status: todo
type: task
priority: normal
created_at: 2026-06-18T14:32:19Z
updated_at: 2026-06-18T14:32:19Z
blocked_by:
    - isaac-why8
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
- Establish the process: when a module releases a new version, bump its tag in modules.edn.

## Acceptance
- modules.edn exists with all installable modules + valid tagged coords (matches the format in isaac-dhzy).
- `isaac modules available` fetches + lists them; `isaac modules install <name>` resolves to the right coord.

BLOCKED BY: the module-versioning task (needs stable tags to point at).
