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

Done: all rename-train branches remain pushed. HTTP `bb ci` is green (121/0/249 specs; 46/0/96 features). Hooks `bb ci` is green after pinning Foundation `ad0a97b7f039814dc92916299baf3c07a5b86f3a`, Agent `8aecfc3a57fef0803c35f81bd4a3f76292b227f1`, and HTTP `11e43014ad5c7b9c8bb693e0eb4f673bbd23991d`. Hail now explicitly loads the Agent config CLI refresh wrapper; its three focused failing features are individually green.

Current RED: hail full `bb ci` still fails seven inheritance/prompt cases only when the complete generated suite runs in one process; focused runs are green. The ordering reproducer is a multi-feature invocation, indicating shared harness state between generated `describe`s, not product behavior.

Next: resume at `isaac-hail-8got/feature-steps/isaac/hail_steps.clj:41`; inspect/reset hail band registry and cached `:loaded-config-result` in `after-scenario` so each scenario starts clean, then rerun full hail `bb ci`. Continue remaining five acceptance repos, registry/config final pins, and old-id grep.