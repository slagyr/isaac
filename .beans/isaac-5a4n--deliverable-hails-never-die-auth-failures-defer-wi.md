---
# isaac-5a4n
title: 'Deliverable hails never die: auth failures defer with attention; dead-letter is for poison only'
status: draft
type: feature
priority: high
created_at: 2026-07-08T22:12:23Z
updated_at: 2026-07-08T22:12:23Z
---

## Goal

A hail whose target and content are healthy must never dead-letter because infrastructure is broken. Dead-letter budget is for poison (the hail breaks its target); everything solvable parks, makes noise, and delivers itself once the problem is fixed. Doctrine from Micah 2026-07-08: 'I don't want hails to die. Yes, they require attention, but once the problem is solved, they should be delivered.'

## Observed (2026-07-08 21:45Z, zanebot)

xAI subscription token expired 21:39Z (refresh cron not yet armed). Prowl's isaac-o14c handback hail 00210196 burned 5 attempts as :api-error in 3 minutes and dead-lettered at 21:48. Token was refreshed at 21:56 — the hail would have delivered untouched had it deferred instead. Recovery required manual re-hail.

## Design (extends isaac-3tvq's two-outcome contract)

- **Drive classifies auth failures as weather**: HTTP 401 (and 403 auth/entitlement rejections) => {:unavailable? true :reason :auth :retry-after-ms N} (default shorter than walls, ~5min — retries are cheap, auth fixes are human-speed). 3tvq wall classification gains :reason :wall.
- **Hail worker unchanged in shape**: unavailable => defer, zero attempt burn, delivery stays pending. :hail/delivery-deferred warn log gains the :reason.
- **Attention**: an :auth-reason deferral posts to notification-comm — throttled once per provider per hour (healthy->failing transition), so a dead token pings a human instead of silently deferring forever. Wall deferrals stay log-only (expected, self-healing).
- **Still dead-letters**: genuine turn errors (non-wall model errors, tool blowups) and :continuations-exhausted — poison budget unchanged.

## Scenarios (spec with Micah before dispatch)

Coverage to spec: 401 defers with :reason :auth and no attempt increment; recovered provider delivers the deferred hail without intervention; comm post fires once per outage (throttle proven); 429 wall still defers log-only without comm post; genuine error still burns attempts.
