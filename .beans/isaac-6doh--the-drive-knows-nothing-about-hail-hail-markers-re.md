---
# isaac-6doh
title: 'The drive knows nothing about hail: hail markers resume like any other source'
status: in-progress
type: task
priority: high
created_at: 2026-09-21T17:07:03Z
updated_at: 2026-09-21T17:16:22Z
blocking:
    - isaac-9azm
---

**Ruling (Micah, 2026-09-21, isaac-9azm): the driver should know nothing
about hail.** Today `isaac-agent` carries hail-shaped code on the resume and
marker paths because hail used to own the turn's outcome. Once hail's
responsibility ends at turn start, a hail marker is a work order like cron's:
never stale, resumed in its own session through the turn queue, exactly like
`#{:comm :cron :cli}` (isaac-yxch).

## What goes

- `src/isaac/bridge/resume.clj`: `marker->delivery`, `requeue-hail!`,
  `write-delivery!` / `deliveries-path`, `archive-cancelled-hail!` /
  `cancelled-dir`, `crash-orphan?`, `resume-attempts`, and the `(= :hail
  source)` branches in `resume-marker!`. `:hail` takes the
  `enqueue-resume-turn!` path with the other sources. A cancelled marker of
  any source is simply cleared.
- `src/isaac/bridge/core.clj` `turn-marker`: no `:delivery-id`, no
  `:attempts`, no embedded `:delivery` payload; the marker is source +
  started-at (plus whatever suspend/weather stamps later). `marker-source`
  needs no `:hail` case — the origin kind is just carried.
- Anything else `grep -rn hail src/` turns up in isaac-agent (the
  `:hail-delivery` charge key is set by hail, not read here; confirm and
  drop any reader).

## Interim behaviour (between this landing and isaac-9azm)

Hail still embeds the delivery on the charge and still waits on the turn's
future; the bridge simply stops copying the payload into the marker. A
restart with an in-flight hail turn resumes it in its own session (right)
and hail's stale-delivery guard finds no marker references (inert, harmless).
Hail's own `turn-resume.feature` / `turn-marker-claim.feature` are already
`@wip` on hail main for isaac-9azm, so this bean must **not** repin
isaac-hail — isaac-9azm bumps the pin and recuts hail's specs.

Known trade-off (recorded, not solved here): the hard-crash "attempts+1"
crash-loop breaker for hail markers goes with `crash-orphan?`. Comm, cron
and CLI markers never had one; hail joins the same policy.

## Scenarios

`isaac-agent/features/session/resume_repair.feature`: "a legacy hail marker
is requeued and removed from its original path" is recut (`@wip`) to "a hail
marker resumes in its own session through the turn queue".

## Acceptance

```
cd isaac-agent
bb features features/session/resume_repair.feature
bb features features/session/resume_queue.feature
bb features features/session/turn_markers.feature
bb ci
grep -rn "hail" src/    # one-time check: no hail-shaped code remains
```

- A `:hail` marker (suspended or crash orphan, any age) is handed to the
  turn queue and completes in its own session; no file is written under
  `hail/`.
- `grep -rn hail isaac-agent/src/` is clean (one-time acceptance, not a
  permanent scenario).
- Do **not** repin isaac-hail in this bean.

feature-baseline: isaac-agent cabfdf29e81b87307a158b0fccd8056d0c03135d
feature-blob: isaac-agent features/session/resume_repair.feature 19b913c0fd8d157a2f251c81adcc2f0341b9f3d6 50



Dispatched: hail 209439d6 2026-09-21T17:15:30Z (band isaac-work)
