---
# isaac-v5gs
title: "Fix server-running disk config load in mem-fs tests"
status: completed
type: bug
priority: low
created_at: 2026-04-27T19:36:11Z
updated_at: 2026-04-28T03:10:50Z
---

## Description

The full quality gates still show one unrelated spec failure and one unrelated feature failure in the server feature-step harness. In mem-fs mode, spec/isaac/features/steps/server.clj materializes the virtual state directory to disk before starting the app, but the started app sees an empty config. This breaks spec/isaac/features/steps/server_spec.clj ('loads config from disk when no in-memory injections are present') and features/config/hot_reload.feature ('a change under config/ fires a reload and updates the cfg'). Investigate the mem-fs-to-disk handoff in server-running, restore config loading from the copied state, and get both gates green again.

