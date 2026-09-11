---
# isaac-cgpt
title: 'isaac-foundation: berths.clj gates lifecycle with a def-aliased Reconfigurable snapshot — extend-based implementations are never loaded'
status: draft
type: bug
priority: high
tags:
    - foundation
    - protocol
created_at: 2026-09-04T04:43:20Z
updated_at: 2026-09-04T04:43:20Z
---

`src/isaac/config/berths.clj:15` — `(def Reconfigurable reconfigurable/Reconfigurable)` copies the protocol MAP at load time; lines 300/309/325 call `(satisfies? Reconfigurable node)` on that copy. `extend` alters the protocol VAR's root (a new map), so any comm that gains Reconfigurable via `extend` after foundation loads is invisible: on-load/on-config-change!/on-unload never fire. Inline deftype methods pass only because the snapshot still holds `:on-interface`. Bit isaac-discord 0.1.12 (see isaac-ay0s): gateway never started on zanebot 2026-09-04. `isaac.api/Reconfigurable` in isaac-agent is the same kind of alias and is a hazard for any `satisfies?` caller.
Fix: reference the live var — `(satisfies? reconfigurable/Reconfigurable node)` (or `@#'reconfigurable/Reconfigurable`) at each call site; audit every `(def X some-protocol)` alias used with `satisfies?`/`extends?` (grep foundation, agent, server). Scenario: a comm that gains Reconfigurable via `extend` AFTER berths loaded has its on-load invoked at node creation (foundation feature; fixture comm defined in the step file). Ships via the foundation tag + release.yml + brew train; until then, comms keep Reconfigurable inline.
