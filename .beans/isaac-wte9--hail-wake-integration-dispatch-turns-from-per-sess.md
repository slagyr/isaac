---
# isaac-wte9
title: 'Hail delivery worker: dispatch pending deliveries as turns'
status: completed
type: feature
priority: normal
created_at: 2026-05-23T21:56:51Z
updated_at: 2026-05-25T18:03:59Z
parent: isaac-ugx7
blocked_by:
    - isaac-7v5h
---

## Motivation

Slice 4 of the Hail epic, **step 2 of the two-step delivery pipeline**. The
router (isaac-7v5h) writes resolved delivery files to
`<state-dir>/hail/deliveries/`. The delivery worker ticks on the shared
scheduler, binds unbound (reach-one) deliveries to an idle candidate, gates
on session in-flight + crew capacity, and dispatches a turn per delivery.
This closes the Hail core loop: substrate produces → router resolves →
delivery worker wakes turns.

## Scope

### Worker loop (async dispatch)

A Reconfigurable module registering a scheduler task (`hail/deliver`) on
startup, ticking ~1s.

On each tick, for each ready delivery (backoff window elapsed) in
`hail/deliveries/`:
- **Bind if unbound**: pick the first idle candidate from `:candidates`
  (idle via `store/in-flight?`, capacity via `store/can-dispatch?`). If none
  idle, leave pending.
- **Skip** if the (bound) session is in-flight, or the crew is at capacity.
  Gating is **not** a failed attempt.
- Otherwise **claim** the session (mark in-flight) so a sibling delivery to
  the same session is gated this tick, move the delivery
  `hail/deliveries/ → hail/inflight/`, and **schedule the turn as a
  background task**. The worker does **not** wait for the turn (unlike
  `cron/service.clj`, which blocks — a queue-draining worker must not stall
  on one slow turn).

Never dispatches two turns on the same session at once. Multiple deliveries
to one session serialize across ticks.

### Turn context (origin + autonomy preamble)

The turn opens with a **system preamble** conveying (a) **origin** — this
turn came from hail `<id>`, its addressing, and payload — and (b)
**autonomy** — it runs unattended; the user may not see the reply and may be
unavailable for questions, so don't block on clarification. Followed by a
**user** message = the resolved prompt. (Candidate to generalise into shared
comm-origin framing later — Discord/iMessage do the analogous thing; built
hail-specific for now, no premature abstraction.)

### Completion (finalize on turn end, not at tick time)

Because the worker doesn't wait, finalization happens in the turn's
**completion hook**:
- success → `hail/inflight/ → hail/delivered/`
- failure → `attempts++`; if < 5, back to `hail/deliveries/` with
  `:next-attempt-at` (backoff 1s/5s/30s/2m/10m, reusing comm delivery's
  schedule); at 5, `hail/inflight/ → hail/failed/` + log `:hail/dead-lettered`
  (error).

The `inflight/` dir keeps a dispatched delivery out of the pickable queue so
it is never re-picked mid-turn.

## Out of scope (deferred)

- **Batching** multiple deliveries into one turn (rejected — re-couples
  per-delivery failure, hides partial completion, contaminates context).
- **Hail-driven session spawning** (no idle candidate → wait, don't spawn).
- Delivery retention policy beyond `delivered/` + `failed/`.

## Acceptance

- Worker registered on the shared scheduler (`hail/deliver`, ~1s).
- A bound delivery on an idle session dispatches a turn opening with the
  origin+autonomy preamble + prompt, then moves to `delivered/`.
- An unbound delivery binds the first idle candidate (skips in-flight ones).
- In-flight session or at-capacity crew → no dispatch, delivery stays,
  `attempts` unchanged.
- Never two turns on one session at once; same-session deliveries serialize
  across ticks.
- Dispatch failure → `attempts++` + backoff; 5th attempt → `failed/` +
  `:hail/dead-lettered` log.

## Feature file

`features/hail/delivery.feature` — 8 `@wip` scenarios. Run:

```
bb features features/hail/delivery.feature
```

**Definition of done:** remove `@wip` from `features/hail/delivery.feature`
and `bb features features/hail/delivery.feature` is green.

**New steps introduced:** `the hail delivery worker ticks` and
`the hail delivery worker ticks at "<time>"`.

## Exceptions

- `features/hail/delivery.feature` is allowed non-`@wip` edits from
  `990fdf6e` that remove the transcript-level system-preamble assertion from
  the first scenario and replace it with explanatory comments.
- Those edits are authorized because origin/autonomy framing moved to the
  shared comm-origin helper in `isaac-uysx`; this bean dispatches with
  `:origin {:kind :hail ...}` and verifies prompt delivery/finalization, while
  the preamble assertion now lives in the shared framing coverage.

## Relationship to other beans

- Parent: isaac-ugx7 (Hail epic).
- **Blocked by isaac-7v5h** (router) — consumes `hail/deliveries/`.
- Uses isaac-a1nu (concurrency: `in-flight?` / `can-dispatch?`). a1nu is
  complete.
- Closes the Hail core loop.

## Update: origin framing moved to isaac-uysx

The origin+autonomy system preamble is NOT built by this worker. This bean
only sets `:origin {:kind :hail :hail-id ...}` on the charge and dispatches
the prompt. The framing (turning `:origin` into the system-prompt preamble) is
rendered centrally by the shared comm-origin helper (isaac-uysx). So the
delivery/spawn scenarios assert the user prompt + assistant reply + delivery
finalization, NOT the preamble — preamble assertions live in isaac-uysx,
checked on the composed prompt (system blocks), not the transcript.


## Verification failed

HEAD: 9d76a2268ca0682e786909f0dafc87e2713b699c
Working tree: clean

Verification stopped at acceptance check 1.

I found a non-`@wip` edit in `features/hail/delivery.feature`, but the bean has no top-level `## Exceptions` section authorizing it. The original delivery spec landed in `9c41756f`; `990fdf6e` then removed the system-preamble assertion from the first scenario and replaced it with explanatory comments about `isaac-uysx`; and `9d76a226` only removed `@wip`.

I did consider the beans `## Update: origin framing moved to isaac-uysx` note, but the repo verifier rules only allow non-`@wip` feature edits when they are documented under a top-level `## Exceptions` heading. Because this bean has no such section, I did not continue to the test gate. If the framing assertion move is intentional, add a `## Exceptions` section authorizing the non-`@wip` edits to `features/hail/delivery.feature` and re-hand off for verify.
