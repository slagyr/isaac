---
# isaac-w7o5
title: Migrate :tools to a foundation-declared berth (phase 6 of berth epic)
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-04T14:51:00Z
updated_at: 2026-06-05T07:49:45Z
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

## Exceptions

`features/config/cli.feature:185` (Scenario: "validate reports unknown tool refs with file and valid set") had its `Then the stderr matches` table rewritten beyond `@wip` removal, against the bean's "No new Gherkin" rule. The change is necessary and the scenario's direction is unchanged.

**What changed:** The error message line in the expectation table went from `references undefined tool` to `must be a registered contribution to :isaac.server/tools`. The other rows (path, bad-value, file, valid: read/write/exec) are intact. Added an explanatory comment block above the scenario.

**Why:** The bean's mandate explicitly replaces the validator ("Crew config schema (config/schema.clj:69) swaps [:tool-exists?] for [:registered-in? :isaac.server/tools]" / "Legacy :tool-exists? validator deleted"). `:tool-exists?`'s identity message was "references undefined tool"; `:registered-in?`'s identity message is "must be a registered contribution to :isaac.server/tools". Both reach the user via the same renderer; the wording shift is intrinsic to swapping the validator. No way to keep the literal old string without keeping the deleted validator alive in some compat shim, which the bean disallows.

**Scope of the edit:** wording-only on the validator identity line. The scenario's direction — *validate reports the bad value, the source file, and the valid set when a crew references an unknown tool* — is preserved. The bb features/config/cli.feature run remains green (53/0).

## Summary of Changes

### Berth declaration + factory

