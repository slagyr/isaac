---
# isaac-09zr
title: Run @slow feature tests in CI (launcher/subprocess end-to-end lane)
status: completed
type: task
priority: normal
tags: []
created_at: 2026-06-19T15:30:35Z
updated_at: 2026-06-19T16:15:00Z
---

@slow feature scenarios are EXCLUDED from the fast lane (:features alias uses
-t ~slow). They are the launcher-subprocess, real-classpath-compose,
transitive-load end-to-end tests — exactly the ones that catch
classpath/composition bugs the in-process tests cannot (e.g. isaac-92p3's
foundation-shadowing). Right now they run NOWHERE, so they can silently rot.

## Scope

• Add a CI lane (job) that runs the @slow features (-t slow, or drop ~slow) —
  start with isaac-foundation, then roll the pattern to the module repos that
  have @slow scenarios.
• Current @slow scenarios to cover: dhzy real-load (greet via launcher), 0yp1
  transitive activation, 92p3 foundation-not-shadowed (once written).

## "Make them all pass first"

Audit the existing @slow scenarios and get them GREEN before the lane gates
merges. Some will be red until their bean lands (92p3's scenario fails until
92p3 is fixed) — keep those @wip, or run the lane non-gating until green, then
flip to required.

## Open decisions

• Cadence: separate nightly/scheduled job vs per-PR slow lane vs on-label. Lean
  nightly + on-demand, promoted to required-on-merge once stable.
• One lane per repo, or an aggregate that checks out + runs each module's slow
  suite.

## Acceptance

• A CI lane runs the @slow features and reports pass/fail.
• All currently-green @slow scenarios pass in it; red ones are tracked (@wip or
  pinned to their bean).
• New @slow scenarios are picked up automatically (tag-driven, not enumerated).



## Implementation (work-2)

HEAD: isaac-foundation (see push SHA)

- `bb features-slow` routed through `bb.test-tasks/run-features-slow!` (60s timeout).
- CI job `slow-features` runs `bb features-slow` on push/PR, nightly (06:00 UTC), and workflow_dispatch.
- Audited @slow scenarios: 2 green (module_deps transitive launcher, modules_list real-load); no 92p3 scenario yet (lands with that bean; use @wip until green).

## Verification notes

- Verification passed on 2026-06-19 against fetched GitHub `isaac-foundation` `main` at `0f5256f`, not the stale local mirror.
- CI wiring is present in [ci-tests.yml](/Users/micahmartin/agents/verify/isaac-foundation/.github/workflows/ci-tests.yml:1): separate `slow-features` job, triggered on `push`, `pull_request`, `workflow_dispatch`, and nightly `schedule`.
- The lane is tag-driven, not enumerated: [bb.edn](/Users/micahmartin/agents/verify/isaac-foundation/bb.edn:43) adds `bb features-slow`, and [bb/test_tasks.clj](/Users/micahmartin/agents/verify/isaac-foundation/bb/test_tasks.clj:33) runs all feature files with `-t "slow" -t "~wip"`.
- The current slow inventory is exactly the two green launcher scenarios the bean names: [features/module/module_deps.feature](/Users/micahmartin/agents/verify/isaac-foundation/features/module/module_deps.feature:14) and [features/module/modules_list.feature](/Users/micahmartin/agents/verify/isaac-foundation/features/module/modules_list.feature:67). There is no 92p3 slow scenario on this head yet.
- Focused proof passed: `env ISAAC_GIT=1 bb features-slow` in `isaac-foundation` → `2 examples, 0 failures, 4 assertions`.
