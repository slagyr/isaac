---
# isaac-7li9
title: 'Durable turn marker: persist the charge, generalize hail/inflight to all turn sources'
status: draft
type: feature
priority: normal
created_at: 2026-07-06T15:43:54Z
updated_at: 2026-07-06T15:44:11Z
parent: isaac-wq8m
---


## Gap

The only "turn in progress" signal is the in-memory `store/in-flight*` atom (spi.clj) — it exists for concurrency control and dies with the JVM. After a crash or restart there is no durable record of WHICH sessions had active turns, nor of HOW to resume them (which hail delivery, which comm to reply on, which cron job). The transcript can reveal THAT a turn was interrupted (trailing user msg / unresolved tool calls) but not the initiating charge.

Hail already solved this for itself: `hail/inflight/{id}.edn` is a durable in-flight record (claimed-at, attempts) that isaac-0tf3 recovery keys off. Interactive and cron turns have no equivalent.

## Proposed change

Promote the inflight concept from hail-private to runtime-wide: a durable per-turn marker written when a turn is claimed, deleted when it completes.

- Marker holds the resume ROUTING, not per-request state: source type (hail/comm/cron/cli), hail delivery id or comm coordinates, prompt override + params, started-at. The transcript remains the per-step checkpoint; a resumed request is rebuilt as (charge + transcript), same as the live loop.
- Resolved values (e.g. model) are NOT restored from the marker — they re-resolve from current config at resume time (consistent with isaac-q5ee crew-model hot-reload).
- For hail turns, the delivery worker writes the marker AT CLAIM TIME as an atomic move of the delivery record out of `deliveries/` (carrying :attempts, backoff, claimed-at) — closing the claim-to-turn-start crash gap. The marker REPLACES the `hail/inflight` directory; delivered/failed terminal records and dead-letter accounting stay in the hail store.
- Deletion happens in the same `finally` that clears the in-memory in-flight marker.

## Notes

- Builds on the charge vocabulary (isaac-895 / bridge → drive).
- Consumers: graceful drain writes interrupted state here; startup resume scans these markers. Those are sibling beans under isaac-wq8m.
