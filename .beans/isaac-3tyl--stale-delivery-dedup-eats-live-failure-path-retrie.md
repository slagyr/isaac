---
# isaac-3tyl
title: 'Stale-delivery dedup eats live failure-path retries: guard must require an orphaned marker'
status: completed
type: bug
priority: high
created_at: 2026-07-06T22:13:19Z
updated_at: 2026-07-06T22:25:22Z
---


## Gap

Observed live (2026-07-06 21:59, zanebot, isaac-lcay, hail 6db8652b): a work turn failed on a transient chatgpt HTTP 503. `reschedule!` correctly wrote the retry (attempts 1) back to `hail/deliveries/` — and 52ms later the next worker tick's stale-delivery dedup guard (isaac-7li9, `:hail/stale-delivery-removed`) deleted it, because the turn's marker still existed (its finally had not yet cleared it). The marker was then cleared. Net: the hail was annihilated — no pending delivery, no marker, no dead-letter, no signal. The bean sat claimed/in-progress, permanently stranded.

Log evidence:
- 21:59:45.982 `:hail/attempt-failed` id 6db8652b attempts 1 :api-error
- 21:59:46.034 `:hail/stale-delivery-removed` id 6db8652b

## Severity

Every failure-path retry is eaten within one tick in the deployed code (agent 0.1.9 / hail 0.1.8). The 5-attempt dead-letter budget can never engage; any transient turn error becomes a permanent silent stall.

## Root cause

The 7li9 dedup rule ("a delivery already referenced by a turn marker is stale — remove it") was designed for the claim-crash window, where the marker is authoritative and the stray delivery is a duplicate. A live turn's failure-reschedule transiently creates the same file pair (retry delivery + not-yet-cleared marker), and the guard cannot tell the two apart. Spec gap, not implementation error: isaac-7li9's S4 scenario never covered the mid-flight reschedule window.

## Fix

Extend D4's orphan philosophy to the dedup guard: **the stale-delivery guard fires only when the referencing marker is an orphan** (no live in-flight entry for its session).
- Crash case (startup): atom empty → marker orphaned → dedup applies, exactly as isaac-7li9 spec'd.
- Live case (failure-reschedule): the turn's in-flight entry still exists at tick time → guard hands off; the finally clears the marker moments later and the retry proceeds normally.
- The reschedule-then-clear-marker ordering stays as-is (crash-safe).

## Acceptance

- [x] `features/turn-marker-claim.feature` — failure-reschedule survives tick while in-flight (new scenario)
- [x] `features/turn-marker-claim.feature:47` — orphaned-marker stray still deduped (7li9 S4 regression)
- [x] `delivery_worker_spec` — live-marker vs orphan-marker unit coverage
- [x] Full hail spec + features green (`-M:dev-local`)

## Resolution (work-3, 2026-07-06)

**isaac-hail** — stale-delivery guard in `tick!` now requires an orphaned turn marker (`store/in-flight?` false) before removing a delivery file referenced by a marker. Live failure-reschedule pairs (marker + in-flight + deliveries/) are left alone until finally clears the marker.
