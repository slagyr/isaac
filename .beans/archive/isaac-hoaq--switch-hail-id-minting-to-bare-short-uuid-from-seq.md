---
# isaac-hoaq
title: Switch hail id minting to bare :short-uuid (from sequential hail-N)
status: completed
type: feature
priority: normal
created_at: 2026-06-26T00:13:12Z
updated_at: 2026-06-26T03:28:30Z
blocked_by:
    - isaac-a3fb
---

Make hail mint ids via ShortUuidStrategy (bare, no prefix) instead of SequentialStrategy "hail-". UUIDs are collision-free without a shared counter, so concurrent/external producers don't race on hail/.counter. Builds on isaac-a3fb (UuidStrategy/ShortUuidStrategy — verified).

## Production change (small) — isaac-hail src/isaac/hail/queue.clj
- `naming-strategy`: `(naming/->SequentialStrategy root "hail" "hail-" fs*)` -> `(naming/->ShortUuidStrategy nil)` (bare; ids = 8-hex short-uuid).
- Remove `sync-hail-counter!` and its call in `next-id` (dead — ShortUuidStrategy is stateless, no .counter). `next-id` simplifies to just `(naming/generate (naming-strategy ...))`.
- next-id is the SINGLE hail-id path: used by send! AND the router reach-:all child fan-out, so both originals and children get short-uuids. Good.
- DO NOT touch delivery_worker.clj:~161 `->SequentialStrategy ... "session-"` — that mints spawned SESSION ids, not hail ids. Out of scope.

## Test rework (the bulk) — these hardcode the deterministic minted id "hail-1"
Short-uuids are random, so the filename hail/pending/<id>.edn is unpredictable. Rework MINTED-id tests to assert FORMAT + uniqueness and reference the pending file id-agnostically (scan pending/ for the single file, or capture the id from stdout/json), NOT "hail/pending/hail-1.edn".
- **features/send.feature** (~9 scenarios): every `hail/pending/hail-1.edn`, `stdout contains "hail-1"`, `id: "hail-1"`, and 'mints a unique SEQUENTIAL id' (hail-1/hail-2) must change. New shape: assert the id matches ^[0-9a-f]{8}$ (or with the send tool's reported id), two sends produce DISTINCT ids, and the pending record is found id-agnostically. May need a new step like 'a pending hail matches:' (id-agnostic) if none exists.
- **spec/isaac/hail/queue_spec.clj**: 'mints sequential hail ids' (hail-1/hail-2) -> 'mints a unique short-uuid each call' (distinct, format). Other `should= "hail-1"` -> capture the returned id / assert format. The :thread-id default (was "hail-1") follows the id.
- router/delivery/spawn/hail-get features are UNAFFECTED — they supply the id in Given (don't depend on minting). hail-get scenarios that hardcode hail-42/hail-1 as GIVEN data are fine.

## Acceptance
- A sent hail's id is a bare 8-hex short-uuid (no "hail-" prefix); two sends produce distinct ids.
- No hail/.counter is created or read by the send/mint path.
- Router reach-:all children also get short-uuids.
- send.feature + queue_spec reworked to format/uniqueness + id-agnostic file refs; all hail specs + features green.
- Then DEPLOY to zanebot (existing hail-N terminal records coexist fine — ids are just strings; hail_get reads by id regardless of format).

## Notes
Existing zanebot records (delivered/failed/undeliverable, named hail-N or delivery-N) coexist — no migration needed; new hails just get short-uuid names. Follow-up to a3fb. Surfaced 2026-06-25, Micah: make :short-uuid the default for hail.

## Verification (2026-06-26)
- Current GitHub `isaac-hail` `main` includes `isaac-hoaq` at `1346654`.
- The mint path is switched in [src/isaac/hail/queue.clj](src/isaac/hail/queue.clj): `naming-strategy` now uses `ShortUuidStrategy nil`, `next-id` is stateless, and the old counter sync path is gone.
- Focused proofs are green on that head:
  - `bb spec spec/isaac/hail/queue_spec.clj` -> `8 examples, 0 failures`
  - `bb features features/send.feature` -> `9 examples, 0 failures`
- Full repo lane is also green on current head aside from the same two pre-existing pending hail-get directory-scan scenarios:
  - `bb ci` -> `71` spec examples, `0` failures; `76` feature examples, `0` failures, `2` unrelated pending
