---
# isaac-fu2m
title: 'isaac-google health + attention: silence and expiry checks, google status command'
status: in-progress
type: feature
priority: normal
tags:
    - google
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T22:38:15Z
parent: isaac-bv1l
blocked_by:
    - isaac-vo2q
    - isaac-cr0o
---

A lapsed watch or subscription does not error; it goes quiet. Process-up is not health.

## Scope (isaac-google)

- Health check in the renewal component's tick: per registration key, (a) expiry read from Google still in the future, (b) last event age below a configured threshold (`:google/health {:silent-after-hours N}`), (c) the push door has answered a request since boot (else the exposure is broken, not Google).
- On failure: raise attention the way hail does (`isaac.hail.attention` pattern — throttled, one notification per condition until it clears), log `:google/silent` / `:google/expired` at :warn, and attempt the renew/create immediately.
- `isaac google status` CLI: registrations, expiries, last-event times, door last-hit.

## Scenarios (approved 2026-09-18, Micah) — committed `@wip` in isaac-google `37cb4dd` `features/health.feature`

| line | scenario |
|---|---|
| :23 | silence past the threshold raises attention once, not once per tick |
| :39 | an expiry in the past is renewed immediately and reported |
| :52 | a door nobody has reached is reported as exposure, not Google |
| :63 | isaac google status shows registrations, expiry, last event and the door |

Pinned: health runs inside the registration timer tick (no second component); state at `google/health.edn` (`last-event-at` per registration key, `door-last-hit`, per-condition `notified-at`); attention = a record in the comm delivery outbox addressed by `attention.notify.comm/target` (hail's `isaac.hail.attention` shape: once per condition, cleared when the condition clears, re-raised after); log events `:google/silent` (warn; `:key :silent-hours`), `:google/expired` (warn; `:key`), `:google/door-unreached` (warn), `:google/health-ok` (debug). `isaac google status` = module CLI berth; table columns key / expires / last event / door.

## Step ledger

| step | status |
|---|---|
| default Grover setup in … / config: / the clock is fixed at … / the test clock advances … / the log has entries matching: / the directory … has exactly N file / the only file in … EDN contains: / the Isaac server is started / isaac is run with … / the exit code is / the stdout contains | reuse (foundation, hail, isaac-http) |
| the google auth store has access … / the Workspace Events API has subscription … / … grants subscriptions … / the google registration timer ticks | reuse (6aw3, vo2q) |
| **the last Google event for {key} was at {ts}** | **NEW — seeds google/health.edn** |

## Acceptance

Definition of done: `@wip` removed and

```
cd isaac-google && bb features features/health.feature:23
cd isaac-google && bb features features/health.feature:39
cd isaac-google && bb features features/health.feature:52
cd isaac-google && bb features features/health.feature:63
cd isaac-google && bb ci
```

Unit specs for: the health evaluation as a pure function (state + now + config → conditions), throttle/clear semantics, status table rendering.

Dispatched: hail 0abe6348 2026-09-18T22:37:29Z (band isaac-work)
