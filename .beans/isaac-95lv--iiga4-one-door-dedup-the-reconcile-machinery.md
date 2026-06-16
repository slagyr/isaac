---
# isaac-95lv
title: 'iiga(4): one door — dedup the reconcile machinery'
status: in-progress
type: task
priority: normal
created_at: 2026-06-15T21:31:34Z
updated_at: 2026-06-16T04:57:06Z
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


## Verification notes

- Verification failed on 2026-06-16. The agent-side collapse itself is behaving as intended after clearing stale generated features from deleted specs: `isaac-agent` `env ISAAC_GIT=1 bb spec spec/isaac/config/install_spec.clj spec/isaac/config/runtime_spec.clj` passed (`5 examples, 0 failures`), and after cleaning `target/gherclj/generated`, `env ISAAC_GIT=1 bb features` also passed (`523 examples, 0 failures`). The committed agent code in [src/isaac/config/install.clj](/Users/micahmartin/agents/verify/isaac-agent/src/isaac/config/install.clj:1) and [src/isaac/config/runtime.clj](/Users/micahmartin/agents/verify/isaac-agent/src/isaac/config/runtime.clj:1) is store-only and no longer starts services.
- But the server-owned single-door path is not green yet. `isaac-server` `ISAAC_GIT=1 bb ci` currently fails in `features/server/services.feature` with a config-schema collision at `:comms` because the server test-only ownership fixture still declares the comm slot under `:isaac.server/comm` in [test-resources/isaac-manifest.edn](/Users/micahmartin/agents/verify/isaac-server/test-resources/isaac-manifest.edn:15), while the live slot owner is `:isaac.agent/comm` in [isaac-agent/resources/isaac-manifest.edn](/Users/micahmartin/agents/verify/isaac-agent/resources/isaac-manifest.edn:514). So the deduped server path is not yet preserving the existing load/config-change/unload behavior coherently in the affected integration suite.
- This bean is also explicitly blocked by `isaac-bju6` and `isaac-qokc`, and both remain red on current heads.
