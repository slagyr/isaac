---
# isaac-y8od
title: Restore cron hot-reload feature
status: completed
type: bug
priority: normal
created_at: 2026-06-15T23:36:17Z
updated_at: 2026-06-16T00:15:44Z
---

Repo: isaac-cron

Scenario: Cron config hot reload -> Cron prompt content change is picked up at runtime

Current state:
- scenario rehomed from isaac-agent to isaac-cron/features/hot_reload.feature
- scenario now runs instead of pending
- current failure: expected session transcript entry is missing after config rewrite and scheduler tick

Temporary containment:
- scenario tagged @wip in isaac-cron so it does not block the suite

Definition of done:
- remove @wip
- scenario passes in isaac-cron
- keep origin.feature green
