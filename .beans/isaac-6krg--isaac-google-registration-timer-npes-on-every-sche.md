---
# isaac-6krg
title: 'isaac-google: registration timer NPEs on every scheduled tick (door-up? shadowed) and stores the create Operation instead of the subscription'
status: in-progress
type: bug
priority: critical
tags:
    - google
    - unverified
created_at: 2026-09-19T21:21:25Z
updated_at: 2026-09-19T21:21:25Z
parent: isaac-bv1l
---

Found 2026-09-19 on yopp, first live tick after configuring comms: `:scheduler/handler-error :id :google/registration NullPointerException` every 30 s; `isaac google status` stays `unknown never`.

1. `tick!` destructures `{:keys [now root door-up?]}` and then calls `(door-up?)` — the nil local shadows the fn. The scheduler calls `(tick! {})`. Every spec and feature step passed `:door-up?` explicitly, so production was the first caller without it. No Chat subscription or Gmail watch was ever created on a live host.
2. Run by hand with `:door-up? true` the tick reached Google — and stored `operations/…` with `:expires-at nil` for the Chat space: Workspace Events `subscriptions.create` answers with a long-running Operation whose `:response` holds the subscription. Next tick's live listing corrected it, but the log line, `status`, and the state file were wrong.

Fix: read `(:door-up? opts)` explicitly; `create-subscription!` merges `:response` over the operation envelope when present. Specs: tick with no :door-up? does not throw and creates; a finished Operation yields the subscription's name/expireTime; plain/error results pass through. Version 0.1.5.

## Acceptance
    cd isaac-google && bb spec && bb ci     # 48 spec, 19 feature, 0 failures

## Handoff / resume
Planner fixed locally. branch: bean/google-tick-fix @ 5daea33 (base origin/main@828673d) in isaac-google — fast-forward from main. Deployed to yopp ahead of landing (pinned to the branch sha) so the rollout can continue; the pin is moved to main once verify lands it.
