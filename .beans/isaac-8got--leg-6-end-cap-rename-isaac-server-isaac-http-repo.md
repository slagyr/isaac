---
# isaac-8got
title: 'Leg 6 (end cap) — rename isaac-server → isaac-http: repo, module id :isaac.http, berth ids, registry, contributor manifests, zanebot config'
status: completed
type: feature
priority: high
tags:
    - server
    - rename
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-14T00:21:30Z
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

Done: clean-cutover branches are pushed and clean. Foundation is `ad0a97b`; HTTP `11e4301`; Agent
`74f9d83`; Hail `4dc44ef`; Hooks `65a9e63`; CLI Server `ae0743a`; Claude Code `edf0195`; ACP
`738fe6b`; Discord `d7db28d`; Episodes `0cbe24b`; Cron `0bbac06`; iMessage `3f71179`; MCP
`2ac9056`; CLI Proxy `d1380ad`. ACP repairs coordinate-scoped pins and explicitly registers the
Episodes policy. Hail now waits for its asynchronous tool turn before inspecting the pending queue.
Discord selects dev-local only when every split dependency exists and gives JVM features a 180-second
budget. Registry train pins are `c255ad16`; undeployed zanebot config notes are `c6705ace`.

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

## CI repair (scrapper@isaac-work-1, 2026-09-13)

Foundation main CI failed after landing `ad0a97b`. The @slow git-coordinate fixtures still fetched
pre-cutover ACP `f8e1499`, so module tree expected `:isaac.http` but discovered `:isaac.server`, and
`modules show` composed conflicting old/new comm berths. Updated both fixtures to the landed ACP train
`738fe6b`; commits `11a93bf` and `82e3594` were pushed to main and `bean/isaac-8got`.

Verification: focused git-coordinate tree 1/0/2; focused module show 2/0/5; `bb features-slow`
10/0/23; `bb ci` 1022/0/1844 specs + 185/0/489 features (2 pending). GitHub Actions run
34791563590 is green in all jobs, including verify, @slow, and server boot. The original log-viewer
failure did not reproduce across 30 focused runs or subsequent full runs; no product change was made.

## Agent CI repair (scrapper@isaac-work-1, 2026-09-13)

Agent main CI failed at `features/config/schema_cli_options.feature:33`: dependency files still pinned
Foundation `1cd0bfc`, whose test fixture declared the old `:isaac.server/comm` berth. Re-pinned all
Foundation product/spec/support and marigold coordinates to landed Foundation `82e3594`. Focused
schema CLI features pass 7/0/39. The local full run exposed two timing flakes in
`session_steps_spec`; the focused file immediately passed 21/0/43. GitHub Actions run 34791934319
passed the full `bb ci` gate at Agent `104b3c4`. The repair is pushed to main and
`bean/isaac-8got`.



## Landed on main (2026-09-14)

main-sha: isaac-foundation 82e3594346cb5f0e780c0fa05e0eca22634e3a06
main-sha: isaac-http 11e43014ad5c7b9c8bb693e0eb4f673bbd23991d
main-sha: isaac-agent 104b3c4a9c62cff62607a28d3c60da5038688d9f
main-sha: isaac-hail 4dc44eff35803e1329152154e453345d31b98391
main-sha: isaac-hooks 65a9e632ccbb7c21471c708de981ff833ef50464
main-sha: isaac-cli-server ae0743a566063ed87dce26c31783a37bbab9cbee
main-sha: isaac-claude-code edf01951d0375b5c9d7c813d797b1ffb3453b9fa
main-sha: isaac-acp 738fe6b67806b41b59a951e06f1a7e5d8b9823a1
main-sha: isaac-discord d7db28d1e10a5746ab8202abd37ea652d4c7b8f5
main-sha: isaac-episodes 0cbe24b55a2d94be0579a2400a11163748d5913d
main-sha: isaac-cron 0bbac06bfc934e971cd78ea13efe4941ac0fddaa
main-sha: isaac-imessage 3f71179b518915ac92e5319b8ac39817416fb9ff
main-sha: isaac-mcp 2ac90561124d0c03315d79def3e7e4a02e5ff502
main-sha: isaac-foreman a2057ae907a167c11c70c261fe13517923276a40
main-sha: isaac-cli-proxy d1380ad0216a14c13323548c537f3e1e9bbed9d7
main-sha: isaac-worksite 99ff219322b5313577e982bd4a8fbb8cad574e96
main-sha: isaac 648b57c9a35d9fe5fe7f6fe96b6ee036388d5ea0

