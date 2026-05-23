---
# isaac-wte9
title: 'Hail wake integration: dispatch turns from per-session inboxes'
status: todo
type: feature
created_at: 2026-05-23T21:56:51Z
updated_at: 2026-05-23T21:56:51Z
parent: isaac-ugx7
blocked_by:
    - isaac-7v5h
---

## Motivation

Slice 4 of the Hail epic. Per-session inboxes accumulate delivery
records from the fan-out worker (isaac-7v5h). Without something to
wake the sessions, those records sit there. This bean adds the
**wake worker** — ticks on the scheduler, polls each session's
inbox, and when a session is idle and has pending deliveries,
builds a charge and dispatches a turn via `bridge/dispatch!`. The
turn's opening context includes the delivery records (system
message describing each hail + user message from the resolved
prompt). Deliveries are removed after the turn completes.

This bean closes the Hail core loop: substrate produces → bands
registry routes → fan-out distributes → **wake delivers to LLM
turns**.

## Scope

### Worker loop

A `Reconfigurable` module registering a scheduler task on startup,
ticking ~1s. Same shape as fan-out worker.

On each tick:

1. Enumerate `<state-dir>/hail/sessions/*/inbox/*.edn`
2. Group deliveries by session
3. For each session with pending deliveries:
   - Skip if session is in-flight (`a1nu/in-flight?`)
   - Skip if crew at max-concurrent (`a1nu/can-dispatch?`)
   - Otherwise: build a charge, dispatch the turn

### Charge construction

The charge to `bridge/dispatch!`:

- `:session-key` — the session id
- `:crew` — derived from session's `:crew` field
- `:initial-context` — built from the inbox deliveries (see below)

### Initial context format

The deliveries become the turn's opening messages:

- For each delivery, a **system** message describing the hail
  metadata: `Hail <hail-id> received on <addressing>: <payload>`
- A **user** message from the resolved prompt (band's `.md`
  contents for band-addressed deliveries; the hail's `:prompt` for
  direct-addressed ones)

Multiple deliveries in one inbox are batched into ONE turn —
single dispatch with all current deliveries in context, rather than
waking N times. Simpler, lower turn overhead.

### Inbox lifecycle

After successful dispatch (turn completes), the deliveries that
triggered the turn are removed from inbox. v1 default: delete.

Race: between picking deliveries and dispatch end, new deliveries
may arrive. v1 simple semantics — snapshot inbox at dispatch
start; new arrivals wait for next tick.

## Out of scope (deferred)

- **Delivery retention** (`read/` archive instead of delete).
- **Hail-driven session spawning** — if a hail targets a session
  that doesn't exist, fan-out doesn't write to its inbox; wake
  never sees it. Spawning new sessions on hail arrival is a
  separate concern.
- **Per-delivery turn (no batching)** — v1 batches; per-delivery
  isolation is follow-up.
- **Backpressure / queue limits** on inbox size.

## Acceptance

- Worker ticks on the shared scheduler.
- A non-empty inbox on an idle session triggers a turn dispatch.
- Charge carries proper session-key, crew, and initial context
  derived from deliveries.
- After turn completes, those deliveries are removed from inbox.
- In-flight sessions or capacity-exhausted crews aren't dispatched
  again until they free up.
- Empty inboxes never trigger.
- Multiple deliveries in one inbox dispatch in one turn.

## Feature scenarios

`features/hail/wake.feature`, `@wip`. Scenarios to draft later:

- Idle session with one delivery → turn dispatched with delivery
  context.
- Multiple deliveries in inbox → one turn dispatched with all in
  context.
- In-flight session → no dispatch (deferred to next tick).
- Capacity-exhausted crew → no dispatch (deferred).
- Empty inbox → no dispatch.
- After dispatch + turn completion → inbox files removed.

## Relationship to other beans

- **Parent: isaac-ugx7 (Hail epic).**
- **Blocked by isaac-7v5h (fan-out worker)** — consumes the
  delivery records fan-out writes.
- **Companion to isaac-a1nu (concurrency).** Uses `in-flight?` and
  `can-dispatch?` for skip-on-busy semantics. a1nu is verified.
- **Closes the Hail core loop.** Substrate produces, registry
  routes, fan-out distributes, this wakes turns.
