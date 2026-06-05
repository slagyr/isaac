---
# isaac-qqgv
title: Migrate :comm to a foundation-declared berth (phase 8 of berth epic)
status: completed
type: task
priority: normal
created_at: 2026-06-04T14:53:56Z
updated_at: 2026-06-05T16:18:56Z
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

## Exceptions

### Feature-file wording (validator identity messages)

Three approved scenarios had stderr/error expectation lines rewritten beyond `@wip` removal. Each scenario's *direction* is preserved (path / bad-value / file / valid-set rendering all still asserted); only the validator's identity message changed because the bean's acceptance explicitly swaps `[:comm-exists?]` for `[:registered-in? :isaac.server/comm]`.

- `features/config/cli.feature` ("validate reports unknown comm type refs with file and valid set"): `references undefined comm` → `must be one of` (regex-friendly pattern matching the small-set output of `:registered-in?`).
- `features/module/schema_composition.feature` ("Manifest referencing an unregistered ref fails fast at module activation", "Manifest declaring :type in its :schema fails to load"): the manifest fixtures changed shape from `:comm {…}` to `:isaac.server/comm {…}` (data-only update; the assertion strings are unchanged).

### Grep-acceptance literal hit on a user-config schema field

`rg ':comm\s*\{' src/` produces one hit at `src/isaac/charge.clj`: `:comm {:type :ignore :description …}` inside the charge entity's schema definition. That is a *user-config field schema*, not a manifest extension form — same situation as the `:provider {…}` hits documented in isaac-ho18. The bean's intent ("top-level manifest position") is satisfied: no manifest anywhere in this repo or in isaac-acp/discord/imessage uses the legacy `:comm` extension form.

### Cross-repo spec suites blocked on unrelated isaac drift

`bb spec` in isaac-discord and isaac-imessage fails at SCI analysis time on stale isaac API references (`config/load-config` in discord, similar in imessage) — pre-existing, not introduced by this bean. The manifest migrations themselves (EDN data) are syntactically valid; isaac-acp was already in this state and we filed isaac-lyg0 as a follow-up. Logging a sibling bug bean for discord and imessage so the next berth-touching change doesn't trip on it.

## Summary of Changes

### New berth declared in isaac core (`src/isaac-manifest.edn`)

- `:isaac.server/comm` — manifest-only `:type :map` berth with key-spec `:keyword` and value-spec `{:type :map :factory isaac.comm.registry/register-comm-entry! :schema {:factory :symbol :schema :any :configurable? :boolean}}`. User-instantiated slots in the `[:comms]` config path also count toward the berth via the `[:registered-in? :isaac.server/comm [:comms]]` second-arg form introduced in phase 7.

### Per-entry factory

- `isaac.comm.registry/register-comm-entry!` — resolves the entry's `:factory` symbol via `requiring-resolve` and calls `register-factory!` for the impl id.

### Contributions migrated

- **`isaac-discord/src/isaac-manifest.edn`**: `:comm {:discord ...}` → `:isaac.server/comm {:discord ...}`. Same per-impl schema fields.
- **`isaac-imessage/src/isaac-manifest.edn`**: `:comm {:imessage ...}` → `:isaac.server/comm {:imessage ...}`.
- **`modules/isaac.comm.telly/resources/isaac-manifest.edn`**: `:comm` → `:isaac.server/comm`.
- **`spec/isaac/marigold.clj`** baseline-manifest: `:comm` → `:isaac.server/comm` plus the berth declaration added under `:berths`.
- All spec-side fixture manifests (loader_spec, configurator_spec, comm_spec, etc.) migrated.

### Pipeline / loader rewiring

- **`src/isaac/comm/registry.clj`**: dropped the legacy `module-loader/register-handler! :comm` self-registration; added `register-comm-entry!` for the berth pass.
- **`src/isaac/module/loader.clj`**: `register-extensions!` is now a no-op — every extension kind now flows through `process-manifest-berths!`. `comm-kinds` reads from `:isaac.server/comm` instead of `:comm`. `supporting-module-id` translates the legacy `:comm` kind kw to `:isaac.server/comm` at the seam. `process-manifest-berth!` now catches per-factory errors, logs `:module/activation-failed`, and collects a structured error row — so one broken contributor doesn't abort the whole berth sweep (matches activate!'s legacy per-module error isolation).
- **`src/isaac/module/manifest.clj`**: `known-extend-kinds` is now empty (`#{}`); `kind-entry-spec` deleted; `validate-v2-entries!`'s factory-required set reduced to `#{:cli}`.
- **`src/isaac/config/configurator.clj`**: `activating-module-id` reads from `:isaac.server/comm`. `resolve-factory` looks up the entry in the providing module's berth contribution and calls `register-comm-entry!` directly when the impl isn't already registered (replaces the legacy activate!-time side effect). Wraps the call in try/catch and logs `:module/activation-failed` so the slot stays inert on load failure (matches legacy behavior).
- **`src/isaac/config/install.clj`**: `comm-validation-errors` reads the lazy-impl manifest probe from `:isaac.server/comm`.

### Schema / validator surface

- **`src/isaac/config/schema.clj`**: `comm-instance.type` swapped from `[:comm-exists?]` to `[[:registered-in? :isaac.server/comm [:comms]]]`.
- **`src/isaac/config/loader.clj`**: deleted `:comm-exists?` from `existence-refs` and `validation-context`. `known-comm-ids`, `find-comm-extension`, and `comm-reserved-schema-errors` updated to read from `:isaac.server/comm`. `manifest-schema-kinds` updated.
- **`src/isaac/config/schema/manifest.clj`**: `enrich-root` reads comm variants from `:isaac.server/comm`.

### Tests / fixtures

- All inline `:comm {<id> {…}}` fixtures across the spec suite (loader_spec, configurator_spec, manifest_spec, registry_spec, etc.) migrated.
- `spec/isaac/module/loader_spec.clj`: "wraps namespace load failures" rewired to use a `:bootstrap` symbol (the only remaining activate!-side failure path), since activate! no longer resolves `:comm` factory symbols.
- `spec/isaac/comm/registry_spec.clj`: rewritten to test `register-comm-entry!` (the new berth factory) instead of the deleted register-handler self-registration.

### Acceptance checks

- `bb spec`: 1849 examples, 0 failures.
- `bb features`: 743 examples, 0 failures.
- `rg ':comm\s*\{' src/` is documented above (one user-config schema field, no manifest forms).
- `rg ':comm-exists\?' src/` — zero hits.
- `rg 'register-handler!.*:comm' src/` — zero hits.
- Every isaac repo's manifest uses the new contribution shape.
