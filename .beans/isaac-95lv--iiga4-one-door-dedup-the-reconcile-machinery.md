---
# isaac-95lv
title: 'iiga(4): one door — dedup the reconcile machinery'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-15T21:31:34Z
updated_at: 2026-06-26T21:02:13Z
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

- **Agent install is store-only** (`isaac.agent.config.install`); prompt/CLI never reconcile registries or start services
- **Server owns reconcile** (`isaac.config.install` + `configurator` + `install-config-berths!` + `service-runtime/start-all!` at boot only)
- **Production `:comms`** lives in server manifest only; agent test-resources supplies berth stub for features
- **isaac-foundation@54a36d0**: canonical `isaac.comm.factory` + `isaac.comm.registry` (removed from agent + server src)
- **Pins**: agent@75f4ef5, server@45a191f, foundation@54a36d0
- **CI** (work-3): agent `bb ci` → 1110 spec + 550 feature examples; server `bb ci` → 47 feature examples (spec slice green)
- Blockers kbzd/bju6/qokc completed; hot_reload acceptance remains in isaac-server (not monolith)
