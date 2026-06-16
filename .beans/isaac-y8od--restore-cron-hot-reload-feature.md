---
# isaac-y8od
title: Restore cron hot-reload feature
status: completed
type: bug
priority: normal
created_at: 2026-06-15T23:36:17Z
updated_at: 2026-06-16T00:30:00Z
---

Repo: isaac-cron

Scenario: Cron config hot reload -> Cron prompt content change is picked up at runtime

Done in isaac-cron `bbd608a`:
- Rewrote `features/hot_reload.feature` to use `config:` + `cron config is:` +
  `the scheduler ticks at` (no server-only harness steps)
- Added `cron-config-is` step in `scheduler_steps.clj` delegating to agent
  `config-applied`
- `hot_reload.feature` and `origin.feature` green in `bb ci`; no isaac-acp dep