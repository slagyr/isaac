---
# isaac-7li9
title: 'Durable turn marker: persist the charge, generalize hail/inflight to all turn sources'
status: completed
type: feature
priority: normal
created_at: 2026-07-06T15:43:54Z
updated_at: 2026-07-06T18:54:18Z
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

## Scenarios (approved one-by-one, 2026-07-06)

Committed @wip:
- isaac-agent `features/session/turn_markers.feature` (commit 988b245)
- isaac-hail `features/turn-marker-claim.feature` (commit dbebf03)

New steps (approved): `a turn marker exists for session "..." with:` (assert), `no turn marker exists for session "..."`, `a turn marker exists for session "..." referencing delivery "..."` (Given seed), `the orphaned turn markers are:` (exact-set orphan query). The assert steps must be registered for BOTH isaac-agent and isaac-hail feature suites. Reuses the Grover `wait` gate + `the turn ends on session` machinery from concurrency.feature.

Design note (2026-07-06, approved): the stale-delivery dedup guard runs in the delivery worker's TICK (owner of deliveries/), not a startup pass — an every-tick invariant: never dispatch a delivery a turn marker already references; remove it and log `:hail/stale-delivery-removed` at warn.

## Verdicts on existing scenarios (implementation contract)

- isaac-hail delivery.feature:27 "a bound delivery dispatches..." — REWRITE: inflight/ file assertions become turn-marker assertions.
- isaac-hail delivery.feature:445 + :477 (isaac-0tf3 orphan recovery pair) — REMOVE: recover-orphaned-inflight! is deleted; orphan recovery moves to the resume bean (isaac-vdfc) keyed off turn markers.
- isaac-hail delivery.feature: any other inflight/ file references — REWRITE to marker equivalents.
- isaac-agent concurrency.feature — KEEP unchanged: in-memory in-flight semantics are unaffected (atom stays the runtime gate; D3).

## Acceptance

- [ ] `bb features features/session/turn_markers.feature:17` green (isaac-agent)
- [ ] `bb features features/session/turn_markers.feature:33` green (isaac-agent)
- [ ] `bb features features/session/turn_markers.feature:44` green (isaac-agent)
- [ ] `bb features features/turn-marker-claim.feature:18` green (isaac-hail)
- [ ] `bb features features/turn-marker-claim.feature:49` green (isaac-hail)
- [ ] delivery.feature verdicts applied (rewrites done, 0tf3 pair removed); full suites green in both repos
- [ ] One-time removal checks (not scenarios): hail/inflight/ directory unused; recover-orphaned-inflight! gone; no caller outside SessionStore touches sessions/turns/
- [ ] @wip removed from all five scenarios


---

## Progress (worker) — Phase A DONE, Phase B remaining

**Phase A (isaac-agent) — landed, green, on `main` `909af70`:**
- SessionStore protocol gains `record-turn-marker!` / `clear-turn-marker!` /
  `get-turn-marker` / `turn-markers` (memory = atom; index/sidecar = files at
  `sessions/turns/<id>.edn` via impl_common helpers). `spi/orphaned-turn-markers`
  = markers with no live in-flight entry (D4).
- The **bridge** is the single writer: `dispatch-charge!` records the marker after
  `mark-in-flight!` and clears it in the same `finally`. Marker holds source +
  (hail) delivery-id/embedded payload + started-at; resolved values NOT stored.
- `features/session/turn_markers.feature` un-@wip, 3 scenarios GREEN; new steps
  registered (marker matches / no marker / seed orphan / orphaned set).
- Additive: `bb verify` 1173 specs / 582 features / 0 failures. Existing paths
  unchanged; isaac-hail unaffected (still pins the pre-Phase-A agent).

**Phase B (isaac-hail) — NOT yet done:**
1. Bump the isaac-agent pin to `909af70` (main + marigold subprojects).
2. Delivery worker `launch-delivery!`: dispatch via the **bridge** with the full
   delivery record embedded in the charge (`:hail-delivery`, carrying attempts/
   backoff/claimed-at) so the bridge writes the marker; delete
   `hail/deliveries/<id>.edn` only AFTER the record returns. Stop the worker
   managing `hail/inflight/` and `mark/clear-in-flight!` directly.
3. **Remove** `hail/inflight/` and `recover-orphaned-inflight!` (isaac-0tf3).
4. Stale-delivery guard in the delivery **tick**: skip+remove any delivery a turn
   marker already references; log `:hail/stale-delivery-removed` at warn.
5. `features/turn-marker-claim.feature` un-@wip (2 scenarios) + register the
   marker assert/seed steps in the hail suite (share with isaac-agent).
6. delivery.feature verdicts: `:27` rewrite (inflight → marker), the isaac-0tf3
   orphan pair REMOVE, other `inflight/` refs → marker equivalents.
7. Removal checks: `hail/inflight/` unused; `recover-orphaned-inflight!` gone;
   no caller outside SessionStore touches `sessions/turns/`.


---

## Phase B DONE — bean complete (unverified)

**isaac-hail `main` `4242b74`** (pin bumped to isaac-agent `9e44e47`):
- Delivery worker claims via the **bridge**: the delivery record is embedded in
  the charge (`:hail-delivery`), the bridge records the durable turn marker, then
  the worker deletes `hail/deliveries/<id>.edn`. Marker cleared (+ in-flight gate
  released) in the finally.
- **Stale-delivery guard** in `tick!`: a delivery already referenced by a turn
  marker is a claim-time-crash stray — removed + `:hail/stale-delivery-removed`
  (warn), never re-dispatched.
- **Removed** `hail/inflight/` (dir/paths/list) and `recover-orphaned-inflight!`
  (isaac-0tf3); "inflight" dropped from `hail-subdirs`. Deliveries re-queue to
  `deliveries/`; delivered/failed + dead-letter accounting unchanged.

**Acceptance — all met:**
- turn_markers.feature (isaac-agent) 3/3 green; turn-marker-claim.feature
  (isaac-hail) 2/2 green; **@wip removed from all five**.
- delivery.feature verdicts applied: the two isaac-0tf3 scenarios REMOVED; the
  now-vacuous `hail/inflight/…` file assertions dropped (mechanism gone); header
  reworded. delivery_worker_spec orphan-recovery unit tests + `write-inflight!`
  removed.
- Removal checks: `hail/inflight/` unused; `recover-orphaned-inflight!` gone; the
  only `sessions/turns/` access is inside the SessionStore impl (impl_common).
- Both suites green: isaac-agent `bb verify` 1173 spec / 582 feature (Phase A;
  B1 kept turn_markers green); isaac-hail `bb ci` 118 spec / 118 feature, 0 fail.

**Cross-bean flag (for verifier/planner):** the concurrently-landed **isaac-3tvq**
added `@wip` scenarios to delivery.feature, one of which — "the inflight recovery
window defaults to 2h" — asserts on the `hail/inflight/` recovery mechanism this
bean **removes**. It's `@wip` (excluded, suite still green), but when 3tvq is
worked it must be reconciled with 7li9's design (recovery moves to the resume
bean isaac-vdfc, keyed off turn markers — NOT inflight/). Left untouched (not my
bean). Rebased over 3tvq's delivery.feature edits (kept my 0tf3 removal).
