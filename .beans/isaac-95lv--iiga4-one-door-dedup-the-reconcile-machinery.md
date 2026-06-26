---
# isaac-95lv
title: 'iiga(4): one door — dedup the reconcile machinery'
status: completed
type: task
priority: normal
tags:
    []
created_at: 2026-06-15T21:31:34Z
updated_at: 2026-06-26T21:12:56Z
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

## Verification

Verified on fetched GitHub heads:

- `isaac-foundation` `54a36d0cf52c5351a6b50c01a4941f1d300f0fa8`
- `isaac-agent` `75f4ef5de312c93e5d5b2bd9c325f8f06433ca2e`
- `isaac-server` `45a191f89efbaf9aeff6f987d9f4a69d02dd8878`
- `isaac-discord` `daeecc27b2e44d4490ac2165bc95813d5aa9c15d`

Structural checks:

- `isaac.comm.factory` / `isaac.comm.registry` now live only in foundation: [factory.clj](/private/tmp/isaac-95lv-foundation/src/isaac/comm/factory.clj:1), [registry.clj](/private/tmp/isaac-95lv-foundation/src/isaac/comm/registry.clj:1)
- Agent/server no longer carry duplicate `src/isaac/comm/factory.clj` or `src/isaac/comm/registry.clj`
- Agent install path is explicitly store-only in [isaac.agent.config.install](/private/tmp/isaac-95lv-agent/src/isaac/agent/config/install.clj:1)
- Server still owns service start at [isaac.service.runtime/start-all!](/private/tmp/isaac-95lv-server/src/isaac/service/runtime.clj:36) and [server.app](/private/tmp/isaac-95lv-server/src/isaac/server/app.clj:220)

Proofs were green:

- `isaac-agent`: `bb ci` -> `1110` spec examples, `550` feature examples, `0` failures
- `isaac-server`: `env ISAAC_GIT=1 bb ci` -> `155` examples, `0` failures, `279` assertions plus green feature phase

This matches the worker handoff: agent runtime no longer starts services, reconcile/service start is server-owned, and the duplicated comm factory/registry sources are gone.
