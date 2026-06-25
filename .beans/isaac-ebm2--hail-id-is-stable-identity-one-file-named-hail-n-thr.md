---
# isaac-ebm2
title: 'Hail id is stable identity: one file named hail-N through the whole lifecycle (drop separate delivery-N)'
status: draft
type: feature
priority: normal
created_at: 2026-06-25T19:34:09Z
updated_at: 2026-06-25T22:14:11Z
---

An id is identity — it must not change once minted, and the filename must equal the id. Today the router TRANSFORMS a `hail-N` into a brand-new `delivery-M` record (new id, new file), so the same logical hail appears under different filenames as it's processed. That's wrong.

## Principle (Micah, 2026-06-25)
- A hail's `:id` is assigned at creation and NEVER changes; the filename is always `<hail-id>.edn`.
- The single hail file MOVES between lifecycle dirs (pending -> inflight -> delivered/failed/undeliverable) as it's routed/processed.
- The file CONTENT is enriched in place (routing adds resolved addressing: crew/session; the worker bumps :attempts, :next-attempt-at, etc.).
- There is NO separate `delivery` id/record. A 'delivery' is just the hail at a later lifecycle stage.

## Current (to change)
- `isaac.hail.router`: `delivery-id` mints `delivery-N` via SequentialStrategy on `hail/deliveries`; `resolve-obligations` writes one `deliveries/delivery-M.edn` per obligation, wrapping the hail under `:hail`.
- Lifecycle dirs: pending/ (hail-N), deliveries/ + inflight/ + delivered/ + failed/ (delivery-M), undeliverable/ (hail-N).
- Delivery worker processes delivery-M files.

## Proposed model (RECOMMENDED — confirm during scenario review)
- Drop the `deliveries/.counter` + delivery-N id. The hail file itself flows through the dirs, keeping `hail-N`.
- Router enriches the pending hail in place (adds resolved crew + session) and moves it to inflight/ (or undeliverable/ with :reason). No new id.
- **reach :all fan-out (the one real tension): MODEL A** — the single `hail-N` file carries per-session delivery state: `:deliveries [{:session a :attempts N :status ...} {:session b ...}]`. It stays in inflight/ until all sessions resolve, then moves to delivered/ (all ok) or failed/ (any exhausted). The worker updates per-session entries in place; one stable file/id.
  - (Alternatives considered: B = reach :all spawns child hails with new ids — reintroduces id churn; C = atomic all-or-nothing per hail — loses independent per-session retry. A chosen.)

## Affected code
- `isaac.hail.router`: remove delivery-id minting; enrich+move the hail file; resolve-obligations -> in-place addressing on the hail (+ :deliveries list for reach :all).
- `isaac.hail.delivery_worker`: operate on hail-N files; per-session attempt/backoff/dead-letter within the :deliveries list; move file by aggregate state.
- Dir layout: drop `deliveries/` (or repurpose); pending -> inflight -> delivered/failed; undeliverable unchanged (already hail-N).

## Scenarios
Review existing one at a time (router.feature 12, delivery.feature 8, spawn-session.feature 6; plus hail-threading/hail-get references to ids) — propose updates + any new. Recorded here as approved.

## Acceptance (sketch — pending design lock)
- A hail keeps its `hail-N` id and filename from creation through delivered/failed/undeliverable; the file moves dirs and content is enriched in place.
- No `delivery-N` ids or `deliveries/` files exist.
- reach :all tracks per-session delivery state within the one hail file (model A); independent retry preserved.
- Undeliverable still keyed by hail-N (unchanged).

## Notes
Surfaced 2026-06-25 on zanebot: hail-6 became failed/delivery-3.edn; hail-2 stayed undeliverable/hail-2.edn — the rename inconsistency. Builds on the u5tj routing redesign (session-only resolution). Fan-out model A pending confirmation in scenario review.

## LOCKED MODEL (2026-06-25, Micah) — supersedes the "Proposed model" above

Anchor: any id a sender is handed must resolve via `hail_get` to a meaningful record. `hail_get` is a DUMB READ — returns whatever record matches the id; NO aggregation.

