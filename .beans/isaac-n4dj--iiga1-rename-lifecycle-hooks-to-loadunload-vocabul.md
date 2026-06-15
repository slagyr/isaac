---
# isaac-n4dj
title: 'iiga(1): rename lifecycle hooks to load/unload vocabulary'
status: todo
type: task
priority: normal
created_at: 2026-06-15T21:31:34Z
updated_at: 2026-06-15T21:38:28Z
---

Child of epic isaac-iiga. Pure vocabulary/structure change — no behavior change; the resource-open split
happens in child (3).

- Module protocol (foundation): on-startup -> on-load, on-shutdown -> on-unload. Update every implementor.
- Reconfigurable protocol (foundation): on-startup! -> on-load; keep on-config-change!; ADD on-unload.
  Implement on-unload wherever slot/component eviction happens today (comm slots, components).
- Cross-repo sweep: foundation declares; agent/server/acp/etc. implement.

Acceptance: protocols renamed, every implementor updated (incl new on-unload), all affected suites green.
No new behavior.
