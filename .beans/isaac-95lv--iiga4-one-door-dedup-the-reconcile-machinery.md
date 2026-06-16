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

- isaac-agent 515d0ae: removed production `:comms` + `:isaac.agent/comm` berth; test-resources supplies server-owned `:comms` + `:isaac.server/comm` berth stub; schema/root merges classpath builtins; telly/marigold comm keys → `:isaac.server/comm`
- isaac-server a8ea8b6: production manifest owns `:comms` (`:isaac.server/comm`); test-resources fixture drops duplicate `:comms` (crew/models/providers snapshot-only remain)
- isaac-foundation 3cc82a1: `comm-kinds` reads `:isaac.server/comm` (falls back to `:isaac.agent/comm`)
- Foundation pinned to 3cc82a1 in agent + server
- Agent CI green: 1039 spec + 523 feature examples; server CI green: 121 spec + 36 feature examples
- Cross-classpath `:comms` compose no longer collides (agent production manifest no longer contributes the table)
- Remaining: agent still carries comm/factory.clj for module defmethods (not invoked on agent boot); hot_reload features live in monolith isaac only; bean still blocked by isaac-bju6 / isaac-qokc
