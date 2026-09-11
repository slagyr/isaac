---
# isaac-oc3f
title: 'Foundation''s ''isaac server'' boots without the host hooks: reloader, resume scan, delivery workers and Discord never start (legs 1+3 train rolled back 2026-09-11)'
status: todo
type: bug
priority: critical
tags:
    - foundation
    - server
    - discord
created_at: 2026-09-11T15:06:55Z
updated_at: 2026-09-11T15:06:55Z
parent: isaac-3q4m
---

Parent: isaac-3q4m (leg 1 follow-up). Repos: **isaac-foundation** (`isaac.runner`, the
`server` CLI command), **isaac-server** (`server/app.clj` start!/before-components!/after-components!).

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
2. **Discord's contribution is stranded.** isaac-discord still contributes
   `:isaac.server/service`; the component runtime reads only
   `:isaac/component`. Clean cutover means discord (and any other
   contributor: hail?) gets a release with the new key — inventory every
   manifest for `:isaac.server/service` and bump them in the same train.
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
- Every contributor manifest on `:isaac.server/service` moved (discord; grep
  the fleet), released, and pinned in the same train as the fix.

```
cd isaac-server && bb features features/server/ && bb spec && bb ci
cd isaac-foundation && bb features features/component/ && bb ci
```
Redeploy train: foundation 0.1.25 keg (HEAD-1cd0bfc is still installed on
zanebot — relink only), server fix release, discord release, claude 0.1.10,
one restart, full checklist.
