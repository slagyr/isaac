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

Done: pushed coordinated `bean/isaac-8got` branches across 18 repositories. HTTP `bb ci` is green (121/0/249 specs; 46/0/96 features); hooks, cli-server, and Claude Code `bb ci` are green; ACP specs are green. Registry is `961e8010`; deployment manifest is `38d3cd0`. The bean is HOLD with human notifications sent because the clean cutover needs an explicit multi-repo landing order for branches that pin unlanded Foundation/Agent/HTTP commits.

Current RED: hail full suite has seven cross-scenario inheritance/prompt failures; ACP features have three Episodes-policy failures in the partial train. Last test command: `bb features features/delivery.feature:847 features/band-inheritance.feature` in `isaac-hail-8got`; result: 8 examples, 4 failures, 21 assertions (all four failures in band inheritance after the cancellation scenario).

Next after explicit planner/human release: resume at `isaac-hail-8got/feature-steps/isaac/hail_steps.clj:41`; define the train landing order or split the bean, then verify each contributor against landed Foundation/Agent/HTTP commits. Diagnose/reset cancellation-scenario state before band inheritance, pin ACP to the Episodes train, complete Discord, update final registry/config SHAs, run the seven-repo `bb ci` loop, and run the old-id grep.
## Held (awaiting human, 2026-09-13)

Escalated to human by **scrapper**@isaac-work-1. Blocking: coordinated clean-cutover requires an
atomic landing order across 18 repositories, but verifier can only fast-forward one `bean/isaac-8got`
branch per repo while each contributor branch pins unlanded Foundation/Agent/HTTP SHAs; current hail
and ACP full-suite failures also depend on that train state. Planner must split or explicitly define
landing order/pinned branch verification. Resumes only on explicit human action (re-hail the work/plan
band, or re-promote). No crew re-picks this until then.
