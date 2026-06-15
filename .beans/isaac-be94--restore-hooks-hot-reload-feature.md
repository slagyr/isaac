---
# isaac-be94
title: Restore hooks hot-reload feature
status: todo
type: bug
priority: normal
created_at: 2026-06-15T23:36:17Z
updated_at: 2026-06-15T23:36:17Z
---

Repo: isaac-hooks

Scenario: Hook config hot reload -> Hook template content change is picked up at runtime

Current state:
- scenario rehomed from isaac-agent to isaac-hooks/features/hot_reload.feature
- scenario no longer pending in isaac-agent
- current failure: hook registry entry is still nil after harness startup/config rewrite, so the feature lacks working module-local hot-reload coverage

Temporary containment:
- scenario tagged @wip in isaac-hooks so it does not block the suite

Definition of done:
- remove @wip
- scenario passes in isaac-hooks
- keep existing hooks feature behavior unchanged
