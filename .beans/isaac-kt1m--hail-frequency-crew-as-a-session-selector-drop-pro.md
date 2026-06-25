---
# isaac-kt1m
title: 'Hail frequency: :crew as a session selector; drop processing-crew override; require >=1 selector (fix match-all)'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-25T23:00:12Z
updated_at: 2026-06-25T23:07:47Z
blocked_by:
    - isaac-ebm2
---

Two coupled corrections to hail routing (u5tj follow-up):

## Bug (the trigger)
`matching-sessions` (isaac.hail.router) treats ABSENT :session AND :session-tags as MATCH-ALL: nil filters are permissive, so the predicate collapses to (and true true) and every session matches. So a frequency with no session selector (e.g. `:frequency {:crew "main"}`) silently broadcasts to ALL sessions, then reach :one binds one. Observed on zanebot: a `{:crew "main"}` hail 'worked' (delivered to agile-voyage) by accident. :crew was never consulted for matching (u5tj made it processing-only).

## Decision (Micah, 2026-06-25): option C
'Hail the main crew' is a valid intent and NOT a category error — it means 'deliver to sessions whose :crew is main'. u5tj over-corrected by dropping plain :crew addressing along with :crew-tags. Restore :crew as a SESSION SELECTOR; drop the processing-crew override entirely (Micah doesn't use it).

### Changes
1. **:crew becomes a session selector** in :frequency (and band) = 'sessions whose :crew == X', merged with :session/:session-tags via the existing effective-filter/intersect-or. matching-sessions adds the :crew filter (compare session's :crew field).
2. **Require >=1 session selector** (:session | :session-tags | :crew). With none -> :no-recipients undeliverable (NOT match-all). Validate the runtime frequency at send (hail-send tool / CLI / http) with [:requires-any? :session :session-tags :crew], mirroring the band schema; and harden matching-sessions so absent-all-selectors never match-all.
3. **Drop the processing-crew override.** The crew that processes a hail is simply the resolved SESSION's own :crew -> (get-in cfg [:defaults :crew] :main). Remove the hail-top-level :crew override and the band-level :crew processing default. effective-crew simplifies to: (id-keyword (:crew session)) or cfg default. (hail/band :crew now mean SELECTOR, not processing.)
4. **:crew-tags stays removed** (matching crew CONFIG tags is the category error u5tj correctly identified).
5. Band schema: :crew is a selector (single crew id or seq? — decide); :requires-any? becomes :session|:session-tags|:crew.

## OVERLAP with isaac-ebm2 (stable hail id) — IMPORTANT
ebm2 (todo, @wip scenarios already written) encodes the processing-crew OVERRIDE in router.feature scenarios that this bean REMOVES:
- router.feature S3 'a hail processing-crew override beats the matched session crew' -> REMOVE/rework (no override).
- router.feature S7 'a band processing-crew default beats the matched session crew' -> REMOVE/rework (band :crew is now a selector).
- router.feature S8 'processing crew defaults to :main' -> KEEP (still valid: session crew -> default).
- effective-crew references in delivery/spawn scenarios -> crew is just the session's crew now.
This bean ALSO needs NEW scenarios: 'frequency :crew selects sessions of that crew'; 'frequency with no session selector is undeliverable'; 'send rejects a frequency with no session selector'. Implement together with ebm2 (or sequence this after ebm2 and update its scenarios).

## Acceptance
- :frequency {:crew main} resolves to sessions whose :crew is main (not match-all); reach :one binds one, reach :all fans to all.
- A frequency with no :session/:session-tags/:crew -> undeliverable :no-recipients; send rejects it with a clear error.
- Processing crew = resolved session's :crew -> default; no hail/band override path.
- :crew-tags absent.
- ebm2 override scenarios removed/reworked; new crew-selector + no-selector scenarios added; green.

## Notes
Surfaced 2026-06-25: Micah noticed a :crew-only hail 'worked' on zanebot and questioned the u5tj removal of :crew addressing. Resolution: :crew is valid as a session selector (priority case), but :crew-tags and the processing override are not needed.
