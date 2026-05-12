---
# isaac-bnv0
title: "Implement lazy module activation (Phase 2 first cut)"
status: completed
type: task
priority: normal
created_at: 2026-05-05T02:08:43Z
updated_at: 2026-05-05T16:31:42Z
---

## Description

Why: features/modules/activation.feature describes the lazy-activation behavior we need. Modules declared in :modules are inert until something asks for a capability they extend. Activation requires the module's :entry namespace, which performs register! side effects. This is the smallest-useful slice of v0uu: covers the comm-registry-miss path that Discord will eventually use.

## Scope

- isaac.module.loader/activate! (module-id, module-index)
  - Looks up the module's :entry from the manifest
  - Calls (require :entry-ns) once; idempotent on subsequent calls
  - Wraps load failures in a structured error
  - Emits :module/activated (success) or :module/activation-failed (error)
- Comm registry miss hook
  - When the reconciler asks the comm registry for an :impl that isn't registered, walk :module-index for a module whose :extends declares that impl, activate it, retry the lookup
  - Real error if still missing after activation

## Telly test affordances (small additions)

- Telly's manifest declares :entry isaac.comm.telly (or current ns)
- Telly's on-startup! emits :telly/started log entry with the slot id
- Telly's namespace top-level checks ISAAC_TELLY_FAIL_ON_LOAD and throws if set (test hook for activation-failure scenario)

## Out of scope

- Provider registry lazy activation (separate slice)
- Tool registry lazy activation (separate slice)
- Schema composition — already done (isaac-ephr)

## Acceptance

- features/modules/activation.feature scenarios pass without @wip
- Existing reconciler.feature and Discord scenarios still pass

## Acceptance Criteria

features/modules/activation.feature passes without @wip; existing reconciler/Discord scenarios still pass

