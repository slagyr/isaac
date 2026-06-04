---
# isaac-qqgv
title: Migrate :comm to a foundation-declared berth (phase 8 of berth epic)
status: todo
type: task
priority: normal
created_at: 2026-06-04T14:53:56Z
updated_at: 2026-06-04T14:54:15Z
parent: isaac-brth
blocked_by:
    - isaac-jr64
    - isaac-8yxs
    - isaac-2ecl
    - isaac-ma0j
    - isaac-f77b
---

Phase 8 of isaac-brth. The most complex migration — the entire berth
machinery applied to today's comm extension. Earlier phases de-risk
this one; if isaac-jr64 (config berth processing) and isaac-ho18
(provider migration) work cleanly, comm follows the same pattern.

## Today's wiring (to be replaced)

- `:comm` is a hardcoded top-level manifest key
  (`module/manifest.clj`).
- isaac-discord, isaac-imessage, isaac-acp each declare a comm
  factory in their own manifest's `:comm` field.
- User config `:comms {…}` references comm impls by `:type` key.
- `src/isaac/config/schema.clj:231` declares the `comm-instance`
  shape with `:comm-exists?` validation.
- The comm registry, slot-tree lifecycle, and Reconfigurable
  protocol all live in isaac-server today, dispatched via
  `module-loader/register-handler!` at boot.

## After this bean

- Isaac core's manifest declares `:isaac.server/comm` as a berth
  with both `:manifest` and `:config` shapes — exactly matching
  the design from `tmp/isaac-server-manifest.edn` and the
  marigold.bridge fixture used in isaac-jr64.
- Contributing modules (isaac-discord, isaac-imessage, isaac-acp)
  drop their top-level `:comm` key; their manifests instead carry
  a contribution to `:isaac.server/comm` as a map keyed by impl id
  (per the design's map-vs-seq rule).
- Each module's `defmethod` for `isaac.server.comm/create-comm-node!`
  is registered when the module's namespace loads (per isaac-f77b's
  lifecycle) — replacing today's per-impl `:factory` symbol in the
  manifest.
- Crew/comm validators swap `[:comm-exists?]` for
  `[:registered-in? :isaac.server/comm]`.
- `:dynamic-schema [:value :extra-schema]` in the config berth's
  `:value-spec` pulls per-impl `:extra-schema` from each module's
  contribution and merges into the user's slot-validation schema
  (isaac-2ecl).
- Top-level `:comm` removed from `module/manifest.clj`.
  `register-handler! :comm …` calls deleted.

## Acceptance

Existing comm tests are the safety net.

- `bb features features/comm/` passes (Discord routing, iMessage
  routing, ACP — every existing comm scenario stays green).
- `bb spec` green; no regressions.
- Hot-reload scenarios still work: user edits comm config; existing
  Discord channel reconfigures in place (if it satisfies
  `Reconfigurable`) OR is torn down and rebuilt. Behavior preserved
  either way; the bean doesn't introduce the optional
  `on-config-change` protocol — that's a separate later concern.
- Greps:
  - `rg ':comm\s*{' src/` across isaac + module repos — zero hits in
    manifests at the top-level position.
  - `rg ':comm-exists\?' src/` — zero.
  - `rg 'register-handler!.*:comm' src/` — zero.
- Cross-repo: discord, imessage, acp manifests use the new
  contribution shape and pass their own test suites.

## Out of scope

- Adding new comm impls.
- The optional `on-config-change` Node-protocol method
  (Reconfigurable). Today's slot-tree behavior keeps the
  rebuild-on-change default; in-place reconfig as a Node-protocol
  opt-in is a phase-2-of-comm follow-up bean.
- Renaming the `:type` discriminator (we settled on keeping `:type`
  in our design discussion).

## Dependencies

- isaac-jr64 (config berth processing) — the comm berth uses every
  config-side primitive.
- isaac-8yxs (manifest-only berth processing — for any manifest-
  only sub-berths if we end up splitting routes/handlers off).
- isaac-2ecl (:dynamic-schema).
- isaac-ma0j ([:registered-in?]).
- isaac-f77b (Module protocol — for namespace loading to fire
  defmethod registrations).
- isaac-c2g5 ✓ (lexicon — :type :symbol in fixture/manifest schemas).
- isaac-htkp ✓, isaac-yb39 ✓.

## Notes for the worker

- THE marigold.bridge fixture in isaac-jr64 is structurally a smaller
  mirror of what isaac.server/comm needs to become. If those tests
  pass, the comm migration's mechanism is already proven; this bean
  is mostly mechanical replacement at the production manifests.
- Cross-repo coordination required: discord, imessage, acp each
  need their manifest updated AND their tests must pass with the new
  shape. Do as one coordinated change set — don't leave one
  contributor's manifest in a stale shape while another's migrates.
- Today's `isaac.server.comm` already has a registry, dispatch
  machinery, Reconfigurable protocol, etc. The conversion REUSES
  most of this — only the registration entry point changes (berth
  pipeline instead of `register-handler! :comm`).
- The comm berth's `:config :factory` is the existing
  `isaac.server.comm/create-comm-node!` (or whatever the current
  per-slot builder is) repackaged as a multimethod dispatching on
  `:type`. Each contributing module's namespace registers its
  `defmethod` on namespace load.
