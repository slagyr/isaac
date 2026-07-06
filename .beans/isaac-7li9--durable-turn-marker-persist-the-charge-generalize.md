---
# isaac-7li9
title: 'Durable turn marker: persist the charge, generalize hail/inflight to all turn sources'
status: draft
type: feature
priority: normal
created_at: 2026-07-06T15:43:54Z
updated_at: 2026-07-06T16:32:03Z
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

## Design decisions (approved 2026-07-06)

- **D1 — Location:** `<root>/sessions/turns/<session-id>.edn`, a private detail of the SessionStore implementation.
- **D2 — One writer: the bridge.** The bridge is the ONLY writer of turn markers, for every source. Hail claim ordering: worker hands the full delivery record to the bridge inside the charge → bridge records the marker (delivery payload embedded: attempts, backoff, claimed-at) → worker deletes `hail/deliveries/{id}.edn` only after the record returns. Crash window yields a duplicate (marker + stray delivery), never a loss; startup dedup rule: marker is authoritative, stray delivery file removed.
- **D3 — Atom is cache.** The in-memory `in-flight*` atom remains the fast concurrency gate; it is rebuildable from the store on restart. The marker file is durability only.
- **D4 — Orphan = marker with no live in-memory in-flight entry.** Never age-based: markers live for the whole turn, so age cannot distinguish a live 4-minute turn from an abandoned one. At startup the atom is empty → all surviving markers are orphans (correct). At runtime a live turn always has its atom entry → can never be stolen/double-driven.
- **D5 — Bridge owns resume, per source:** `:hail` → hand embedded delivery back to the hail store (attempts+1, backoff, dead-letter budget intact); `:cron` → rebuild charge and dispatch; `:comm` → dispatch only if interruption is fresh (config `:turn-resume-window-ms`, default 10 min, hot-reloadable), else drop marker and log.
- **SessionStore abstraction (constraint):** the bridge delegates to the SessionStore to record inflight turns, clear them, and find orphaned turns. No caller touches `sessions/turns/` directly. `hail/inflight/` is deleted; 0tf3's `recover-orphaned-inflight!` is removed, not generalized.
