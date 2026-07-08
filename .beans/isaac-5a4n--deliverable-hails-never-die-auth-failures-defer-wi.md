---
# isaac-5a4n
title: 'Deliverable hails never die: auth failures defer with attention; dead-letter is for poison only'
status: completed
type: feature
priority: high
created_at: 2026-07-08T22:12:23Z
updated_at: 2026-07-08T23:15:26Z
---

## Goal

A hail whose target and content are healthy must never dead-letter because infrastructure is broken. Dead-letter budget is for poison (the hail breaks its target); everything solvable parks, makes noise, and delivers itself once the problem is fixed. Doctrine from Micah 2026-07-08: 'I don't want hails to die. Yes, they require attention, but once the problem is solved, they should be delivered.'

## Observed (2026-07-08 21:45Z, zanebot)

xAI subscription token expired 21:39Z (refresh cron not yet armed). Prowl's isaac-o14c handback hail 00210196 burned 5 attempts as :api-error in 3 minutes and dead-lettered at 21:48. Token was refreshed at 21:56 — the hail would have delivered untouched had it deferred instead. Recovery required manual re-hail.

## Design (extends isaac-3tvq's two-outcome contract)

- **Drive classifies auth failures as weather**: HTTP 401 (and 403 auth/entitlement rejections) => {:unavailable? true :reason :auth :retry-after-ms N} (default shorter than walls, ~5min — retries are cheap, auth fixes are human-speed). 3tvq wall classification gains :reason :wall.
- **Hail worker unchanged in shape**: unavailable => defer, zero attempt burn, delivery stays pending. :hail/delivery-deferred warn log gains the :reason.
- **Attention**: an :auth-reason deferral posts to the NEW top-level `:attention` config (Micah-approved 2026-07-08):
  `:attention {:notify {:comm "discord" :target "isaac"} :break-glass {:comm "imessage" :target "micah"}}`
  (hot-reloadable; `:break-glass` is declared-but-unused — nothing sends to it in this bean). An auth outage is a SYSTEM-scoped event, so it routes to `:attention :notify`; unset => WARN log only. Posting = enqueueing a durable file in `comm/delivery/pending/` (the existing comm outbox — the comm delivery worker drains it), so attention survives restarts. Throttled once per provider per hour. Wall deferrals stay log-only (expected, self-healing).
- **Still dead-letters**: genuine turn errors (non-wall model errors, tool blowups) and :continuations-exhausted — poison budget unchanged.

## Scenarios (approved by Micah, 2026-07-08)

**S1 — drive classification** (isaac-agent `features/llm/provider_walls.feature`):
a queued grover `http-error` with status 401 / message "Unauthorized" classifies
as `the turn result is unavailable with retry-after-ms 300000 and reason auth`
(NEW step variant), logging `:warn :chat/provider-auth-rejected {provider, status 401}`
(distinct event — "walled" means quota weather, auth is a different disease).
New config key `:defaults :provider-auth-retry-ms` default 300000 (auth retries
are cheap; recovery is human-speed, check every 5 min). Amendment: existing
429/usage-limit scenarios gain a `reason wall` assertion row — `:reason` joins
the classified result for all weather.

**S2 — defer then self-deliver** (isaac-hail `features/delivery.feature`):
scripted `unavailable` rows gain a `reason` column (absent = `:wall`, existing
scenarios green unmodified). Queue `unavailable/300000/auth` then a `text`
success. Tick 1 at 10:00:00Z → deliveries file shows attempts 0 (unchanged),
next-attempt-at 10:05:00Z (tick + auth retry-after, no backoff), log
`:warn :hail/delivery-deferred {:reason :auth :retry-after-ms 300000}`.
Tick 2 at 10:05:30Z + turn end → delivered/hail-1.edn exists, deliveries and
failed files do not. Two tick→turn-end cycles in one scenario (sequential
steps; worker verifies the second tick re-binds).

**S3 — attention, throttled per outage** (isaac-hail):
fixture sets config `attention.notify` = `{:comm "discord" :target "boiler-room"}`
(no band data block needed — system-scoped event). Three auth deferrals at
10:00 / 10:05:30 / 11:06:00. After the first two:
```
Then the directory "comm/delivery/pending" has exactly 1 file
And the only file in "comm/delivery/pending" EDN contains:
  | path    | value                        |
  | comm    | discord                      |
  | target  | boiler-room                  |
  | content | contains "auth" and "grover" |
```
After the third (window expired): exactly 2 files. Throttle: once per provider
per hour, in-memory (restart re-posts — acceptable noise). NEW steps (generic):
`the directory "…" has exactly N file(s)` and `the only file in "…" EDN
contains:` (dodges random outbox filenames). NO comm fake/registration — the
durable outbox IS the assertion surface.

**S4 — walls stay silent** (isaac-hail): one `unavailable/60000/wall` deferral
with `attention.notify` configured => attempts 0, `the directory
"comm/delivery/pending" has exactly 0 files`. Only auth outages page a human.