- **Flat content.** A routed hail file IS the hail, enriched in place (resolved `:crew`/`:session`, `:attempts`, etc.) — no `:hail` wrapper, no separate `delivery-N` id.
- **reach :one** -> the hail IS its single delivery. It flows `pending -> inflight -> delivered/failed`, keeping its id and filename throughout. `hail_get <id>` returns it + its delivery state.
- **reach :all** -> the original hail is a durable BROADCAST PARENT:
  - Router mints N child delivery hails (e.g. hail-42 -> hail-43, hail-44, hail-45), each: its own unique id, ONE resolved session, the shared `:thread-id`, and `:source-hail hail-42` back-ref.
  - The parent record gets `:children [hail-43 hail-44 hail-45]` and, once routed, MOVES to a new `broadcasts/` dir (durable + queryable). It is NOT a delivery and does not flow through inflight/delivered/failed.
  - Children flow through the delivery lifecycle independently (own file, own retry/dead-letter).
  - `hail_get hail-42` returns the broadcast parent (incl the `:children` id list); the caller can `hail_get` each child for its status. NO aggregation in hail_get.
- **undeliverable** unchanged (already keyed by hail-N).

Dirs (corrected): `pending/` (raw) -> `deliveries/` (routed-ready, files now named hail-N) -> `inflight/` (claimed/dispatching) -> `delivered/`|`failed/`. `undeliverable/` for routing failures (hail-N). `broadcasts/` for reach :all PARENT records (durable, queryable; NOT a delivery). DROP only `deliveries/.counter` + the delivery-N id scheme — deliveries are named by hail id. All hail ids (originals + fan-out children) mint from `hail/.counter`.

Decisions retired: Model X (original becomes one delivery) REJECTED — would make `hail_get <original-id>` return only one delivery and hide the rest. Model A (one file + :deliveries list) REJECTED — Micah wants a file per delivery.

## Feature review — router.feature (approved 2026-06-25)
Wire shape: child back-ref = :source-hail; parent :children = plain id list [hail-2 hail-3].
- S1,S3,S4,S6,S7,S8 (reach-one binds/overrides/defaults): REWRITE -> deliveries/hail-1.edn, flat fields (id, frequency, crew, session top-level; no :hail wrapper, no delivery-N).
- S2 (unbound candidates pool): REWRITE -> deliveries/hail-1.edn, flat crew/session nil + candidates.
- S5 (reach :all): MAJOR REWRITE -> parent broadcasts/hail-1.edn with :children [hail-2 hail-3]; children deliveries/hail-2.edn + hail-3.edn each flat with :source-hail hail-1 + resolved crew/session.
- S9,S10,S11 (-> undeliverable): LIGHT -> undeliverable/hail-1 already correct; drop the 'deliveries/delivery-1 does not exist' line (or update to hail-N).
- S12 (router tick registered): KEEP.
Feature description rewrite: router enriches the pending hail in place (flat) and moves it to deliveries/ (reach :one) keeping its id; reach :all writes a broadcasts/ parent (with :children) + one deliveries/ child per session; undeliverable keyed by hail-N.

## Feature review — delivery.feature (approved 2026-06-25)
Pattern: delivery files become hail-N (not delivery-N); content FLAT (delivery IS the hail: hail.id->id, hail.prompt->prompt, hail.payload->payload; drop the separate :id delivery-N). Worker logic unchanged — a reach-:all child is just a delivery hail carrying :source-hail (rides along, no dispatch effect). Parent broadcasts/ record is NEVER touched/aggregated by the worker.
- S1 bound->delivered: REWRITE flat, hail-1 files.
- S2 unbound binds idle candidate: REWRITE flat + candidates (reach-one pool still applies).
- S3 in-flight session->pending: REWRITE flat.
- S4 at-capacity crew->pending: REWRITE flat.
- S5 one-turn/session serializing: REWRITE two delivery hails hail-1/hail-2, flat.
- S6 dispatch failure->attempts+backoff: REWRITE flat; inflight/hail-1.
- S7 exhausts max->failed/: REWRITE flat; dead-letter log :id hail-1.
- S8 worker tick registered: KEEP.
- NEW: a reach-all child delivery completes independently (delivered/hail-2 exists) AND broadcasts/hail-1 parent :children list is unchanged — locks child independence + no parent aggregation/update.

## Feature review — hail-get.feature (approved 2026-06-25)
Already mostly aligned (hail-N filenames, flat top-level fields, subdir walk, no index).
- S1 fetch by id from any subdir: KEEP.
- S2 dir scan no index: UPDATE — add 'broadcasts' to the scanned subdir list.
- S3 templated-band rendered prompt/params: KEEP.
- S4 returns full record: KEEP.
- S5 dir scan for templated-band hails: UPDATE — add 'broadcasts'.
- NEW: hail_get on a broadcast parent returns its :children id list WITHOUT aggregating (dumb read).
- NEW: hail_get on a fan-out child returns its :source-hail back-ref.
hail_get must walk broadcasts/ in addition to pending/deliveries/inflight/delivered/failed/undeliverable.
