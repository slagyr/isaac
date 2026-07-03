---
# isaac-jnkp
title: 'Hail lifecycle audit logging: every state transition logged'
status: in-progress
type: feature
priority: normal
created_at: 2026-07-03T17:00:09Z
updated_at: 2026-07-03T17:21:32Z
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
