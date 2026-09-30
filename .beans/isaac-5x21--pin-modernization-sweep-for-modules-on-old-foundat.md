---
# isaac-5x21
title: Pin modernization sweep for modules on old foundation/agent/http
status: scrapped
type: task
priority: normal
created_at: 2026-09-30T06:37:18Z
updated_at: 2026-09-30T14:12:35Z
---

Found overnight 2026-09-30 while writing handbook chapters. Several modules pin old isaac-foundation/isaac-agent/isaac-http shas, so their own tests warn `:manifest/unknown-key :handbook` and can't use newer foundation helpers. Bumping them hits:

- agent isaac-e9jl made `worker/tick!` async: feature steps must call `worker/await-idle!` after ticking (isaac-hail fixed its steps this way). gchat's "three quick messages … one consolidated reply" (inbound.feature:702) fails at new agent: likely the same step race.
- http isaac-q1iu adjudicates any presented bearer even without auth configured: scenarios must configure the token they send.
- foundation test-support (40a185f, slow-lane isolation) shells out to a per-repo `bb -Sforce gherclj` task that no downstream repo defines, so `bb features-slow` breaks after a foundation bump (seen in isaac-cli-proxy).
- isaac-discord: client-lifecycle scenarios stop seeing `:discord.client/started` past apron 3.2.1.

Repos still on old pins: isaac-cron, isaac-google, isaac-gchat, isaac-gmail, isaac-discord, isaac-imessage, isaac-acp, isaac-cli-proxy, isaac-worksite, isaac-foreman.

## Wanted

Bump each to current foundation/agent/http mains together, fixing the step-level fallout above, and resolve the foundation test-support `gherclj` task dependency (foundation provides it, or the lane doesn't require a per-repo task). One bean per repo or a small sweep, planner's call.

## Scrapped (2026-09-30)

Absorbed into milestone isaac-vyqs: each module bumps its foundation/agent pins in its own namespace-restructure bean (inside-out).
