---
# isaac-e89r
title: Retire (delete) the isaac monolith
status: todo
type: task
priority: normal
created_at: 2026-06-16T17:18:54Z
updated_at: 2026-06-16T17:18:54Z
---

The monolith is now DETACHABLE — verified 2026-06-16:
- Every module's runtime AND test deps reference only MODULE coordinates (isaac-foundation, isaac-agent,
  isaac-server, *-spec, *-test-support, marigold.* fixture modules). NONE reference io.github.slagyr/isaac
  or isaac-spec. (server's only ref is a benign :exclusions.)
- The fleet finished the acp/discord/imessage off-monolith test-host migration + created the isaac-agent-spec
  / isaac-server-spec / *-test-support coordinates — so the originally-proposed enabler + 3 migration beans are
  already done.
- CI frozen: ci-tests.yml is workflow_dispatch-only as of 2026-06-16.

## Homing (verified earlier; churn since only added to modules)
- src: 100% homed in modules.
- features: accounted — 4 @wip provider features -> scrap (isaac-gky8); component_hot_reload relocated to
  isaac-cron + isaac-hooks.
- spec: all homed except isaac.harness.feature-cleanup (monolith combined-suite teardown — dies with the
  monolith) and 1 stray step-file (server/cli/cli_steps.clj).

## Pre-deletion checklist
1. Re-scan: confirm NO repo still pulls io.github.slagyr/isaac or isaac-spec (runtime or alias).
2. Confirm acp/discord/imessage + cron CI are green off-monolith (their migrations just landed).
3. isaac-gky8: scrap the 4 @wip provider messaging features (or let them die with the monolith).
4. Carry server/cli/cli_steps.clj to isaac-server, or confirm its step phrases are covered by
   isaac-server/.../server_steps.clj.
5. Confirm no module's :dev-local local-roots point at ../isaac/* (they should point at ../isaac-foundation,
   ../isaac-agent, ../isaac-server).

## Deletion
- Remove src/, features/, spec/ from the monolith repo. Relocate/keep any in-tree modules/* fixtures only if
  still referenced (foundation/agent/server now own their fixtures — confirm none reference ../isaac/modules).
- Archive the repo (or leave a README pointing at the module repos). Remove/disable CI.

## Acceptance
- Monolith repo holds no live code.
- All module repos green with the monolith deleted/unreachable.
