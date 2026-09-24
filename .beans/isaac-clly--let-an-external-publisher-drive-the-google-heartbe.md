---
# isaac-clly
title: Let an external publisher drive the Google heartbeat (Cloud Scheduler), judged by arrival not by pairing
status: completed
type: feature
priority: high
created_at: 2026-09-24T20:36:32Z
updated_at: 2026-09-24T21:42:57Z
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

## No service account is needed for this (verified 2026-09-24)

Google's own documentation on creating a Cloud Scheduler job states, for a
Pub/Sub target:

> Cloud Scheduler will publish messages to this topic as a Google APIs service
> account.

A user-managed service account is required only for **HTTP** targets. For a
Pub/Sub target the identity is Google's own and there is nothing to create, no
key to export, and nothing for `constraints/iam.disableServiceAccountKeyCreation`
to refuse.

So this path needs **no** `isaac-pubsub` service account. The one created on
2026-09-24 is inert and can be deleted; its `roles/pubsub.publisher` binding
goes with it.

### Consequence for isaac-286x's machinery

`isaac.google.service-account` — the JSON-key reader, the RS256 assertion, the
token cache, and the `google.<org>.pubsub.credentials-file` config key — is
then unused on every host, for every purpose except `isaac google smoke
--send-live`, which is a manual probe someone runs and watches.

That is a decision to make deliberately rather than let drift: either keep it
for the smoke probe and say so in its docstring, or delete it and let
`--send-live` report that publishing is no longer Isaac's job. Dead credential
plumbing is worth removing — it is the kind of thing that gets configured
years later by someone who assumes it must be needed.

## Landed 2026-09-24 as isaac-google `b1778cb` (module 0.1.15). NOT deployed.

Suite verified by the planner: 264/0 specs, 44/0 features. isaac-foundation
untouched.

**One acceptance bullet deliberately not met**, and the planner agrees with the
call: "Isaac-published heartbeats keep today's pairing behaviour unchanged".
After deleting the publisher nothing publishes, so a pairing branch is dead
code on every host — and keeping `enabled` (default **true**) beside it would
reintroduce the inert watchdog *as the default*. There is now one kind of
heartbeat: externally driven.

The design went further than the bean asked, correctly: there is no mode flag
and no on/off switch. Naming `expected-interval-ms` is the only thing that can
say what late means, so the switch and the deadline are the same key and
"watched but unable to fire" is unrepresentable rather than merely refused.

## DEPLOY IS BLOCKED ON OPERATOR ACTION — and the order matters

`health.heartbeat.enabled` is now a **retired key and a hard config error**.
yopp currently has `google.tonotop.health.heartbeat.enabled false`, so
upgrading the module without unsetting it first makes yopp **refuse to start**.

The config edit can go neither first nor last:

- on the **old** build, unsetting `enabled` re-enables the heartbeat and
  demands the service-account credentials file the org policy will not issue
- on the **new** build, `enabled` is a retired key and a hard error

Order (full detail in `doc/rollout.md`):

1. create and prove the Cloud Scheduler job while the old build still runs
2. `isaac config set google.tonotop.health.heartbeat.expected-interval-ms <ms>`
   on the old build — unknown key, warning only
3. `isaac modules upgrade isaac.google`, then **before restarting**
   `isaac config unset google.tonotop.health.heartbeat.enabled` and
   `…pubsub.credentials-file`, and `isaac config validate` clean
4. one restart
5. delete the inert `isaac-pubsub` service account and any
   `~/.isaac/google/pubsub-sa.json`
6. watch for `:google/heartbeat-received` and no `:google/heartbeat-missed`

## Open

Cloud Scheduler same-project IAM is documented from Google's stated behaviour
(the service agent receives `roles/cloudscheduler.serviceAgent` when the API is
enabled), not verified against a live project. An explicit
`roles/pubsub.publisher` grant to
`service-<project-number>@gcp-sa-cloudscheduler.iam.gserviceaccount.com` is
given as the fallback, so the runbook is safe either way — but the first real
run on yopp is the confirmation.

## The deploy trap is gone — isaac-google `5cdf807`

The operator pushed back on `health.heartbeat.enabled` being a hard config
error, and was right. `[[:retired? …]]` is the established idiom in
`schema_base` for `:server :port` and friends — but those are **structural
moves**: the setting still exists elsewhere, so ignoring one would silently
change behaviour and refusing is correct. This key is not that. The interval is
the switch now, so a leftover `enabled` changes nothing, and refusing over it
would have taken yopp down for config that no longer means anything.

Unknown and leftover fields are warnings in this codebase. The config-check
berth already carries `:warnings` beside `:errors`, so the helpful message and
a host that starts were never actually in tension.

Now:

| situation | result |
|---|---|
| interval named and usable | watched |
| interval named but unusable (0, garbage) | **error** — a watch was asked for and cannot work |
| retired `enabled` present | **warning** naming its replacement |
| heartbeat block, no interval | **warning** — nothing is watched |

The property that mattered survives: you cannot end up with a watch that
silently cannot fire. What is gone is stopping a host that merely receives.

**Revised deploy order — the config edit no longer has to be threaded:**

1. **operator:** create and prove the Cloud Scheduler job
2. upgrade `isaac.google`, restart (yopp starts fine with its leftover
   `heartbeat.enabled false`, warning only)
3. `isaac config set google.tonotop.health.heartbeat.expected-interval-ms <ms>`
   and `isaac config unset google.tonotop.health.heartbeat.enabled`
4. watch for `:google/heartbeat-received` and no `:google/heartbeat-missed`

Steps 2 and 3 can be done in either order and neither can break the host.