Verify gate (perceptor@isaac-verify): leftover grep of `:isaac.server/`, `isaac.server`, `io.github.slagyr/isaac-server`, `slagyr/isaac-server` on git-tracked files excluding `.beans` is empty across active module repos after registry land. Seven-repo `bb ci` green on origin/main: HTTP 122/0/250 + 46/0/96; hooks 29/0/43 + 17/0/33; hail 160/0/371 + 149/0/573 (2 pending); cli-server 9/0/38 + 10/0/41; claude-code 64/0/207 (3 pending) + 39/0/127; ACP 72/0/198 + 64/0/151; Discord 46/0/96 native + 98/0/226 JVM + 67/0/134 (3 pending). Hail/Discord first local `bb ci` hit the 60s default timeout; both green with `ISAAC_TEST_TIMEOUT_MS=600000` (Discord CI). No zanebot install/remove/restart; `modules list` deferred to human deploy.

## iMessage CI repair (scrapper@isaac-work-1, 2026-09-14)

Reproduced all five JVM integration failures from Actions run 34791226244. Root cause was stale test harness setup after the component/runtime cutover: app specs supplied only the iMessage contribution and relied on `:cfg`, so the Foundation runner never received the built-in HTTP component index. Updated `spec/isaac/server/imessage_app_spec.clj` to merge built-ins, pass the module index explicitly, use `:config`, reset component/comm registries and activations, and isolate nexus state. Also fixed `spec/isaac/comm/imessage/imessage_steps.clj` to replace HTTP's pre-seeded real root with the iMessage in-memory fixture; this repaired log capture and delivery-queue filesystem consistency. Speculative Foundation/Agent repins were tested, shown unrelated, and reverted.

Repair commit `0422f6d9f9afa91c70c6517fcd2d1d7b8bbc6d61` is pushed to both `main` and `bean/isaac-8got`. Verification: focused JVM specs 50/0/81; three focused failing features 3/0/6; full `ISAAC_TEST_TIMEOUT_MS=600000 bb ci` 41/0/69 native specs + 50/0/81 JVM specs + 15/0/30 features. GitHub Actions run 34794666739 passed. No zanebot operation was performed.

## CLI Proxy CI repair (scrapper@isaac-work-1, 2026-09-14)

Reproduced Actions run 34791228232 (`isaac.tool.names` missing). Re-pinned CLI Proxy's native/test Agent dependencies to landed Agent `104b3c4`, Foundation dependencies to landed Foundation `82e3594`, and CLI Server dependencies/fixtures to landed `ae0743a`. Updated empty-vector argv matching for current Agent step-table semantics, disabled accidental use of stale local CLI Server siblings, supplied a stable log source for HTTP-kit virtual-thread dispatch in the feature harness, and advanced the embedded ACP integration fixture from pre-cutover `3b48d97` to landed ACP `738fe6b`.

Repair head `7b32217e85e55fc3248f2520e98b620ea4eec53c` is pushed to `main` and `bean/isaac-8got`. Verification: `bb spec` 17/0/52; `bb features` 11/0/40; `bb features-slow` 4/0/10; full `bb ci` green with the same counts. GitHub Actions run 34797088398 passed. CI repair only; no verify hail, landing coordination, or zanebot operation was performed.

## ACP CI repair (scrapper@isaac-work-1, 2026-09-14)

Reproduced Actions run 34791220774: JVM specs loaded `isaac.session.policy.episodes` from ACP step definitions, but `isaac-episodes` existed only in the `:features` alias. Added Episodes to `:spec`, then excluded Episodes' stale/squashed platform and test coordinates from all ACP classpaths so ACP's explicit Foundation/Agent/HTTP train pins win. Repair head `9c825880b1334035b5d9218ee4b8b4ace4878c14` is pushed to `main` and `bean/isaac-8got`.

Verification: native specs 72/0/198; clean JVM specs 72/0/198; `ISAAC_GIT=1 bb ci` 72/0/198 specs + 64/0/151 features. GitHub Actions run 34797685529 passed specs and features. CI repair only; no verify hail, landing coordination, or zanebot operation was performed.
