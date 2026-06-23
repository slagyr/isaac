---
# isaac-u5tj
title: 'Hail routing redesign: resolve sessions by :session/:session-tags only; crew is processing-only'
status: in-progress
type: feature
priority: normal
created_at: 2026-06-23T18:15:30Z
updated_at: 2026-06-23T22:20:05Z
---

Rework hail routing so a hail resolves to an in-flight SESSION purely by session selectors, and the crew is chosen only AFTER resolution to PROCESS the hail. Rationale (Micah, 2026-06-23): a hail addresses an in-flight thing (a session); a crew is config, not in-flight, so matching recipients by crew/crew-tags is a category error.

Builds on isaac-a5ez (:spawn -> :spawn-session, verified).

## New model
### Session resolution — `:session` + `:session-tags` ONLY
- `matching-sessions` filters sessions solely by `:session` (ids) and `:session-tags` (the session's OWN tags via session-store/tags-of), merged across band + hail-frequency (existing effective-filter/intersect-or).
- REMOVE the `:crew` and `:crew-tags` rungs from session matching, and the `crew-config` lookup (router.clj ~125, ~169-175). This also kills the silent-drop bug (session whose crew isn't configured).
- A band/hail with no `:session` and no `:session-tags` resolves to nothing -> undeliverable :no-recipients (or spawn, below). A band lacking session selectors is usable only if the hail's frequency supplies them.
- `:reach` (:one|:all) unchanged; applies to the matched sessions.

### Processing crew — chosen AFTER resolution, in order
`effective-crew = (or hail :crew  ->  band :crew  ->  session :crew  ->  :main)`
(analogous to effective-reach: hail top-level or [:frequency :crew], then band, then session's :crew, then :main). The delivery's `:crew` (bound-delivery/candidate-entry) becomes this resolved value, not the raw session crew.

### Remove `:crew-tags` entirely
From band schema, frequency, router, and the send CLI. Crews are config (not in-flight) so tag-matching them as recipients makes no sense.

### `:crew` repurposed: selector -> single processing crew
- Band `:crew` and hail `:crew` become a SINGLE crew id (the processing default/override), NOT a seq selector. Schema: band `:crew` changes from `{:type :seq}` to a single crew id; remove `:crew-tags`.

### Spawn (builds on :spawn-session)
When no session matches AND :spawn-session AND :reach :one -> spawn a new session with crew = (hail :crew -> band :crew -> :main) (no session crew yet). REMOVE `matching-crews`, `available-host-crew`, and the `:no-host` undeliverable reason (there is always a default crew = main).

## Code touch points (isaac-hail)
- `router.clj`: matching-sessions (drop crew/crew-tags + crew-config); new `effective-crew`; resolve-obligations (use effective-crew, drop matching-crews/host-crews/:no-host); bound-delivery + candidate-entry (crew = effective-crew).
- `delivery_worker.clj`: spawn path uses effective-crew for host crew; crew-capacity check keys off the resolved processing crew.
- `resources/isaac-manifest.edn` band schema: remove `:crew-tags`; `:crew` seq -> single id (processing crew); confirm `:session`/`:session-tags` stay seqs.
- `cli.clj` / send-addressing: `--crew` now sets the hail processing-crew override (single); REMOVE `--crew-tag`.

## Feature / spec changes (REVIEW 1-at-a-time before locking)
Affected: features/router.feature, spawn.feature, send-addressing.feature, bands.feature, delivery.feature; spec/isaac/hail/router_spec.clj, delivery_worker_spec.clj. Many scenarios are built on crew/crew-tags matching and need rework or removal. Scenarios will be reviewed with Micah one at a time, then recorded here.

## Migration (clean break)
- `:crew-tags` configs become invalid.
- Bands defined only by `:crew`/`:crew-tags` no longer resolve; rewrite to `:session`/`:session-tags`.
- `--crew-tag` removed from `hail send`.
- Document in release notes.

## Acceptance
- Hails resolve to sessions via `:session`/`:session-tags` only (band + frequency); crew/crew-tags play NO part in resolution.
- Processing crew resolves hail -> band -> session -> main.
- `:crew-tags` removed from schema, router, CLI; band `:crew` is a single processing crew.
- Spawn uses the resolved crew; `:no-host` gone.
- Reviewed feature scenarios updated and green; specs updated.

## Notes
Supersedes the crew-matching half of the original hail routing. a5ez (:spawn-session rename) already verified; this builds on it.

## Feature review — router.feature (approved 2026-06-23)
- S1 reach-one band matches one session: REWRITE — band selector crew-tags -> session-tags; tag the session. Delivery crew stays = session crew.
- S2 reach-one pool unbound w/ candidates: REWRITE — frequency crew-tags -> session-tags on the two sessions; candidates keep crew=session-crew.
- S3 direct crew frequency binds to that crew's session: REMOVE (addressing a session by crew is gone). Optionally replace with crew-override scenario.
- S4 direct session frequency binds exact session: KEEP.
- S5 reach :all fans out per matching session: REWRITE — crew-tags -> session-tags on sessions.
- S6 band + session-tag intersect: REWRITE — band crew-tags -> session-tags; sessions tagged band-tag + warp-coil so intersection narrows to coil-tinkering.
- S7 unknown band -> :unknown-band: KEEP.
- S8 reach-one band no matching crew -> undeliverable: REWRITE + RENAME to 'no matching session'; crew-tags -> session-tags -> :no-recipients.
- S9 reach :all zero sessions -> :no-recipients: REWRITE — crew-tags -> session-tags.
- S10 router tick registered: KEEP.
NEW scenarios (processing-crew order): hail :crew override beats session crew; band :crew beats session crew; default to :main when none.

## Clarifications (2026-06-23, Micah)
1. Crew resolution FINAL fallback is the CONFIG default crew, not hardcoded :main:
   effective-crew = hail :crew -> band :crew -> session :crew -> (get-in cfg [:defaults :crew] :main).
   Spawn (no session yet): hail :crew -> band :crew -> (cfg :defaults :crew, default :main).
2. The spawn crew is resolved at ROUTER time (deterministic now — no session needed) and CARRIED in the spawn delivery (:crew = resolved value), NOT left nil for the worker to fill.

## Feature review — spawn.feature (approved 2026-06-23)
- Preamble: REWRITE — match-or-create by session-tags; create under resolved crew (hail->band->cfg-default/main); drop crew-tags + :no-host language.
- S1 spawn reach-one no session -> spawn delivery: REWRITE — drop crew-tags; spawn delivery now CARRIES resolved crew (cfg-default/main or override), session nil.
- S2 without spawn, no session -> undeliverable: REWRITE — session-tags, no match, spawn off -> :no-recipients.
- S3 spawn + session-tags but no crew to host -> :no-host: REMOVE (:no-host gone; spawn always proceeds under cfg-default/main).
- S4 spawn creates tagged session + dispatches: REWRITE — crew-tags -> hail :crew override drives spawned crew; session-tags applied; dispatch.
- S5 spawn binds existing matching session: REWRITE — drop crew-tags; match by session-tags -> bind.
- S6 spawn whose only match in-flight waits, no sibling: REWRITE — drop crew-tags; session-tags match -> wait.
- S7 spawn waits when crew at capacity: REWRITE — capacity gates the RESOLVED crew; use hail :crew override (max-in-flight 1, busy session) -> wait.
Net: rewrite 7 (incl preamble), remove 1 (:no-host).

## Principle (2026-06-23, Micah): :frequency is ROUTING ONLY
- :frequency holds only routing selectors: :band, :session, :session-tags, :reach, :spawn-session.
- The :crew override is a PROCESSING concern -> lives at the hail TOP-LEVEL (:crew, single keyword), NOT in :frequency.
- So effective-crew = (or (:crew hail) (:crew band) (id-keyword (:crew session)) (get-in cfg [:defaults :crew] :main)) — reads hail top-level :crew, no [:frequency :crew] lookup. (Supersedes the earlier 'top-level or frequency' note.)

## Feature review — send-addressing.feature (approved 2026-06-23)
- Preamble: REWRITE — drop --crew-tag; --crew = processing override (single, hail top-level); --session/--session-tag = routing.
- S1 --crew populates :crew: REWRITE — --crew marvin -> :crew :marvin at hail TOP-LEVEL (single), not frequency {:crew [:marvin]}.
- S2 --session: KEEP.
- S3 --crew-tag: REMOVE (flag deleted).
- S4 --session-tag: KEEP.
- S5 combine --crew + --session-tag: REWRITE — :crew :marvin (top-level) + frequency {:session-tags ...}; reframe override+routing, not intersection.
- S6 --from-json band: KEEP.
- S7 bare - EDN: REWRITE example to new shape (:crew top-level, :session-tags in frequency).
- S8 no --prompt errors: REWRITE — address via --session-tag (not --crew) to isolate the missing-prompt error.
Net: keep 3, rewrite 5 (incl preamble), remove 1.

## Feature review — bands.feature (approved 2026-06-23)
- S1 validate accepts valid band: REWRITE — drop :crew-tags; new shape {:crew :ops :session-tags [...] :reach :one :spawn-session true} (single :crew).
- S2 validate rejects invalid :reach: REWRITE — drop :crew-tags from fixture; keep :reach :many -> rejects.
- NEW: band :crew must be a single id, not a seq — {:crew [:ops]} -> validation error.
- Bands remain config (config/hail/<name>.edn) — they ARE schema-validated, unlike commands/skills.

## NO LEGACY / NO BACK-COMPAT (hard requirement, Micah 2026-06-23)
Zero compatibility shims anywhere in this redesign:
- :crew-tags — removed from schema/router/CLI; not read anywhere.
- :crew selector-as-seq — NOT accepted; :crew is a single id; no 'use first element' coercion.
- :crew in :frequency — NOT read; effective-crew reads hail TOP-LEVEL :crew only. No dual-location read.
- crew-based resolution — gone; NO fallback to crew matching when session selectors are absent. A crew/crew-tags-only band -> undeliverable (:no-recipients), never silently routed.
- :no-host — removed entirely.
- :spawn alias — none (a5ez was a clean cutover).
- --crew-tag CLI flag — removed, not hidden/deprecated.
- Feature scenarios exercising old behavior — DELETED, not retained for compat.
DECISION (pending): removed keys (:crew-tags, seq :crew) should HARD-REJECT at config validate (loud migration signal) rather than fall through to the default unknown-key warning. Recommended: hard-reject.

## Feature review — delivery.feature (approved 2026-06-23): ALL KEEP
Operates on already-resolved deliveries (concrete :crew + :session); no :crew-tags. No edits. The :crew in deliveries/candidates is understood as the router-resolved processing crew, but fixtures supply it directly so no scenario changes. (S1-S8 all keep.)

## DECISION LOCKED (2026-06-23): hard-reject stale keys
Removed keys HARD-REJECT at config validate (loud migration signal), not soft unknown-key warning:
- a band/hail carrying :crew-tags -> validation ERROR.
- :crew as a seq -> validation ERROR (must be a single id).

## Feature review COMPLETE (2026-06-23)
All affected hail feature files reviewed + recorded: router.feature, spawn.feature, send-addressing.feature, bands.feature, delivery.feature. Unaffected: send, http, commands, crew-tool. Specs to mirror: router_spec.clj, delivery_worker_spec.clj. Scenario verdicts above are the implementation contract.



## Verification note (2026-06-23)

Verification failed on fetched GitHub `isaac-hail` `main` `2daafc1`. The touched spec slice is green: `bb spec spec/isaac/hail/router_spec.clj spec/isaac/hail/delivery_worker_spec.clj spec/isaac/config/hail_loader_spec.clj spec/isaac/hail/bands_spec.clj spec/isaac/hail/cli_spec.clj spec/isaac/hail/http_spec.clj` -> `47 examples, 0 failures, 118 assertions`.

But the reviewed feature surface is still red. Repo `bb features` is blocked by the pre-existing foundation tag/sha dependency poison in current module heads, so I verified the fetched hail head with a scratch `gherclj` classpath wired to the live verifier checkouts. That run fails the reviewed feature set with current behavior. The failing assertions are concentrated in:
- `features/send-addressing.feature`: all direct-addressing scenarios fail because the expected pending hail record is not found (`id` expected `hail-1`, got `nil`).
- `features/bands.feature`: all four `config validate` scenarios fail their output assertions.

So `u5tj` does not currently meet its own acceptance `Reviewed feature scenarios updated and green`, even though the underlying spec slices are green and the static redesign surfaces are present in code/schema/release notes.



## Re-verification note (2026-06-23)

Re-verified on the same fetched GitHub `isaac-hail` `main` `2daafc1` (no new code landed since the prior failure note). The touched spec slice is still green: `47 examples, 0 failures, 118 assertions`.

The reviewed feature surfaces are still red on current behavior. Re-running the focused reviewed subset via the scratch `gherclj` verifier path produced:`
- send-addressing.feature`: all direct-addressing scenarios still fail with the pending hail record missing (`id` expected `hail-1`, got `nil`).
- `bands.feature`: the valid-band and invalid-band validation scenarios still fail their output assertions.

So the earlier verification failure stands unchanged: current head still does not satisfy `Reviewed feature scenarios updated and green`.