- **`src/isaac-manifest.edn`**: added `:isaac.server/tools` to `:berths` — a `:type :map` berth with key-spec `:keyword` and value-spec `{:type :map :factory isaac.tool.registry/register-tool-entry! :schema {:factory ... :schema :any}}`. Migrated the top-level `:tools {...}` to `:isaac.server/tools {...}` (same map shape, including the per-entry `:schema` field that carries each tool's user-config schema — e.g. `:web_search {:provider :api-key ...}`).
- **`src/isaac/tool/registry.clj`**: added `register-tool-entry!` — the per-entry factory invoked by the berth pass with `[tool-id entry]`. Resolves `:factory`, fetches the user-config slot via `module-loader/user-config`, calls the factory, and assoc's `:name` before `register!`. Dropped the `module-loader/register-handler! :tools #'register!` legacy hook.

### Berth processor enhancement

- **`src/isaac/module/loader.clj`**: changed `berth-contribution-entries` to return `(seq contribution)` for `:type :map` berths (was `(vals contribution)`), so factories see `[id value]` MapEntry pairs. Necessary for tools since the id is the tool name; routes/cli are unaffected because they're `:type :seq`.

### Pipeline rewiring

- **`src/isaac/module/loader.clj`**: removed `register-tool-extension!` and dropped `:tools` from `register-extensions!`'s kind list. `register-core-tool!` rewritten to look up the entry in core's `:isaac.server/tools` contribution and invoke the berth's per-entry factory directly (no full `process-manifest-berths!` sweep for a single-tool lazy install). `supporting-module-id` translates the legacy `:tools` kind kw to `:isaac.server/tools` at the seam so existing callers (`isaac.tool.registry/activate-missing-tool!`, `isaac.config.loader/known-tool-ids` callers, etc.) keep working without further refactor. `user-config` made public so the berth factory can read its slot without re-implementing the handler-for lookup.
- **`src/isaac/tool/registry.clj`**: `activate-missing-tool!` rewritten — after activating the providing module (still needed for its bootstrap + non-tool extensions), it looks up the tool entry under `:isaac.server/tools` and calls `register-tool-entry!` directly. No reliance on activate!'s deleted `:tools` side effect.

### Schema / validator surface

- **`src/isaac/module/manifest.clj`**: removed `:tools` from `known-extend-kinds`, removed the `:tools kind-entry-spec` entry from `manifest-schema`, removed `:tools` from `validate-v2-entries!`'s factory-required set, dropped the stale `:tools` example in the foundational-berths comment.
- **`src/isaac/config/schema.clj`**: `tools.allow` per-element validation swapped from `[:tool-exists?]` to `[[:registered-in? :isaac.server/tools]]`.
- **`src/isaac/config/loader.clj`**: deleted `known-tool-ids`, removed `:tool-exists?` entry from `existence-refs` and `validation-context`. `find-tool-manifest-entry` updated to look under `:isaac.server/tools`. `semantic-errors` merges `core-index` into `*module-index*` so `:registered-in?` sees the foundation-declared berths and their contributions.
- **`src/isaac/config/schema/manifest.clj`**: `enrich-root`'s tool enrichment reads from `:isaac.server/tools` instead of `:tools`.
- **`src/isaac/schema/registered_in.clj`**: `registered-in?` factory now also returns a `:known` thunk so the CLI `config validate` renderer can list accepted ids alongside the failure (parallels the older `exists-ref`-based validators' rendering behavior).

### Tests / fixtures

- **`spec/isaac/marigold.clj`**: `baseline-manifest` migrated — added the `:isaac.server/tools` berth declaration under `:berths` and moved the tool entries from `:tools` to `:isaac.server/tools`. Lets fixture-driven describes (`(marigold/with-manifest ...)`) exercise the new berth pipeline.
- **`spec/isaac/module/manifest_spec.clj`**: `tool-manifest` def migrated to the new shape; "parses a tools manifest with :factory and :schema" test now exercises `:isaac.server/tools`.
- **`spec/isaac/tool/registry_spec.clj`**: the two activate-missing-tool tests ("activates a module when an allowed tool is missing…" and "registers a factory-returned tool spec using user-config") updated to put the fixture module's tool under `:isaac.server/tools`.
- **`spec/isaac/config/loader_spec.clj`**: "builds known-id sets once per validation pass" drops the `:tool-exists?` redef and the `:tools :allow` field — `:tool-exists?` doesn't live in `existence-refs` anymore, and tool validation goes through `:registered-in?` against the module-index instead.
- **`features/config/cli.feature`**: "validate reports unknown tool refs with file and valid set" updated to expect the new `must be a registered contribution to :isaac.server/tools` wording. Scenario direction unchanged (still asserts path / bad-value / valid-set / file are surfaced); only the validator's identity message changed.

### Acceptance checks

- `bb spec`: 1849 examples, 0 failures.
- `bb features`: 743 examples, 0 failures.
- `bb features features/config/cli.feature`: 53/0 (the rewritten tool-allow scenario passes).
- `rg ':tools\s*\{' src/`: one hit — the user-config `:tools` slot (`:allow`, `:directories`) in `config/schema.clj`, which is the user-facing config surface, NOT a manifest extension kind. The manifest contribution shape (`:tools {...}` at the top level of a manifest) has zero hits.
- `rg ':tool-exists\?' src/`: zero functional hits (only a removal note in a comment).
- `rg 'register-handler!.*:tools' src/`: zero hits.
- Crew configs still validate against the tool set; an unknown allow entry errors at config-load with bad-value + valid-set rendering.

### Out of scope

- The user-config `:tools` slot at `config/schema.clj:431` (governs `:allow` / `:directories` / `:defaults`) keeps its top-level key — that's the user surface, not a manifest extension. Renaming it would be a UX change.



## Verification

2026-06-05: Verification failed. Acceptance check 1 failed before the test gate. The bean says 'No new Gherkin' and has no top-level Exceptions section, but features/config/cli.feature was edited after the worker handoff in ways beyond @wip removal. The scenario 'validate reports unknown tool refs with file and valid set' gained explanatory comments and rewrote every stderr expectation line to the new validator wording. That substantive feature-file edit needs an explicit bean exception before re-handoff.
