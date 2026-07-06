---
# isaac-3tvq
title: Hail retry against a busy session must not cost dead-letter budget
status: draft
type: bug
priority: normal
created_at: 2026-07-06T16:32:20Z
updated_at: 2026-07-06T16:32:20Z
---


## Gap

Observed live on zanebot (2026-07-06, hail a6dd1076 / bean isaac-4tn1): while a worker session is actively working a bean, each retry redelivery hits the busy session, gets refuse-dispatch, and increments :attempts. A hail can dead-letter at 5 attempts even though the work it requested is proceeding normally. Busy-session refusal is backpressure, not failure — it should reschedule with backoff WITHOUT consuming dead-letter budget. Attempts should count only genuine delivery failures (exceptions, dead sessions, band resolution errors).

## Notes

- Fix likely in isaac-hail delivery_worker.clj reschedule path: distinguish :refused-busy from failure errors; reschedule busy refusals without attempts++ (possibly with a separate, much higher busy-retry ceiling or none).
- Related to isaac-wq8m epic (D4: a live turn can never be stolen) but independently shippable — this is delivery-side accounting, not turn markers.
