---
# isaac-jnkp
title: hail_send emits the handoff feed notification itself — models cannot claim unsent handoffs
status: draft
type: feature
created_at: 2026-07-03T17:00:09Z
updated_at: 2026-07-03T17:00:09Z
---

## Context (isaac-wtg8 incident, 2026-07-03)

A worker announced "➡️ handed off to planner" on Discord, then its turn died (isaac-k4mf) before hail_send ran: the feed claimed a handoff that never existed. The act-then-notify skill fix (orchestration repo, deployed 2026-07-03) shrinks this window; this bean eliminates the class.

## Idea

The hail system emits the handoff feed line ITSELF, transactionally with the persisted send:

- When hail_send persists a band hail whose resolved band data carries notification-comm, the hail layer sends the at-a-glance line (e.g. `<bean-id> ➡️ **<from-crew>** handed off to <band>`) after the record is durably written.
- Models stop sending ➡️ handoff lines entirely (skills drop them); a feed handoff line becomes PROOF a hail record exists.
- Non-handoff milestones (claim, observations, verify start/pass/fail) remain model-sent — they describe work, not hail transactions.

## Design questions to settle before promotion

- Where: hail_send tool only, or also CLI/HTTP sends? (Lean: everywhere a band hail with notification-comm data persists — one code path in the hail layer.)
- Format source: template in band data vs convention baked into the hail layer? (Lean: convention + data-supplied bean-id/crew; keep bands thin.)
- From-crew: ambient session identity of the sender (isaac-s0ho) for tool sends; :from for CLI sends.
- Suppression: params flag to silence (e.g. dispatcher smoke tests)?
- Loop safety: notification failures must not fail the send (log + continue).

## Likely repo scope

isaac-hail (send path), orchestration skills (drop model-sent ➡️ lines) after.

Draft until the design questions are settled and scenarios reviewed.
