---
# isaac-axzg
title: 'Undeliverable hails park silently: notify and log loudly when a hail has no recipients'
status: todo
type: bug
priority: high
created_at: 2026-07-07T15:42:13Z
updated_at: 2026-07-07T15:42:13Z
---


## Gap

When the router resolves a hail to zero recipients (bad band name, impossible session-tag/filter combination, no matching sessions), the hail is parked in `hail/undeliverable/` with NO notification, NO warn/error log event surfaced to operators, and NO signal to the sender — the sending crew believes the handoff succeeded.

Observed live (2026-07-07): isaac-exi2's verify handoff (eb08500f) targeted the isaac-verify band with an invented `:session-tags #{:project/isaac-acp}` → zero recipients → parked silently. The bean sat in-progress for 11 hours, one verification away from completing its chain, with every queue empty and no signal anywhere. Triage then found **14 hails** in undeliverable/, including 10 perceptor verify-fails for isaac-iqqz parked since Jul 3 — that bean has been mutely stuck for four days.

This is the third silent terminal state found this campaign (limbo turns → isaac-5ru9; eaten retries → isaac-3tyl; now this). Same design lesson each time: **a hail that stops moving must make noise.**

## Fix

When a hail is parked as undeliverable:
1. Log `:hail/undeliverable` at WARN with the hail id, band, and the frequencies that matched nothing.
2. Send an at-a-glance notification to the configured notification-comm: `<hail-id> ⚠️ undeliverable — no recipients for <band/frequencies> (bean <bean-id> if present)`. The router/worker has config access; if runtime-side comm sending is architecturally off-limits, deliver the notice via a fallback band or surface it in `isaac hail list` + heartbeat tooling — but the default should be an active notification, not a passive file.
3. Consider (spec-time decision): a `--dry-run`/recipient-count check in hail-send so a sender can detect zero-recipient addressing at send time and correct itself.

## Mitigation deployed meanwhile (2026-07-07)

All three hail-bean skills now rule: band handoffs carry `band` + `params` ONLY — no session-tags/crew/filters.
