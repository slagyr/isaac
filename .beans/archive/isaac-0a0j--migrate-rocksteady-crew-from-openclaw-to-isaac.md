---
# isaac-0a0j
title: Migrate Rocksteady crew from OpenClaw to Isaac
status: in-progress
type: task
priority: normal
created_at: 2026-05-12T01:25:16Z
updated_at: 2026-05-13T20:06:23Z
---

## Description

User requested migration of the Rocksteady OpenClaw agent into Isaac. Inspect the OpenClaw soul and durable memory sources, back up any existing Isaac Rocksteady soul, migrate the authoritative files into Isaac, verify the result, and record durable guidance for future migrations.

## Notes

Migrated Rocksteady into Isaac. Verified OpenClaw workspace-rocksteady had no durable memory files; only generic workspace scaffolding. Verified authoritative persona source was ~/.openclaw/agents/rocksteady/agent/SOUL.md, while ~/.openclaw/workspace-rocksteady/SOUL.md was a generic placeholder. Installed soul at ~/.isaac/config/crew/rocksteady.md, created ~/.isaac/config/crew/rocksteady.edn, initialized ~/.isaac/crew/rocksteady/memory/, and verified bb run isaac crew plus resolve-crew-context include rocksteady.



## Verification failed

Current environment no longer shows the migrated Rocksteady crew. `bb run isaac crew` does not list `rocksteady`, `~/.isaac/config/crew/` has no `rocksteady` files, and `~/.isaac/crew/rocksteady/memory/` does not exist. Reopening because the migrated state described in the bean notes is not present on this checkout/home anymore.
