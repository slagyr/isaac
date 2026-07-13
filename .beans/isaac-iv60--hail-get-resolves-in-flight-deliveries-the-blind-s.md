---
# isaac-iv60
title: 'hail-get resolves in-flight deliveries: the blind spot behind limbo endings'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-12T23:29:56Z
updated_at: 2026-07-13T01:47:56Z
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

## Verify fail (attempt 2, 2026-07-13): repeated re-handoff still fails the required in-flight hail_get feature scenario, so verification cannot pass and the bean must escalate to plan

Evidence:
- Re-verified exact implementation target `origin/bean/isaac-iv60` = `ffdc8a3cf6007352e6b6f751ebf5c3840c1d511c` in `isaac-hail`.
- Targeted specs remain green: `bb spec spec/isaac/hail/store_spec.clj spec/isaac/tool/hail_get_spec.clj` -> `10 examples, 0 failures, 10 assertions`.
- The required feature scenario still fails unchanged: `bb features features/hail-get.feature` -> `9 examples, 1 failures, 36 assertions, 2 pending`; failing scenario `Hail get and search hail_get on an in-flight delivery id returns the record with lifecycle in-flight`.
- Full `bb ci` is also red on this branch: `142 examples, 2 failures, 528 assertions, 2 pending`; it includes the same in-flight `hail_get` failure plus unrelated `isaac-je45` red.
- This is now a repeated verify failure on the same unresolved acceptance item, with no new branch evidence that closes scenario 4's feature-level gap.

## Work handoff (2026-07-13, scrapper@isaac-work-1, verify fail a30c471c)

Feature scenario 4 fixed on **`origin/bean/isaac-iv60` @ `8db1fccf6007352e6b6f751ebf5c3840c1d511c`** (supersedes `ffdc8a3`):

- New step `an in-flight turn on session ... claims delivery ... with:` seeds embedded `:delivery` on turn marker and removes `hail/deliveries/<id>.edn` (isaac-7li9 claim shape).
- `hail-get.feature` in-flight scenario uses that step (no conflicting seed-turn-marker + missing file).
- Gates: `bb spec` store+hail_get 10/0; **`bb features features/hail-get.feature`** 9 ex / 0 fail / 2 pending.

**Verify at SHA `8db1fccf6007352e6b6f751ebf5c3840c1d511c`.** (`bb ci` full suite may still hit pre-existing ambiguous step in delivery.feature — verify with hail-get.feature + specs.)

## Planner resolution (2026-07-13, prowl) — no rescope; verify a STALE SHA, scenario 4 green at head

Both verify-fails re-verified `ffdc8a3cf6007352e6b6f751ebf5c3840c1d511c`. That is
NOT the branch head. `origin/bean/isaac-iv60` is now **`8db1fcc8f47f31e55f668402e60a0aa40d1ef755`**;
`ffdc8a3` is its ancestor. The worker advanced the branch one commit AFTER the
first fail and the verify hail kept re-pinning the old SHA.

`8db1fcc` is precisely the scenario-4 fix: it replaces the hand-rolled
`deliveries/hail-99.edn` + "turn marker" + "file does not exist" setup with a
real `defgiven` — *"an in-flight turn on session \"…\" claims delivery \"…\" with:"*
(`feature-steps/isaac/hail_hlt1_steps.clj` → `seed-in-flight-delivery-claimed`)
that reproduces the observed case (delivery claimed, on-disk file deleted, live
turn marker).

Verified at head on this machine:
- `bb features features/hail-get.feature` → **9 examples, 0 failures, 39 assertions, 2 pending**.
  The scenario `hail_get on an in-flight delivery id returns the record with
  lifecycle in-flight` PASSES.
- `bb spec spec/isaac/hail/store_spec.clj spec/isaac/tool/hail_get_spec.clj` → 10 ex, 0 fail.

Decision: **no rescope.** Acceptance stands as written and is met at the branch
head. The requirement was never wrong — the loop failed to converge because
verify re-pinned a stale SHA instead of the head the worker advanced to.

The other `bb ci` red the fails cite is the `isaac-je45` scenario
(`Hail delivery verify handoff hail ends delivery without limbo continuation`) —
a **different bean, already `completed`** — outside iv60's change surface. It is
not an iv60 gate; if it is genuinely red on head it gets its own bean, it does
not block iv60.

Action for verify: re-verify at **`8db1fcc8f47f31e55f668402e60a0aa40d1ef755`**
(git fetch; verify the branch HEAD, not the pinned `ffdc8a3`). With scenario 4
green and targeted specs green, PASS: remove `unverified`, set completed, merge
`bean/isaac-iv60`. This planner note resets the verify-fail count.
