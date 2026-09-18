---
# isaac-2a2x
title: 'Audit + alerts: :principal on request logs and hail records; last-used; Discord alerts for 401 bursts, first use, expired/revoked use, expiring soon'
status: draft
type: feature
priority: normal
tags:
    - security
created_at: 2026-09-18T04:13:56Z
updated_at: 2026-09-18T04:13:56Z
parent: isaac-gym1
blocked_by:
    - isaac-bzgw
---

Child 4 of isaac-gym1 (principals epic). Blocked by child 1. Repos: isaac-http (audit, alerts), isaac-hail (principal on records).

## Work
- Every request log line (`:http/request` or equivalent) carries `:principal name`; 401/403 lines carry `:principal nil|name` and `:reason :unknown|:expired|:scope`.
- Hail records and deliveries carry `:principal` (from `:isaac/principal` on the request); `isaac hail show` prints it.
- Last-used: `~/.isaac/state/auth/last-used.edn` (or the existing state dir convention), updated at most once per principal per minute (not per request) — for `auth list`.
- Alerts through the existing notification comm (Discord `isaac` channel, same path the crews use): (a) ≥N 401s from one address in a minute (reuse burst thresholds), (b) first ever use of a principal, (c) any use of an expired or revoked token, (d) a principal expiring within 7 days (daily). Hot-reloadable thresholds under `:server :auth :alerts`.

## Scenarios (draft)
1. an authenticated request logs its principal; a refused one logs the reason
2. a hail sent by principal ci is recorded with :principal ci
3. first use of a principal raises one notification; the second use does not
4. use of an expired token raises a notification
5. last-used is written for a principal and shown by `auth list`
