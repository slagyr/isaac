---
# isaac-an14
title: 'Google health as a heartbeat: silence per tenant, log once on transition, hourly synthetic push with a deadline'
status: in-progress
type: feature
priority: high
tags:
    - google
    - ops
created_at: 2026-09-22T23:26:20Z
updated_at: 2026-09-22T23:31:47Z
---

## Decision (Micah, 2026-09-22)

Per-space silence at 30 s cadence is the wrong signal ("warnings every 30
seconds per space sound terrible"): a quiet space is normal, and the log line
repeats every tick instead of firing once. What the design doc actually needs
is a heartbeat for the push pipeline — an expired subscription, a broken
Pub/Sub binding or a dead Funnel all look like nobody talking.

## Change (isaac-google)

- Silence is evaluated **per tenant**, not per registration key: "no event
  from Google for this tenant in `silent-after-hours`". Keys go away as a
  health unit once `spaces/-` lands (sibling bean).
- Every health condition logs **once on transition** (silent → not silent, and
  back), never per tick. `should-notify?` already exists for posts; the log
  line follows the same rule.
- **Synthetic heartbeat**: on the hourly tick, publish one real Pub/Sub
  message to the tenant's topic (the `--send-live` path isaac-mu1i built) and
  expect it at the door within a deadline (default 60 s). A missed heartbeat is
  the condition `:heartbeat-missed`, logged and posted once on transition,
  cleared when the next one arrives. This proves door + verification + inbox
  regardless of chat traffic.
- Config: `google.<org>.health {:silent-after-hours n, :heartbeat {:enabled
  true :deadline-ms 60000}}`; the old per-key silence keys are removed (no
  back-compat).

## Scenarios (isaac-google health + registration features)

- tenant with two keys, one quiet for days, one active an hour ago → no
  silent condition.
- tenant with no event for longer than the threshold → one `:silent`
  condition, logged once; the next tick logs nothing; an event clears it and
  logs the clear once.
- heartbeat published, arrives inside the deadline → no condition; not
  arrived by the deadline → `:heartbeat-missed` once; arrives later → cleared.

## Acceptance

- Suites green in isaac-google; `bb ci`.
- One-time on yopp after deploy: server.log shows at most one silence line per
  transition per tenant, and one heartbeat line per hour at debug.

## Related

spaces/- bean (sibling), isaac-mu1i (`silent` check reads the new
condition), email-and-chat.md "The failure mode is silence".
