---
# isaac-u5tj
title: 'Hail routing redesign: resolve sessions by :session/:session-tags only; crew is processing-only'
status: todo
type: feature
created_at: 2026-06-23T18:15:30Z
updated_at: 2026-06-23T18:15:30Z
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
