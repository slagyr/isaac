---
# isaac-4cq3
title: gchat generic target accepts an email (cron to, attention)
status: in-progress
type: bug
priority: high
created_at: 2026-10-06T17:59:11Z
updated_at: 2026-10-06T17:59:51Z
---

Micah, 2026-10-06, from Yopp's handbook PRs (isaac-gchat #3, isaac-cron #1, merged). A cron job's `to` (and an attention notice's `target`) reaches gchat as the delivery's generic `:target`. `send!*` sends only `gchat/to` through the email → DM lookup (`resolve-dm-space!`); a generic `:target` goes through `target/resolve-space`, which knows space ids, names and `spaces/…` only. So `to "chris@example.com"` matches nothing, logs `:gchat.send/missing-target`, returns `transient? false` and dead-letters silently. The handbook currently documents a workaround (find the DM's `spaces/…` id with `gchat__spaces` + `gchat__history`).

## Fix
In `send!*`, a generic `:target` that is an email (contains `@`, not `spaces/…`) resolves through the same DM lookup as `gchat/to`, creating the DM when absent. Precedence stays `gchat/to`, `gchat/space`, then `:target`.

## Acceptance (gated)
- The 3 @wip scenarios at the end of isaac-gchat `features/comm/gchat/outbound.feature` pass with @wip removed.
- Handbook (gchat chapter): the generic-target paragraph says an email reaches that person's DM (created when absent); the `spaces/…` workaround paragraph and the "cron job addressed to an email never arrives" troubleshooting entry are removed.
- `bb ci` green.

Likely repo scope: isaac-gchat. Host: yopp (deploy when Micah asks).

feature-baseline: isaac-gchat dff11603900bd0875e1fa1e3363cd2663abe129e
feature-blob: isaac-gchat features/comm/gchat/outbound.feature a4e3d4970dfe744ae379705eb28f953b51634a09
