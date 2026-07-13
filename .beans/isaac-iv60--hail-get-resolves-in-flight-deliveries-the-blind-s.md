---
# isaac-iv60
title: 'hail-get resolves in-flight deliveries: the blind spot behind limbo endings'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-12T23:29:56Z
updated_at: 2026-07-13T00:13:46Z
---

## Bug

hail_get on an in-flight delivery id returns 'hail not found': the pending record is consumed at binding and the delivered record is not yet written, so a hail is INVISIBLE precisely while it is being worked. Observed at least 7 times since 2026-07-10 across work and verify bands (4 in one 12-minute window tonight).

## Why it matters (systemic)

The band skills tell agents to fetch thread context via hail_get and to reply_to their delivery id. When that lookup errors mid-turn, models get rattled and end turns with status narration instead of terminal hail actions — implicated in the limbo-ending pattern that just exhausted isaac-tki3's continuation budget (dead-letter 23:26 2026-07-12) and in this week's manual nudges. The je45 detector bounds the damage; this bean removes a root cause.

## Fix

hail-get resolves ids across ALL lifecycle stores: pending, deliveries (in-flight), delivered, failed, undeliverable — returning the record plus a :lifecycle field naming where it was found. An in-flight delivery is not an error; it is the CALLER'S OWN CONTEXT.

## Scenarios (worker writes; required coverage)

1. hail_get on an id whose record sits in deliveries/ returns it with :lifecycle :in-flight.
2. Same for delivered/ and failed/ (:lifecycle values accordingly).
3. Unknown id still errors 'hail not found' (only truly absent ids fail).
4. A turn's own delivery id (the exact observed case) resolves during the turn — feature-level, via the existing hail fixtures.

## Implementation (scrapper@isaac-work-2, 2026-07-13)

Root cause: after claim (isaac-7li9), `hail/deliveries/<id>.edn` is deleted while the turn runs; `find-by-id` only walked on-disk subdirs, so active delivery ids vanished until `delivered/` was written.

Fix on `origin/bean/isaac-iv60` @ `ffdc8a3cf6007352e6b6f751ebf5c3840c1d511c` (isaac-hail):
- `find-by-id-with-lifecycle` scans pending/deliveries/delivered/failed/undeliverable/broadcasts and falls back to embedded `:delivery` on registered session turn markers (`:lifecycle :in-flight`).
- `hail-get` tool attaches `:lifecycle` on every hit; unknown id unchanged.
- Spec: `store_spec`, `hail_get_spec` (10 ex, 0 fail). Features: new `hail-get.feature` scenarios for in-flight (marker, no deliveries file) and failed lifecycle.

Note: full `bb ci` features lane blocked here by sibling `isaac-server` step classpath skew; targeted specs green. Verify at pinned SHA.

## Verify fail (attempt 1, 2026-07-13): acceptance scenario 4 is still red on the pinned branch, so hail_get does not yet verify the in-flight delivery case at feature level

Evidence:
- Verified implementation repo `isaac-hail` at exact branch target `origin/bean/isaac-iv60` = `ffdc8a3cf6007352e6b6f751ebf5c3840c1d511c`.
- Implementation diff is present and relevant (`src/isaac/hail/store.clj`, `src/isaac/tool/hail_get.clj`, `spec/isaac/hail/store_spec.clj`, `spec/isaac/tool/hail_get_spec.clj`, `features/hail-get.feature`).
- Targeted specs are green: `bb spec spec/isaac/hail/store_spec.clj spec/isaac/tool/hail_get_spec.clj` -> `10 examples, 0 failures, 10 assertions`.
- But the required feature-level acceptance is red: `bb features features/hail-get.feature` -> `9 examples, 1 failures, 36 assertions, 2 pending`.
- The failing scenario is the bean's key observed case: `Hail get and search hail_get on an in-flight delivery id returns the record with lifecycle in-flight`.
- Full gate is also red on this branch: `bb ci` -> `142 examples, 2 failures, 528 assertions, 2 pending`; one of the two failures is the same `hail_get` in-flight scenario above. The other failure is `Hail delivery verify handoff hail ends delivery without limbo continuation (isaac-je45)`.
- Because scenario 4 explicitly requires that a turn's own delivery id resolves during the turn at feature level, and that scenario is not green, this bean cannot pass verification yet.
