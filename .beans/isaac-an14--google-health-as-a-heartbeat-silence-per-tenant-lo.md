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

## Handoff (worker, 2026-09-22)

Branch `bean/isaac-an14` in isaac-google, one commit `3a775d2`, pushed. Bean
left `in-progress`, no tags.

### Design

- **Tick cadence.** `component/default-tick-ms` 30000 → **3600000**. The inbox
  worker keeps `default-inbox-ms` 2000 — it drains real pushes and must not
  wait an hour. `renew-within-hours` left at 24: the shortest configured renew
  window still gets two dozen hourly ticks before it closes, so a 7-day expiry
  cannot be missed.
- **Silence per organization.** `health/evaluate` now takes
  `:tenants [{:tenant id :keys [key …]}]` instead of a flat `:keys`. Silence is
  one condition per tenant — `{:kind :silent :tenant id :silent-hours n}` —
  computed from the **most recent** `:last-event-at` across every key that
  organization owns, against that organization's own `:silent-after-hours`
  (default 6). Per-key silence is gone; no back-compat. Expiry stays per key
  (`{:kind :expired :key k}`), and an unreached door still suppresses
  everything.
- **Log once on transition.** `log-conditions!` takes the pre-tick state and
  logs only conditions `should-notify?` calls new, then one
  `:google/health-cleared {:kind :subject :since}` per notified condition that
  has gone. A condition that is merely still present logs nothing. Attention
  posts were already once-per-condition (`apply!`); the log line now follows
  the same `notified-at` state.
- **Synthetic heartbeat.** New `isaac.google.heartbeat`: after health,
  `tick!` calls `send-all!`, which publishes one real Pub/Sub message per
  enabled organization to its own topic, marked twice — attribute
  `ce-type: isaac.google/heartbeat` and payload `{:isaac-heartbeat true}` — and
  records `:heartbeat-sent-at`. A failed publish logs
  `:google/heartbeat-failed` and records nothing (nothing in flight to miss).
  The door (`http/handler`) recognises the marker before `inbox/accept!`:
  records `:door-last-hit` + `:last-heartbeat-at`, logs
  `:google/heartbeat-received` at debug, answers 204. It is **never**
  persisted, handed to a handler, turned into a turn, or recorded as a
  `last-event` — a heartbeat that counted as an event would quiet the very
  silence watch it complements. `evaluate` raises
  `{:kind :heartbeat-missed :tenant id :deadline-ms n}` when the last published
  heartbeat is older than `:deadline-ms` and nothing arrived after it; the next
  arrival clears it. Judged at the following tick, so an hourly tick with a 60s
  deadline reports a miss on the next pass.
- **Shared publish.** New `isaac.google.pubsub/publish!` — one message, one
  organization, its own topic and token — factored out of
  `cli/publish-test-message!` (isaac-mu1i's `--send-live`) and reused by the
  heartbeat.
- **Smoke.** `smoke/decide-silent` counts both `:silent` and
  `:heartbeat-missed`, naming the organization (not the key); `cli/run-smoke`
  passes `:tenants` to `evaluate`, so the check reads the per-tenant condition
  and the heartbeat state straight off the live health.edn.

### Config keys

```
google.<org>.health.silent-after-hours      int   (default 6)
google.<org>.health.heartbeat.enabled       bool  (default true)
google.<org>.health.heartbeat.deadline-ms   int   (default 60000)
```

Heartbeat is **on unless an organization says otherwise**. Publishing needs
`pubsub.topics.publish` on the topic (the same grant `--send-live` needs);
without it the tick logs `:google/heartbeat-failed` hourly and raises no
condition. Schema updated in `src/isaac/google/config.clj` **and** the embedded
copy in `resources/isaac-manifest.edn` (module_spec compares them).

### Files

- new: `src/isaac/google/heartbeat.clj`, `src/isaac/google/pubsub.clj`,
  `spec/isaac/google/heartbeat_spec.clj`, `spec/isaac/google/pubsub_spec.clj`
- changed: `health.clj`, `http.clj`, `registration.clj` (tick wiring only),
  `component.clj`, `config.clj`, `smoke.clj`, `cli.clj`,
  `resources/isaac-manifest.edn`, `doc/rollout.md`,
  `features/health.feature`, `features/push_door.feature`,
  `feature-steps/isaac/google_steps.clj`, and the matching specs.

### Tests

| Command | Result |
|---|---|
| `bb lint src` | 0 errors, 1 warning (pre-existing, `worker.clj:18`) |
| `bb spec` | **207 examples, 0 failures, 332 assertions** |
| `bb features` | **32 examples, 0 failures, 132 assertions** |
| `bb ci` | green (config-bypass-lint + both suites) |

`bb lint` with no args also lints `spec/`, where ~74 pre-existing
"Unresolved symbol: describe/it/should=" findings come from speclj's
`:refer :all`; unchanged by this bean.

### Scenarios

`features/health.feature` (rewritten header; heartbeat off in Background so
the silence scenarios are not also heartbeat scenarios):

| Scenario | New? |
|---|---|
| an organization past its silence threshold is reported once, not once per tick | **rewritten — per tenant, plus "exactly 1" log assertion** |
| one quiet space does not make the organization silent | **NEW** |
| an event clears the silence and the clear is logged once | **NEW** |
| a heartbeat that never arrives is reported once and cleared when one does | **NEW** |
| an expiry in the past is renewed immediately and reported | unchanged |
| a door nobody has reached is reported as exposure, not Google | unchanged |
| isaac google status shows registrations, expiry, last event and the door | unchanged |

`features/push_door.feature`:

| Scenario | New? |
|---|---|
| the tick's own heartbeat is recorded at the door and starts nothing | **NEW** |

New steps (Marigold fixtures, no network — the publish goes through the
existing `events/request!` stub, the door through the existing signed-push
step): `When a Google heartbeat for "<org>" arrived at "<ts>"`,
`Then a Google heartbeat was published to "<topic>"`. Each "logged once"
assertion was mutation-checked (flipping the expected count to 2 fails the
scenario).

### What needs the live host to prove

- That a real published heartbeat comes back through Funnel and the OIDC door
  within 60s — the feature stubs the publish and drives the door directly.
- That the Isaac account actually holds `pubsub.topics.publish` on the topic;
  if not, `server.log` shows `:google/heartbeat-failed` hourly (and the
  heartbeat proves nothing until the grant is added).
- One-time after deploy, per the acceptance criteria: at most one silence line
  per transition per organization, and one heartbeat line per hour at debug.
- Cadence in the wild: the scheduler firing `:google/registration` hourly, and
  that an hourly tick still renews inside a 24h window.
