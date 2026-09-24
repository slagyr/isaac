---
# isaac-clly
title: Let an external publisher drive the Google heartbeat (Cloud Scheduler), judged by arrival not by pairing
status: todo
type: feature
priority: high
created_at: 2026-09-24T20:36:32Z
updated_at: 2026-09-24T20:36:32Z
---

Repo: **isaac-google** (`src/isaac/google/health.clj`, `heartbeat.clj`).

## Why

isaac-286x took `auth/pubsub` off the human's grant, and the follow-up made the
service-account credential a demand of the *heartbeat* rather than the topic —
because the `tonotop` organization forbids service-account keys outright:

    FAILED_PRECONDITION: Key creation is not allowed on this service account
    constraints/iam.disableServiceAccountKeyCreation

That is a policy worth keeping: long-lived service-account keys are a standard
breach vector. So yopp now runs with `google.tonotop.health.heartbeat.enabled
false` and publishes nothing, and the push pipeline has no silence detector.

**Google can publish it instead, with no key in existence.** A Cloud Scheduler
job targeting the Pub/Sub topic runs as a service account *inside* GCP — the
identity is never exported. It sidesteps the org policy completely, and it is a
better test than Isaac publishing to itself: the message originates outside the
process being tested, so it exercises the path a real event takes rather than a
loop Isaac controls at both ends.

The door already accepts such a message unchanged. `heartbeat?` matches on
either the `ce-type` attribute `isaac.google/heartbeat` or `isaac-heartbeat
true` in the payload, and Cloud Scheduler can set both.

## What blocks it today

`health/heartbeat-conditions` judges the heartbeat by **pairing a send with an
arrival**:

```clojure
(let [sent    (->instant (get-in state [:heartbeat-sent-at tenant]))
      arrived (->instant (get-in state [:last-heartbeat-at tenant]))
      elapsed (millis-between sent (->instant now))]
  (when (and elapsed
             (>= elapsed (heartbeat-deadline-ms config tenant))
             (or (nil? arrived) (.isBefore arrived sent)))
    …))
```

With an external publisher there is no `:heartbeat-sent-at`, so `elapsed` is
nil and the condition **never fires**. That is worse than having no heartbeat:
arrivals would update `:last-heartbeat-at`, the config would read as enabled,
and the watchdog would be silently inert. A health check that cannot fail is
not a health check.

## Acceptance

- An organization can declare its heartbeat externally driven — Isaac publishes
  nothing and demands no service-account credential, but still watches.
- In that mode the condition is **arrival recency against an expected
  interval**, not sent/arrived pairing: no heartbeat seen for longer than the
  configured interval (plus grace) is `:heartbeat-missed`.
- The mode is impossible to configure into inertness: an externally-driven
  heartbeat with no interval configured is a config error, not a silent pass.
- Isaac-published heartbeats keep today's pairing behaviour unchanged.
- Scenarios cover: arrival inside the interval is healthy; no arrival past
  interval+grace fires `:heartbeat-missed`; and — the regression this bean
  exists for — an externally-driven organization that has never published
  still reports missed, rather than nil.
- `doc/rollout.md` gains the Cloud Scheduler setup (job, topic target, message
  body and `ce-type` attribute, schedule matched to the configured interval,
  and the IAM role the Scheduler service account needs on the topic).

## Notes

yopp is the live case: `google.tonotop.health.heartbeat.enabled false` today,
which should become the externally-driven mode once this lands.
