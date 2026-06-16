---
# isaac-be94
title: Restore hooks hot-reload feature
status: done
type: bug
priority: normal
created_at: 2026-06-15T23:36:17Z
updated_at: 2026-06-16T00:15:00Z
---

Repo: isaac-hooks

Scenario: Hook config hot reload -> Hook template content change is picked up at runtime

Done in isaac-hooks `270a8c9`:
- Added `feature-steps/isaac/hooks_steps.clj` with module-local config harness wiring
  `hooks/registry` through `runtime/install!` / `runtime/reconcile!`
- Removed `@wip` from `features/hot_reload.feature`; scenario green in `bb ci`
- Existing `hooks.feature` scenarios remain pending (unchanged)
