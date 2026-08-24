---
# isaac-u7ug
title: Hail records vanish under restructured storage — send returns id, no file anywhere
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-08-23T17:12:07Z
updated_at: 2026-08-24T14:05:31Z
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
