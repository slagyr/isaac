---
# isaac-oc3f
title: 'Foundation''s ''isaac server'' boots without the host hooks: reloader, resume scan, delivery workers and Discord never start (legs 1+3 train rolled back 2026-09-11)'
status: in-progress
type: bug
priority: critical
tags:
    - foundation
    - server
    - discord
    - episodes
created_at: 2026-09-11T15:06:55Z
updated_at: 2026-09-11T18:48:46Z
parent: isaac-3q4m
---

Parent: isaac-3q4m (leg 1 follow-up). Repos: **isaac-foundation** (`isaac.runner`, the
`server` CLI command), **isaac-server** (`server/app.clj` start!/before-components!/after-components!),
**isaac-discord** and **isaac-episodes** (same-train berth cutover).

jrj0 landed 2026-09-11: agent workers + resume/suspend are already `:isaac/component`
(agent `e9cba64` after 209q). Do not redo that. Remaining hook work is whatever
`app/start!` still does that production `isaac server` never runs (reloader, hail
delivery worker, any leftover named starts).

## What happened (zanebot, 2026-09-11 14:56Z — train for legs 1 + 3, rolled back at 15:04Z)

Foundation 0.1.25 (vs6f) + server 0.1.15 + claude-code 0.1.10 deployed; graceful
restart. The launchd unit runs `isaac server`, which is now **foundation's**
command: it calls `runner/start!` with the default `*before-components*` /
`*after-components*` hooks (`constantly nil`). isaac-server's `app/start!` —
which binds those hooks to start the config reloader, the resume scan, the
agent's delivery/episodes/turn workers and hail's delivery worker — is only
reachable from tests (`the Isaac server is started`) and never from the
production command. Boot log: `component/started http`, `runner/started
:components 1`, `server/started`, nothing else. Six in-flight turns were not
resumed, one delivery sat undelivered, no Discord ready (0.1.13 contributes
under `:isaac.server/service`, which the component runtime no longer reads),
no hail delivery. HTTP answered 401, so the door looked open while the crew
was gone.

Rolled back: foundation keg rebuilt by hand at f5bdde0, server 0.1.14,
claude 0.1.9; all six turns rebound on the rollback boot. The three releases
stay on main; the registry pins are rolled back.

## Defects

1. **The production boot path is not the tested boot path.** Features boot
   through `app/start!`; production boots through foundation's `isaac server`.
   Every boot behaviour the server adds in hooks is invisible to the runner.
   Fix per the epic's own design: the server's hook work becomes
   components (reloader, resume, delivery worker as `:isaac/component`
   entries — resume ranked so it runs after http, or accept order within a
   module), not dynamic bindings; `app/start!` collapses into "run the
   runner". The feature harness must boot through the same entry the unit
   file uses (`isaac server`, or the runner directly with no hooks).
2. **`:isaac.server/service` is stranded.** The component runtime reads only
   `:isaac/component`. Fleet grep 2026-09-11 (after 209q): **isaac-discord**
   `src/isaac-manifest.edn` and **isaac-episodes**
   `resources/isaac-manifest.edn`. 209q landed the episodes worker on the old
   key — same shape as Discord at 15:04Z. Both must cut over and pin in this
   train. Hail is already on `:isaac/component`; do not invent extra
   contributors.
3. **Train checklist gap.** "port 401" is not "up". Post-boot smoke must
   require `resume/scan-complete`, `hail/bound` for every requeued marker,
   Discord liveness :ready, and `:components N` matching the expected count.

## Acceptance (scenarios to plan)

- isaac-server: booting through `isaac server` (the CLI) starts http, the
  reloader, the resume scan and the delivery worker as components and logs
  `runner/started :components ≥ 4`; stopping stops them in reverse.
- isaac-foundation: a module contributing under a retired berth key fails
  load with a message naming `:isaac/component` (one-time acceptance is not
  enough here — this is the guard that would have caught Discord).
- Every contributor manifest on `:isaac.server/service` moved (**discord** and
  **isaac-episodes**; grep the fleet again before release), released, and
  pinned in the same train as the fix.

```
cd isaac-server && bb features features/server/ && bb spec && bb ci
cd isaac-foundation && bb features features/component/ && bb ci
```
Redeploy train: foundation 0.1.25 keg (HEAD-1cd0bfc is still installed on
zanebot — relink only), server fix release, discord release, episodes
release (new key), claude 0.1.10, one restart, full checklist. Do not
relink the keg until discord **and** episodes are on `:isaac/component`.

## Work checkpoint (2026-09-11, scrapper@isaac-work-1)

Done: Foundation diagnostic is green/pushed (`adbeace`); server runtime + plain app delegation + migrated feature harness are pushed (`e25af3c`); Episodes full suite is green/pushed (`ec2fc54`); Discord component implementation and full JVM specs are green/pushed (`9e2acd7`); Hail router/delivery component focused spec is green/pushed (`afc6253`). Foundation and Episodes full specs are green.

Next: finish production CLI-path acceptance, replace retired server app specs, run server/Foundation/Discord/Hail CI, rebase all branches, and record coordinated release/pin coordinates. Current RED: production boot spec observes nondeterministic map order and needs to assert reverse of observed startup rather than a fixed startup sequence. Resume at `isaac-server/spec/isaac/server/production_boot_spec.clj:54` and rerun `bb spec spec/isaac/server/production_boot_spec.clj`.
