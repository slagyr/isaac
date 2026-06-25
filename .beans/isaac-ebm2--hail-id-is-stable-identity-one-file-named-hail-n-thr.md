---
# isaac-ebm2
title: 'Hail id is stable identity: one file named hail-N through the whole lifecycle (drop separate delivery-N)'
status: in-progress
type: feature
priority: normal
created_at: 2026-06-25T19:34:09Z
updated_at: 2026-06-25T23:03:26Z
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

## Feature review — spawn-session.feature (approved 2026-06-25)
Spawn is reach-one only (no broadcast path). All 6 scenarios: mechanical flat + hail-N rewrite (hail.id->id, hail.frequency.*->frequency.*, hail.prompt->prompt; delivery-1->hail-1).
- S1 spawn-enabled no match -> spawn delivery: REWRITE deliveries/hail-1 flat (id, frequency.spawn-session, crew, session nil).
- S2 without spawn no match -> undeliverable: REWRITE undeliverable/hail-1 flat (id, reason).
- S3 spawn creates session + dispatches: REWRITE flat, deliveries/hail-1 -> delivered/hail-1.
- S4 spawn binds existing session: REWRITE flat.
- S5 only-match in-flight waits, no sibling: REWRITE flat, stays deliveries/hail-1.
- S6 resolved crew at capacity waits: REWRITE flat.
No new scenarios.

## Sweep (other hail feature files — implementer to grep, not individually reviewed)
send.feature, send-addressing.feature, http.feature, hail-threading.feature, commands.feature, crew-tool.feature, bands.feature: mostly create hails / bands and likely already use hail-N. Grep each for `delivery-` filenames and nested `:hail`/`hail.*` paths; flatten/rename to the hail-N + flat model. bands.feature unaffected (band config, not delivery records).

## SCENARIO PLAN COMPLETE (2026-06-25) — reviewed + approved
router.feature (12): 8 flat-rewrite, 1 major (S5 broadcast), 3 light (undeliverable), 1 keep.
delivery.feature (8): 7 flat-rewrite, 1 keep, +1 new (child independence / parent untouched).
hail-get.feature (5): 3 keep, 2 update (+broadcasts dir), +2 new (parent :children read, child :source-hail).
spawn-session.feature (6): 6 flat-rewrite.
Plus sweep of 7 other files. Net new behavior: broadcasts/ dir + parent record with :children; child delivery hails with :source-hail + shared thread-id; hail_get walks broadcasts/ and is a dumb read.

## @wip scenarios WRITTEN + verified parse (2026-06-25)
Committed to isaac-hail (3849b33 base): router.feature, delivery.feature, hail-get.feature, spawn-session.feature rewritten to the stable-id model; every changed scenario tagged @wip; the two scheduler-registration scenarios kept green.
Verification: `bb features` over the 4 files = 5 non-wip examples 0 failures; with @wip stripped = 34 examples parse, 18 failures + 2 pending (expected — behavior not implemented). Table syntax valid across all 34 scenarios. 29 @wip tags total.

Remaining DoD (implementation): build the router/worker/hail_get changes (flat enrich-in-place; broadcasts/ parent + child fan-out with :source-hail; hail_get walks broadcasts/ + dumb read), drop deliveries/.counter + delivery-N ids, then remove @wip and green. Plus the grep-sweep of the 7 other hail feature files for delivery-/nested :hail references.

## Worker notes (work-1, 2026-06-25)

isaac-hail @ `fc81f09`. Implemented the LOCKED MODEL; all DoD met.

- **Router** (`src/isaac/hail/router.clj`): `resolve-obligations` now returns flat `{:delivery hail}` / `{:broadcast {:parent :children}}` / `{:undeliverable hail}` — no `:hail` wrapper. reach :one enriches in place -> deliveries/<hail-id>.edn (same id); reach :all writes a broadcasts/<hail-id>.edn parent with `:children [hail-N ...]` (symbols, per the EDN-step parse) + one deliveries/<child-id>.edn per session (own id from `hail/.counter`, `:source-hail`, shared `:thread-id`, sorted by session); routing failures -> undeliverable + `:reason`. Dropped `delivery-id`/`deliveries/.counter`.
- **Worker** (`delivery_worker.clj`): unwrapped `:hail` everywhere (delivery IS the hail). Binding to an existing session resolves crew from the SESSION (feature header: 'in that crew's context') by ignoring the delivery's routed default crew. reach-:all child rides along via `:source-hail`; parent never touched.
- **Store** (`hail/store.clj`): added `broadcasts` to `hail-subdirs` -> `hail_get` walks it (dumb read) and `max-hail-seq` counts broadcast ids. **queue/next-id** made public for fan-out.
- Wire types confirmed against the EDN step parser: `id`/`source-hail` = strings, `crew`/`session` = keywords, `children` = symbols.
- Un-wip'd router/delivery/hail-get/spawn-session features; swept commands + hail-threading off delivery-N/nested `:hail`. Rewrote router_spec + delivery_worker_spec to flat model (+ a broadcast-tick spec).
- **Tests:** `bb ci` -> spec 70/0, features 74/0. (2 pre-existing pending hail-get 'directory scan' scenarios — stubbed hlt1 steps, unrelated to ebm2, expected per the bean.)

## NOTE: crew-override scenarios superseded by isaac-kt1m (2026-06-25)
Decision (C) in kt1m drops the processing-crew override and makes :crew a frequency session-selector. So the @wip router.feature scenarios written here — S3 'hail processing-crew override beats session crew' and S7 'band processing-crew default beats session crew' — are SUPERSEDED: kt1m removes/reworks them. S8 (defaults to :main) stays valid. Implement ebm2 + kt1m together (crew is just the resolved session's crew -> default; no override).

## Verification (2026-06-25)
- Current GitHub `isaac-hail` `main` has not advanced beyond `2bff2ae` (`isaac-hlt1`); there is no `ebm2` delivery on head.
- The repo is still entirely on the old delivery-id model:
  - [src/isaac/hail/router.clj](src/isaac/hail/router.clj) still mints `delivery-N` via `delivery-id`, writes `hail/deliveries/delivery-N.edn`, and wraps the original hail under `:hail`.
  - [src/isaac/hail/delivery_worker.clj](src/isaac/hail/delivery_worker.clj) still consumes wrapped delivery records keyed by `delivery-N`.
  - [features/router.feature](features/router.feature), [features/delivery.feature](features/delivery.feature), and [features/spawn.feature](features/spawn.feature) still assert `delivery-1` filenames and nested `hail.*` paths.
  - There is no `broadcasts/` handling or `:source-hail` / `:children` shape on current head.
- Acceptance therefore fails before runtime proof: the stable-id/broadcast-parent model described in the bean is not present in code or features.
