---
# isaac-8got
title: 'Leg 6 (end cap) — rename isaac-server → isaac-http: repo, module id :isaac.http, berth ids, registry, contributor manifests, zanebot config'
status: in-progress
type: feature
priority: high
tags:
    - server
    - rename
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-13T21:16:08Z
parent: isaac-3q4m
blocked_by:
    - isaac-vs6f
    - isaac-jrj0
    - isaac-q9j6
    - isaac-zgfx
    - isaac-yrxx
---

Parent: isaac-3q4m (decision 8). Last; depends on legs 1–5.

Clean cutover, no aliases: GitHub repo rename (redirect kept by GitHub), module id `:isaac.server` → `:isaac.http`, manifest berth ids `:isaac.server/route` → `:isaac.http/route` (and any others the server still declares after leg 1), registry `modules.edn`, zanebot `config/isaac.edn` :modules entry, every contributor manifest that names the route berth (hooks, hail, cli-server, claude-code after leg 3), CI workflows and hail secrets, docs and READMEs, deploy notes. Train: one coordinated release of every contributor plus the renamed module, then `isaac modules install isaac.http` + remove `isaac.server` on zanebot, restart, smoke every route.

Scenarios: none new — clean cutover; unknown berth ids already fail at load. `modules list` showing isaac.http and no isaac.server is a one-time acceptance check.

## Acceptance

modules list on zanebot shows isaac.http and no isaac.server; every route smokes after the coordinated train; no :isaac.server/ berth id remains in any contributor manifest (one-time grep across module repos).

```
for r in isaac-http isaac-hooks isaac-hail isaac-cli-server isaac-claude-code isaac-acp isaac-discord; do (cd $r && bb ci); done
```

## Held (awaiting human, 2026-09-13)

Escalated to human by **scrapper**@isaac-work-1. Blocking: the GitHub credential cannot rename
`slagyr/isaac-server` (`PATCH /repos/slagyr/isaac-server` returned 404), and the required zanebot
service restart is reserved for the human operator. Resumes only on explicit human action (re-hail
the work/plan band, or re-promote). No crew re-picks this until then.

## Released (2026-09-13, planner)

GitHub rename is done: `slagyr/isaac-server` → `slagyr/isaac-http` (redirect kept). Old URL
resolves. Worker GitHub token is not needed for the rename.

Zanebot is still the 0.1.24 keg / old pins. **Do not** `isaac modules install isaac.http`, remove
`isaac.server`, or restart the service. That is the human deploy train after this lands.

This turn: module id `:isaac.http`, berth ids, registry, contributor manifests, CI, docs. Leave
zanebot `config/isaac.edn` as a note for the deploy, or patch it in Zane's config repo without
restarting. Acceptance `modules list` on zanebot waits for that deploy — do not HOLD for it.
Verify on the code grep + `bb ci` loop.

## Worker checkpoint (scrapper@isaac-work-1, 2026-09-13)

Done: ACP now loads Episodes `0cbe24b`, repairs coordinate-scoped feature pins, and explicitly
registers the Episodes policy at `738fe6b`; focused Episodes features pass 4/0/17 and ACP `bb ci`
passes (72/0/198 specs; 64/0/151 features). Discord `bb ci` passes after its checkout/timeout repair
at `d7db28d` (46/0/96 native specs; 98/0/226 JVM specs; 67/0/134 features, 3 pending). Registry
train pins are updated at `125feed9`; undeployed zanebot config notes are updated at `e00cf3c`.
HTTP and Hooks passed in the final acceptance-loop attempt.

Current RED: the acceptance loop stopped in Hail at 149 examples, 1 failure, 572 assertions,
2 pending. `features/crew-tool.feature:78` inspected the pending queue before the asynchronous turn
completed. A local, untested repair now makes `sole-pending-hail-edn-contains` await the turn.

Next: resume at `isaac-hail-8got/feature-steps/isaac/hail_steps.clj:188`; run
`bb features features/crew-tool.feature:78`, commit/push if green, rerun Hail `bb ci`, then continue
the seven-repository loop from CLI Server through Discord and run the old-ID grep.

No zanebot install, removal, restart, modules-list check, or route smoke is performed by this worker.
Those deployment checks remain deferred to the human.

## Held (awaiting human, 2026-09-13)

Escalated to human by **scrapper**@isaac-work-1. Blocking: coordinated clean-cutover requires an
atomic landing order across 18 repositories, but verifier can only fast-forward one `bean/isaac-8got`
branch per repo while each contributor branch pins unlanded Foundation/Agent/HTTP SHAs; current hail
and ACP full-suite failures also depend on that train state. Planner must split or explicitly define
landing order/pinned branch verification. Resumes only on explicit human action (re-hail the work/plan
band, or re-promote). No crew re-picks this until then.

## Released (2026-09-13, planner) — no split, land in this order

Do **not** split. The pins *are* the cutover. One bean, one verify, explicit land order.

Verifier fast-forwards `bean/isaac-8got` in this sequence (later repos pin earlier SHAs; registry last):

1. isaac-foundation
2. isaac-http
3. isaac-agent
4. isaac-hail, isaac-hooks, isaac-cli-server, isaac-claude-code, isaac-acp, isaac-discord, isaac-episodes, isaac-cron, isaac-imessage, isaac-mcp, isaac-foreman, isaac-cli-proxy, isaac-worksite
5. isaac (registry `modules.edn`)

Hail 7-red and ACP 3-red stay on this bean. Work: finish the hail CLI config-snapshot wrapper (`hail_steps.clj` around `main/run`), prove `delivery.feature:847` then `band-inheritance.feature` green, then hail `bb ci`. ACP: pin to the episodes train and prove or fix the three policy failures. Follow-up bean only if the same red exists on origin/main with foundation+http+agent already landed.

Still do **not** install/remove modules or restart zanebot.
