---
# isaac-95lv
title: 'iiga(4): one door — dedup the reconcile machinery'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-15T21:31:34Z
updated_at: 2026-06-16T02:14:52Z
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



## Worker handoff

- isaac-agent f46df92: store-only install/runtime; deleted agent configurator; comm/activation/hot-reload features moved off agent
- isaac-server cc1ced2: server boot owns reconcile-modules!, install-config-berths!, service start-all!; comm_extension + activation features added; factory accepts :isaac.agent/comm manifests
- Foundation pinned to c12ebe3 in both repos
- Agent + server CI green locally
- Remaining: agent still carries comm/factory.clj for module defmethods (not invoked on agent boot); hot_reload features live in monolith isaac only
