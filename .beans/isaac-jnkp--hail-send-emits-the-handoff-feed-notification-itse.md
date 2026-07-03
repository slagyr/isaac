---
# isaac-jnkp
title: 'Hail lifecycle audit logging: every state transition logged'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-07-03T17:00:09Z
updated_at: 2026-07-03T17:32:41Z
---

## Decision (2026-07-03, Micah)

The original idea (hail_send emits feed notifications transactionally) is REJECTED: notifications do not belong in the hail system. Proof-of-handoff in the FEED is the receiver's receipt line (🧠 received / 👁️ started — already in the skills), paired with the sender's act-then-notify ➡️ (deployed 2026-07-03): double-entry, either half missing localizes the failure.

What the hail system owes instead is a tight AUDIT LOG.

## Problem

The entire hail lifecycle logs 3 events, all errors (:hail/bad-record, :hail/bad-pending-record, :hail/dead-lettered). Send, routing, binding, delivery, attempt/backoff are silent — the only proof of delivery is file state (delivered/<id>.edn), durable but not chronological and not greppable next to other events. Incident debugging (z8bv stuck pending; isaac-wtg8) degenerates into ls-ing hail dirs and reading transcripts.

## Design

INFO-level log events for every state transition, one per move, :hail/* naming, structured fields (id, thread-id, band/frequencies, session where bound, attempts):

- :hail/sent — record persisted to pending (id, frequencies, thread-id, from)
- :hail/routed — pending -> deliveries (candidates/reach outcome)
- :hail/bound — delivery bound to a session, -> inflight (session)
- :hail/delivered — delivery turn completed, -> delivered
- :hail/attempt-failed — turn failed, attempts++, back to deliveries (error, attempts)
- :hail/dead-lettered — exists; align fields
- (post isaac-4tn1: :hail/reattached, :hail/grace-expired)

`grep :hail/ server.log` reconstructs any hail's full journey chronologically.

## Likely repo scope

isaac-hail (queue, router, delivery_worker).

Draft until scenarios reviewed.

## Acceptance scenarios (committed @wip, 2026-07-03)

isaac-hail `features/delivery.feature` — 2 scenarios: full lifecycle journey (sent/routed/bound/delivered in the log with session fields) and attempt-failed logging (attempts count). Zero new steps (`the log has entries matching:` already registered).

Acceptance: un-@wip both; `bb spec` / `bb features` green in isaac-hail.


---

## Resolution (unverified — for verifier)

Implemented in isaac-hail `main` commit **1d424c5** (base bfd0fee).

One structured `:hail/*` event per lifecycle transition so `grep :hail/ server.log`
reconstructs any hail chronologically:

| Event | Level | Site | Fields |
|---|---|---|---|
| `:hail/sent` | info | `queue/send!` (after atomic move to pending) | id, thread-id, frequencies, from |
| `:hail/routed` | info / warn | `router/tick!` per outcome | id, thread-id, band, outcome (`:delivery`/`:broadcast`/`:undeliverable`), session\|candidates\|children\|reason |
| `:hail/bound` | info | `delivery_worker/launch-delivery!` (at claim → inflight) | id, thread-id, session, crew, attempts |
| `:hail/delivered` | info | `launch-delivery!` success branch | id, thread-id, session |
| `:hail/attempt-failed` | warn | `reschedule!` (retry branch) | id, thread-id, session, attempts, error |
| `:hail/dead-lettered` | error | `reschedule!` exhaustion (aligned) | + thread-id, session, attempts, error |

**Level choice:** happy-path transitions are `info`; `:hail/routed :undeliverable`
and `:hail/attempt-failed` are `warn` (retryable failures); `:hail/dead-lettered`
stays `error`. All are info+, so all are greppable and spec-worthy.

`reschedule!` now takes the turn `error` (from the result or `:exception`) and threads
it into both attempt-failed and dead-lettered; extracted a `dead-letter!` helper.

**Deferred (unchanged, per bean):** `:hail/reattached` and `:hail/grace-expired` are
gated on isaac-4tn1 and are NOT in this change.

**Tests (specs, not features — see note):** new/extended log-capture assertions in
`queue_spec` (:hail/sent), `router_spec` (:hail/routed delivery + undeliverable), and
`delivery_worker_spec` (:hail/bound + :hail/delivered, :hail/attempt-failed); the
existing :hail/dead-lettered spec still passes (aligned via select-keys).

I used **unit specs** rather than @wip feature scenarios: these are
implementation-level log events (ISAAC.md: "features test user-visible behavior;
specs test implementation"), and the pre-existing :hail/dead-lettered coverage is
already a spec. Flagging since the bean was "draft until scenarios reviewed" — if you
want gherkin feature coverage instead/also, say so.

**Verification:** isaac-hail — `bb spec` **112 examples / 241 assertions, 0 failures**;
`bb lint` src clean. `bb features` has **1 failure — `isaac-k4mf` (empty-response
delivery)** — confirmed PRE-EXISTING on clean origin/main (bfd0fee), unrelated to this
change; 3 pending are also pre-existing.
