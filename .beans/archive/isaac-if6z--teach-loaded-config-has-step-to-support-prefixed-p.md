---
# isaac-if6z
title: "Teach loaded-config-has step to support /-prefixed paths"
status: completed
type: task
priority: normal
created_at: 2026-05-04T19:36:17Z
updated_at: 2026-05-04T23:45:45Z
---

## Description

Why: the cccs module-index is keyed by namespaced keywords like :isaac.comm.pigeon. The current get-path in spec/isaac/features/steps/config.clj splits on '.', which destroys these keys. We need a path form that treats '.' as literal inside segments.

Approach: mirror isaac.config.cli.common/normalize-path. When the path string starts with '/', split on '/' and walk literally; otherwise keep existing dot-path behavior (back-compat for all current scenarios).

Examples:
- /module-index/isaac.comm.pigeon/manifest/id  ->  walks through the namespaced-keyword key
- crew.marvin.soul                              ->  unchanged

## Acceptance Criteria

get-path in spec/isaac/features/steps/config.clj recognizes leading '/' as opt-in slash-separated walk; dots inside segments are literal; existing dotted-path scenarios still pass; blank cell continues to assert nil

