---
# isaac-c58s
title: Reconcile hail frequency onto the shared session selector
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-26T16:28:54Z
updated_at: 2026-06-27T18:26:34Z
parent: isaac-4e4b
blocked_by:
    - isaac-nbgn
---

Child of isaac-4e4b — LAST, deliberately. Make hail's :frequency BE the shared :select map (or map cleanly onto it) from isaac-nbgn (B1), so hail stops parsing its own selector and consumes the shared agent-side resolver. hail keeps its extras: --band (named/saved selector), --reach all (fan-out — the others are :one), --payload + async delivery.

## Why last
Refactors WORKING code just shipped (ebm2 stable-id lifecycle + kt1m :crew selector). Highest risk, lowest new value of the four migrations. Do it only after the shared lib is battle-tested on prompt/chat/acp.

## Scope
- hail send builds the shared :select from --session/--crew/--session-tag (already has these) + --create (mapped from :spawn-session: false->:never, true->:if-missing; no :always) + --reach; serializes it as :frequency.
- The router resolves via the shared resolver (or the shared resolver is what the router already does — converge them).
- --with-* override on hail (set the spawned/targeted session's params). Reconcile with hail's current behavior.
- Keep band/reach-all/payload/async.

## Acceptance
- hail's selector logic == the shared one (no hail-specific duplicate); :frequency is the serialized :select.
- All hail routing/delivery features still green (ebm2/kt1m scenarios).
- hail-specific extras (band, reach all, payload, async) preserved.

Blocked by isaac-nbgn (B1). Surfaced 2026-06-26.

## Delta analysis (grounded in router.clj, 2026-06-26)

Core finding: hail ALREADY has a duplicate of the shared filter — `router.clj` `matching-sessions [band sessions hail]` (~line 166) filters by id ∈ ids / tags ⊇ session-tags / crew ∈ crew-ids, identical to `isaac.session.selector/matching-sessions` + `session-matches?`. The heart of this bean is subtractive: delete hail's copy, call the shared one; `:frequency` becomes the serialized shared `:select`.

Deltas / what stays hail-specific:
- **:spawn-session (bool) -> :create**: false -> :never (match-only, undeliverable if none), true -> :if-missing. No :always (hail's :create is a two-value subset). Translate at the send/router boundary.
- **:reach :all is hail's extra**: shared `resolve-session-targets` is pick-one (:reach :one). `:all` fan-out wants EVERY match -> uses the shared `matching-sessions` filter directly, NOT the pick-one resolver. So hail reuses the shared filter for both reaches; the shared pick-one only for :reach :one; :all stays hail's broadcast logic (ebm2 children).
- **Band-merging stays hail's**: `effective-id-filter`/`effective-tag-filter`/`crew-selector-set` union the band's saved selector with the hail's (the --band extra). Output is one shared `:select`. OPEN: hail allows a crew SET via the union; shared `:crew` is single — either the shared filter accepts a set or hail collapses. (small decision)
- **Processing crew + --with-* override stay**: `effective-crew` (session crew -> cfg default -> :main) is distinct from :crew-as-selector and stays hail-specific; `--with-crew`/`--with-model`/... feed the same behavioral-keys -> `create-with-resolved-behavior!` path prompt uses. Reconcile override with effective-crew.
- **Keep**: band, :reach :all, payload, async delivery, ebm2 stable-id lifecycle, kt1m :crew selector.

## SETTLED (2026-06-26): --prefer applies to hail
`--prefer recent|oldest` (from isaac-4e4b) DOES apply to hail when `:reach :one` and multiple sessions match — it picks the target among matches (default recent), same tiebreak as the shared selector. The chosen session is then delivered to (bind-idle/wait per existing async semantics). For `:reach :all`, `--prefer` is irrelevant (all matches are targeted).

## Test posture
Existing hail router/delivery features (ebm2/kt1m) are the regression net — they MUST stay green (selector == shared one, no behavior change). New scenarios assert the translation (spawn-session<->create), that the shared filter is in use, and --prefer picking among multi-match under :reach :one.

## Gherkin design finalized (2026-06-26, reviewed with Micah)

### Record shape
- Rename `:frequency` -> `:frequencies` GLOBALLY (proper "open hailing frequencies" nod; the old name implied repetition). Touches router/delivery/store/cli code, `get-in [:frequencies ...]`, and every `frequency.*` in the hail features.
- `:frequencies` holds the SAME flat map the prompt command builds — selection keys + `:with-*` override keys inline (NOT a separate `:override` slot). The shared core projects it: select keys -> matching; `:with-*` -> behavioral-keys -> create-with-resolved-behavior!.
- Resolved outputs stay top-level on the record (`:crew`, `:session`, `:candidates`, `:attempts`, ...) — kept separate from the input flat map because the delivery worker re-resolves each tick (selector must survive). That input/output split is why we did NOT fully flatten the record.
- Feature tables use dotted paths, no nested EDN: `frequencies.session | [:engine-room]`, `frequencies.with-crew | :navigator`.

### New @wip scenarios
1. **--prefer orders the frozen candidates** (router.feature) — reach:one multi-match freezes `candidates`; `:prefer recent|oldest` sorts that snapshot (oldest-first for :oldest). Placement (a): router orders, worker binds first idle in order.
2. **--with-crew overrides the processing crew** (router.feature) — `:with-crew` sits at the top of `effective-crew` (override -> session crew -> cfg default -> :main); selection unaffected.
3. **--with-model overrides the dispatched turn** (delivery.feature) — representative of the behavioral path; effort/context-mode follow the same projection, no separate scenarios.

### Rewrites (vocabulary)
4. `:spawn-session` -> `:create` adoption: rewrite `spawn-session.feature` -> `session-create.feature`; `spawn-session true` -> `create :if-missing`, absent/false -> `create :never` (hail default). `:always` out of scope (two-value subset), noted in prose. Rename `effective-spawn` -> `effective-create`, send/cli `--spawn`/`:spawn-session` -> `--create`.

### Regression net
Existing ebm2/kt1m router/delivery scenarios MUST stay green (selector == shared one, no behavior change).

### Write entanglement
The 3 new scenarios are @wip-safe to write now. The `:frequencies` rename + `:create` rewrites of EXISTING scenarios CANNOT be gherkin-only — they break the passing suite unless the code changes too, so they land WITH the implementation, not before.

## Deploy
Migrate zanebot hail config: :frequency -> :frequencies, :spawn-session -> :create before deploy (one-time, ops). Strict validation will fail loud if missed.
