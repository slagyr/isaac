---
# isaac-fu2m
title: 'isaac-google health + attention: silence and expiry checks, google status command'
status: draft
type: feature
priority: normal
tags:
    - google
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T04:12:15Z
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

## Scenarios to draft

1. No event for longer than the threshold → one attention notification, not one per tick.
2. An expiry in the past → renew attempted immediately and `:google/expired` logged.
3. Status command shows the table.
