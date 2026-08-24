---
# isaac-u7ug
title: Hail records vanish under restructured storage — send returns id, no file anywhere
status: completed
type: task
priority: normal
created_at: 2026-08-23T17:12:07Z
updated_at: 2026-08-24T14:21:15Z
---

Observed 2026-08-23 (post isaac-k27z storage restructure era): 'isaac hail send --band isaac-work' returned id 1232eaed; minutes later the record existed in NO hail dir (pending/inflight/delivered/deliveries all checked, find+grep across ~/.isaac/hail empty) while a worker turn DID resume the bean — so delivery may have happened with the record cleaned, or persistence is racing. Either way the audit trail broke: a sent hail must be findable somewhere from send until archive (hails-never-die requires durable records). Diagnose-first: trace 1232eaed's lifecycle in the current hail module; pin the record-retention contract in scenarios. Layer: isaac-hail — no overlap with in-flight work.

## Diagnosis (1232eaed)

Server log on 2026-08-23 reconstructed the full lifecycle:

1. `17:07:34Z` `:hail/sent` from CLI (`queue.clj`) — id `1232eaed`, band `isaac-work`.
2. `17:07:34Z` `:hail/routed` — 2 candidates, outcome `:delivery`.
3. `17:07:35Z` `:hail/bound` — session `isaac-work-2`, crew `scrapper`.
4. `17:13:30Z` `:hail/turn-ended` outcome `:delivered` then `:hail/delivered`.

No `1232eaed.edn` exists now in pending/deliveries/delivered/failed/undeliverable/broadcasts. `finish-delivered!` logged success, but the delivered file is gone. Claim deletes `hail/deliveries/<id>.edn` after writing the turn marker; if the delivered/ write is lost (or later cleaned) the audit trail is empty. Worker turn still ran — matches the observed "send returned id, no file anywhere".

Root cause: lifecycle dirs are the only store. Claim, stale-guard, and pending-move delete the current file. There is no durable copy independent of those deletes.

## Implementation

Durable ledger at `hail/records/<id>.edn` (`store/persist-record!`). Written on send, route (delivery/undeliverable/broadcast children), claim, finish-delivered, finish-failed, defer, reschedule, requeue. Worker never deletes `records/`. `find-by-id` scans `records/` last and tags it `:delivered` when no lifecycle dir holds the id. Store root resolution now matches queue/router (nexus → loader → CLI `*root*`).

Scenarios: delivery.feature retention after turn; hail-get.feature hail_get after delivery. Specs: store ledger lookup, queue send writes records/, worker findable after lost delivered/ write.

Suites: `bb spec` 132/0; `bb features` 139/0 (2 pre-existing pending hail-get search stubs).

## Verify fail (attempt 1, 2026-08-24): implementation is not landed on isaac-hail main; bean commit exists only on origin/bean/isaac-u7ug


## Landed on main (verify-fail repair, 2026-08-24)

Fast-forwarded isaac-hail `main` to `9c9d742` and pushed `origin/main`. `git branch -r --contains 9c9d742` now includes `origin/main`. Suites re-checked on main: `bb spec` 132/0; `bb features` 139/0 (2 pre-existing pending hail-get search stubs).

## Verify fail (attempt 2, 2026-08-24): isaac-hail main now contains 9c9d742, but full `bb features` is red in crew-tool.feature on current main


## Planner adjustment (2026-08-24, prowl@isaac-plan) — verify-fail attempt 2

**Decision: rescope acceptance off full-suite green; route suite-health repair first as a separate bean.**

### Why not "fix crew-tool under u7ug"

- Diff for `9c9d742` does not touch `features/crew-tool.feature`, hail-send tool, or the pending-list step path the scenarios assert.
- u7ug product contract is durable ledger + findability after lifecycle deletes (`delivery.feature` retention, `hail-get.feature` post-delivery, store/queue/worker specs). Holding that bean hostage to pre-existing dispatch red is wrong scope.
- Worker body claimed full `bb features` 139/0 — that claim is **false on current main** and must not remain as acceptance.

### Acceptance (supersedes suite lines above)

Verify on isaac-hail at a SHA that contains the ledger work:

```
bb spec
bb features features/delivery.feature features/hail-get.feature
```

- `bb spec` — 0 failures (pending allowed only if pre-existing and listed).
- Targeted features — 0 failures on the two files above.
- Do **not** require full `bb features` green for this bean.

### Suite-health follow-up

Draft **isaac-d13o** owns the two red crew-tool hail-send dispatch scenarios (expected 1 pending, got 0). Promote/work that bean separately; it is not a gate on u7ug completion.

### Reset

Verify-fail escalation counter reset by this note. Worker: confirm targeted suites on main@ledger SHA (or equivalent), retag unverified, hand to verifier. Do not re-expand acceptance to full `bb features` without a green baseline from d13o (or equivalent suite repair).


## Targeted suites confirmed (planner rescope, 2026-08-24)

Confirmed on isaac-hail `origin/main` @ `9c9d742` (contains durable ledger):

- `bb spec` — 132 examples, 0 failures, 289 assertions
- `bb features features/delivery.feature features/hail-get.feature` — 34 examples, 0 failures, 126 assertions, 2 pending (pre-existing hail-get search stubs: directory scan / templated band search — not yet implemented)

Full `bb features` is **not** claimed. crew-tool hail-send dispatch red stays on draft **isaac-d13o**.
