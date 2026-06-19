---
# isaac-09zr
title: Run @slow feature tests in CI (launcher/subprocess end-to-end lane)
status: todo
type: task
created_at: 2026-06-19T15:30:35Z
updated_at: 2026-06-19T15:30:35Z
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
