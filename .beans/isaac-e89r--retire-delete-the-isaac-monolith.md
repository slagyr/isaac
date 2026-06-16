---
# isaac-e89r
title: Retire (delete) the isaac monolith
status: in-progress
type: task
priority: normal
created_at: 2026-06-16T17:18:54Z
updated_at: 2026-06-16T18:02:46Z
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
4. server/cli/cli_steps.clj needs NO carry. It is agent/LLM-flavored (reply assertions reading bridge
   state + provider/HTTP/drive capture, requires isaac.drive.dispatch) — NOT foundation-generic CLI. Its step
   phrases are already homed: reply-contains/matches/does-not-contain in isaac-agent
   (spec/isaac/agent/module_steps.clj), and 'isaac is run in the background' in isaac-foundation
   (foundation/cli_steps.clj). It dies with the monolith. (Quick-confirm the provider/HTTP/drive-capture
   registrations exist in agent's test harness; no verbatim carry.)
5. Confirm no module's :dev-local local-roots point at ../isaac/* (they should point at ../isaac-foundation,
   ../isaac-agent, ../isaac-server).

## Deletion
- Remove src/, features/, spec/ from the monolith repo. Relocate/keep any in-tree modules/* fixtures only if
  still referenced (foundation/agent/server now own their fixtures — confirm none reference ../isaac/modules).
- Archive the repo (or leave a README pointing at the module repos). Remove/disable CI.

## Acceptance
- Monolith repo holds no live code.
- All module repos green with the monolith deleted/unreachable.


## Done (pending review, then verifier)
- CI frozen: ci-tests.yml -> workflow_dispatch only.
- Last tie fixed: isaac-server :test telly fixture repointed ../isaac/modules -> ../isaac-agent/modules
  (isaac-server commit 75a2e01). Verified: :test classpath has 0 monolith paths, telly loads,
  configurator_spec 6/0.
- Re-scan: NO module references the monolith (runtime or test); server's only hit is the benign :exclusions.
- Deleted src/ (144), spec/ (183), features/ (134), modules/ (48) — commit f84afa01, -62114 lines.
- README replaced with a retirement notice + where-the-code-lives map.

## CORRECTION — do NOT archive/delete the repo
The repo still hosts .beans/ (607 beans — the project's issue tracker) and the architecture docs.
"Retire the code" != "delete the repo." The repo persists as the beans/docs home; CODE is removed.
(My earlier 'archive the repo' note was wrong on this point.)

## Remaining for the verifier
- Confirm all module CIs are green off-monolith (gh), especially isaac-server (telly repoint just landed)
  and acp/discord/imessage/cron (their migrations).
- Husk cleanup (optional, not blocking): bb.edn/deps.edn/dev/ now reference removed dirs — gut or leave.
- Doc relocation (optional): FOUNDATION.md / ISAAC.md describe foundation/agent architecture — consider
  moving into isaac-foundation; leave AGENTS.md (toolbox/beans workflow still applies to this repo).
