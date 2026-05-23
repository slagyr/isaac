---
# isaac-y21w
title: Fix ad-hoc session store resolution
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-05-23T19:13:10Z
updated_at: 2026-05-23T19:32:57Z
---

resolve-store and charge/build both silently create ephemeral stores when no :session-store is in context. Remove those fallbacks: resolve-store should require :session-store or throw; charge/build should use session-store as-is (nil is fine). api.clj and bridge/status.clj state-dir arities should call store/create directly.

## Summary of Changes

- resolve-store now throws immediately when :session-store is absent; removed the state-dir sidecar fallback
- charge/build falls back to store/registered-store instead of ad-hoc sidecar creation
- api.clj state-dir arities call store/create directly instead of relying on resolve-store
- bridge/status.clj string arity calls store/create directly
- describe blocks whose system/with-system wrapped with-memory-store needed to swap to with-nested-system to preserve sessions store
- server_steps.clj cron tick helper now includes :sessions {:store (store/create ...)} in system context so the cron service can resolve the store
- All 1667 unit specs and 638 feature scenarios pass
