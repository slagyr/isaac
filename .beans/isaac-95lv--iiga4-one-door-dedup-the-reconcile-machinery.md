---
# isaac-95lv
title: 'iiga(4): one door — dedup the reconcile machinery'
status: todo
type: task
priority: normal
created_at: 2026-06-15T21:31:34Z
updated_at: 2026-06-16T00:59:07Z
blocked_by:
    - isaac-kbzd
    - isaac-bju6
    - isaac-qokc
---

Child of epic isaac-iiga. Make server-only structural, not conventional.

- Collapse the duplicated turn-on machinery (agent AND server each carry config/install.clj, configurator.clj,
  comm/factory.clj, comm/registry.clj) so comm/service reconcile + start runs in exactly ONE place: server boot.
- Remove the agent-runtime's ability to start services (today install!'s `(when (seq registries) ...)` path).
  The agent loads config + registers Reconfigurables; it never starts services.

Acceptance:
- No code path outside server boot can start a service (prompt / CLI / agent runtime never invoke Service start).
- The duplicated reconcile collapses to one server-owned implementation.
- Existing slot load/config-change/unload behavior preserved.
