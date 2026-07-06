---
# isaac-3tvq
title: Hail retry against a busy session must not cost dead-letter budget
status: draft
type: bug
priority: normal
created_at: 2026-07-06T16:32:20Z
updated_at: 2026-07-06T17:46:53Z
---


## Gap

Observed live on zanebot (2026-07-06, hail a6dd1076 / bean isaac-4tn1): while a worker session is actively working a bean, each retry redelivery hits the busy session, gets refuse-dispatch, and increments :attempts. A hail can dead-letter at 5 attempts even though the work it requested is proceeding normally. Busy-session refusal is backpressure, not failure — it should reschedule with backoff WITHOUT consuming dead-letter budget. Attempts should count only genuine delivery failures (exceptions, dead sessions, band resolution errors).

## Notes

- Fix likely in isaac-hail delivery_worker.clj reschedule path: distinguish :refused-busy from failure errors; reschedule busy refusals without attempts++ (possibly with a separate, much higher busy-retry ceiling or none).
- Related to isaac-wq8m epic (D4: a live turn can never be stolen) but independently shippable — this is delivery-side accounting, not turn markers.

## Scope extension (2026-07-06, Micah-approved): failure-class-aware retry accounting

Observed live: codex usage_limit_reached failures burned 5 attempts in ~5 minutes per hail across every band (work, verify, plan, ci-failure) — the wall lasts hours, the retries were pure waste, and each retry re-drove a turn re-sending a ~120K head against the same wall.

Classify delivery failures and price retries accordingly:
- **:refused-busy** (session in-flight) — backpressure, not failure: reschedule with backoff, NO attempts increment (original scope).
- **:provider-limit** (usage limits, 429s, quota/credit exhaustion — e.g. "usage_limit_reached", "credit balance is too low") — the world is broken, not the hail: park with LONG backoff (tens of minutes, configurable), NO attempts increment.
- **genuine failure** (exceptions, dead sessions, band resolution errors) — today's semantics: attempts++, 5-attempt dead-letter.

Also carry the failure class + message into the dead-letter record — the failed/*.edn files from this incident had no :error detail at all; diagnosis required log archaeology (the isaac-cehc fix put it in logs; put it on the record too).
