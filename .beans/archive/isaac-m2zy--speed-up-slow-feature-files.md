---
# isaac-m2zy
title: "Speed up slow feature files"
status: completed
type: task
priority: low
created_at: 2026-05-06T14:23:52Z
updated_at: 2026-05-06T14:40:35Z
---

## Description

The feature suite has a few noticeably slow files, especially features/modules/activation.feature, features/server/logging.feature, and features/server/status.feature. Current profiling shows module activation/discovery paths spending time in local/root module discovery, and lightweight server features paying more startup/config-home cost than they need. Optimize the shared code paths without changing feature behavior. Acceptance: identify the slowest feature files with measurement, reduce their runtime materially, keep bb spec and bb features green, and document the before/after timings in the bead notes.

## Notes

Measured with bb feature-docs on the hotspot files. Before: Module activation scenarios were 1.01413s / 1.01085s / 1.01152s, Discord-as-a-module was 1.04977s. After: Module activation scenarios are 0.00365s / 0.00173s / 0.00217s, Discord-as-a-module is 0.00581s. Shared changes: defer local/root add-deps from discovery to first activation, cache loaded module coords, use an isolated default home for synthetic server runs, and skip change-source startup entirely for startup-only scenarios by honoring server.hot-reload=false in the server test helper and setting it in the slow module features. Full bb spec and bb features pass.

