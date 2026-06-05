---
# isaac-w7o5
title: Migrate :tools to a foundation-declared berth (phase 6 of berth epic)
status: in-progress
type: task
priority: normal
created_at: 2026-06-04T14:51:00Z
updated_at: 2026-06-05T07:19:44Z
parent: isaac-brth
blocked_by:
    - isaac-8yxs
    - isaac-ma0j
---

Phase 6 of isaac-brth. Convert today's hardcoded `:tools` mechanism
to a foundation-declared manifest berth, and replace the legacy
`:tool-exists?` validator with `[:registered-in? :isaac.server/tools]`
(isaac-ma0j's primitive).

## Today's wiring (to be replaced)

- `:tools` is a hardcoded top-level manifest key
  (`src/isaac/module/manifest.clj` — `kind-entry-spec`-shaped).
- `src/isaac-manifest.edn:13` declares built-in tools (`:edit`,
  `:read`, etc.) with `:factory` symbols.
- `src/isaac/config/schema.clj:69` references `:tool-exists?`
  validation on crew config `:tools` field (users list which tools
  each crew can use; the validator asserts each name is registered).
- The `:tool-exists?` validator is defined somewhere in
  `isaac.config.loader` (per the brth bean's original phase 6
  description); it walks the loaded module-index for registered
  tool ids.

## After this bean

- Isaac core's manifest declares `:isaac.server/tools` as a
  manifest-only berth. Entry-level `:factory` is the existing
  tool-factory registration symbol (likely `register-tool!` or
  similar in the tool registry).
- Consumer manifests (isaac core's built-in tools; any module
  shipping additional tools) move from top-level `:tools` to a
  contribution to `:isaac.server/tools`.
- Top-level `:tools` removed from `manifest-schema` and
  `known-extend-kinds` in `module/manifest.clj`.
- Crew config schema (`config/schema.clj:69`) swaps
  `[:tool-exists?]` for `[:registered-in? :isaac.server/tools]`.
- Legacy `:tool-exists?` validator deleted; nothing else should
  reference it after the swap.

## Acceptance

No new Gherkin. Existing tool-related tests are the safety net.

- `bb features features/tool/` passes — every existing
  tool/crew/allowlist scenario stays green.
- `bb spec` green; no regressions.
- Greps come up clean:
  - `rg ':tools\s*{' src/` — zero hits in manifests (legacy form gone).
  - `rg ':tool-exists\?' src/` — zero hits (validator replaced).
  - `rg 'register-handler!.*:tools' src/` — zero hits.
- Crew configs continue validating tool references via the new
  primitive; an unknown tool name still errors at config-load.

## Out of scope

- Adding new tools or changing existing tool behavior.
- Per-tool `:schema` validation changes. Today's `:tools` entries
  with schemas (e.g. `:signal-flare {:schema {…}}` in the
  marigold baseline) keep their shape; the berth's
  `:manifest :schema` accepts a `:schema` field on entries.

## Dependencies

- isaac-8yxs (manifest-only berth processing).
- isaac-ma0j ([:registered-in?] validator).
- isaac-htkp ✓, isaac-yb39 ✓ (already complete — manifest + contribution validation).

## Notes for the worker

- Marigold test fixtures already exercise the tool surface
  (`spec/isaac/marigold.clj` baseline-manifest's `:tools` block).
  Those fixtures need updating to use the new berth contribution
  shape; otherwise tests that bind the marigold manifest will
  break.
- The `:signal-flare` tool entry has a `:schema {:provider {…}
  :api-key {…}}` field today. Confirm the new berth's
  `:manifest :schema` allows arbitrary per-entry schemas (it
  should — `:type :map` with no key/value restrictions, or a
  meta-schema reference once isaac-2yqb is ready).
