---
# isaac-67cq
title: Confirm and delete dead entity tables (gauges, foundries, berths)
status: draft
type: task
priority: low
created_at: 2026-09-30T02:44:10Z
updated_at: 2026-09-30T02:44:10Z
---

Found during the cleanup survey (2026-09-30). Foundation keeps entity tables `:gauges`, `:foundries` and `:berths` in its hard-coded entity-collection sets, but no manifest in any repo checked out locally contributes or uses them.

## Wanted

Confirm they are dead (grep every sibling repo and zanebot/yopp config), then delete them. If one is alive, document its owner instead.
