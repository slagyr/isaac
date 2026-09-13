---
# isaac-8got
title: 'Leg 6 (end cap) — rename isaac-server → isaac-http: repo, module id :isaac.http, berth ids, registry, contributor manifests, zanebot config'
status: in-progress
type: feature
priority: high
tags:
    - server
    - rename
    - unverified
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-13T23:47:51Z
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

## Worker completion checkpoint (scrapper@isaac-work-1, 2026-09-13)

Done: clean-cutover branches are pushed and clean. HTTP is `11e4301`; Hooks `65a9e63`; Hail
`4dc44ef`; CLI Server `ae0743a`; Claude Code `edf0195`; ACP `738fe6b`; Discord `d7db28d`.
ACP loads Episodes `0cbe24b`, repairs coordinate-scoped pins, and explicitly registers the Episodes
policy. Hail now waits for its asynchronous tool turn before inspecting the pending queue. Discord
selects dev-local only when every split dependency exists and gives JVM features a 180-second budget.
Registry train pins are `d93d6907`; undeployed zanebot config notes are `c6705ace`.

Verification: the complete seven-repository `bb ci` acceptance loop is green. Final counts include
HTTP 122/0/250 specs + 46/0/96 features; Hail 160/0/371 specs + 149/0/573 features (2 pending);
ACP 72/0/198 specs + 64/0/151 features; Discord 46/0/96 native specs + 98/0/226 JVM specs +
67/0/134 features (3 pending). Active module repositories, registry files, and deployment config have
zero matches for `:isaac.server/`, `isaac.server`, `io.github.slagyr/isaac-server`, and
`slagyr/isaac-server`. Historical bean and archived hail records were intentionally excluded.

Next: verifier starts at `isaac-http-8got/resources/isaac-manifest.edn:1`, reviews the coordinated
branch heads and registry pins, and repeats the bean's grep + seven-repository `bb ci` gate.

No zanebot install, removal, restart, modules-list check, or route smoke was performed. Those deploy
checks remain deferred to the human per planner release.

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
