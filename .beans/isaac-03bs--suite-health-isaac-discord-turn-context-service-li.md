---
# isaac-03bs
title: 'Suite health (isaac-discord): turn_context + service_lifecycle reds on scuttlebutt train'
status: draft
type: bug
priority: high
tags:
    - suite-health
created_at: 2026-09-04T03:21:09Z
updated_at: 2026-09-04T03:21:09Z
---

Ambient/full-gate failures on `isaac-discord` after the scuttlebutt train pin bump to agent `bf4323326c150bdcda4be2c0245cf2f7b0cbd629`, observed while adjudicating **isaac-o0bk**. These failures are outside the phase-1 mechanical Comm migration itself and must not block beans whose own scuttlebutt surface is green.

## Observed (2026-09-04, scrapper@isaac-work-2, isaac-discord `bean/isaac-o0bk` @ `0f9dfeb`)

Focused migration spec passes:
- `bb spec spec/isaac/comm/discord_spec.clj` → `42 examples, 0 failures`

Ambient failures on the pinned train:

1. `features/comm/discord/turn_context.feature:101`
   - scenario expects the trusted JSON prompt block to contain `":true"` for `was_mentioned`
   - current trusted JSON renders booleans as `true` / `false` (no leading colon)
   - this is a turn-context rendering contract issue, not a scuttlebutt protocol migration issue

2. `features/comm/discord/service_lifecycle.feature:37`
   - step `And the Discord client is connected` fails on the pinned train
   - this is a service/gateway lifecycle issue, not a scuttlebutt protocol migration issue

Non-owning note:
- `bb features` timing out at the repo's shared 60s wrapper is already a known runner issue in `isaac-discord` (`bb features` delegates to `jvm-features` with a timeout); do not treat the wrapper alone as the product failure here. That process issue is already documented under qomx/jndk-style gate adjustments.

## What this bean owns

Make the two named feature files green on the current train without weakening their scenario intent:
- trusted JSON boolean rendering vs feature expectation (`turn_context.feature`)
- lifecycle readiness / connected-state contract (`service_lifecycle.feature`)

If either failure belongs to an existing owner, record the handoff here with the bean id; otherwise fix it here.

## Acceptance

    cd isaac-discord
    bb jvm-features features/comm/discord/turn_context.feature
    bb jvm-features features/comm/discord/service_lifecycle.feature

0 failures on each, on current main / current train pin, with no new `@wip` tags. Record the train SHAs used.
