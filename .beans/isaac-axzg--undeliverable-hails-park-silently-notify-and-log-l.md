---
# isaac-axzg
title: 'Undeliverable hails park silently: notify and log loudly when a hail has no recipients'
status: in-progress
type: bug
priority: high
created_at: 2026-07-07T15:42:13Z
updated_at: 2026-07-07T16:23:45Z
---


## Gap

When the router resolves a hail to zero recipients (bad band name, impossible session-tag/filter combination, no matching sessions), the hail is parked in `hail/undeliverable/` with NO notification, NO warn/error log event surfaced to operators, and NO signal to the sender — the sending crew believes the handoff succeeded.

Observed live (2026-07-07): isaac-exi2's verify handoff (eb08500f) targeted the isaac-verify band with an invented `:session-tags #{:project/isaac-acp}` → zero recipients → parked silently. The bean sat in-progress for 11 hours, one verification away from completing its chain, with every queue empty and no signal anywhere. Triage then found **14 hails** in undeliverable/, including 10 perceptor verify-fails for isaac-iqqz parked since Jul 3 — that bean has been mutely stuck for four days.

This is the third silent terminal state found this campaign (limbo turns → isaac-5ru9; eaten retries → isaac-3tyl; now this). Same design lesson each time: **a hail that stops moving must make noise.**

## Fix (descoped 2026-07-07, Micah)

Log `:hail/undeliverable` at WARN with the hail id, band, and reason when the router parks a hail. **No comm post** — runtime events triggering comm notifications is a general observability design (which events, which comms, throttling, configuration) that is deliberately NOT being opened here. Parked as a possible future epic if silent-state pain recurs despite the warning.

## Mitigation deployed meanwhile (2026-07-07)

All three hail-bean skills now rule: band handoffs carry `band` + `params` ONLY — no session-tags/crew/filters.

## Scenario (approved 2026-07-07)

Committed @wip: isaac-hail `features/router.feature` line 395 — same setup shape as the existing :no-recipients parking scenarios, plus the WARN log assertion. All steps reuse.

## Acceptance

- [ ] `bb features features/router.feature:395` green (isaac-hail)
- [ ] Existing router.feature undeliverable scenarios remain green
- [ ] @wip removed
