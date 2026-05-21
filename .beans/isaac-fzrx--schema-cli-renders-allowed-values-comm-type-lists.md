---
# isaac-fzrx
title: Schema CLI renders allowed values; comm :type lists registered comm kinds
status: completed
type: feature
priority: normal
created_at: 2026-05-18T19:05:47Z
updated_at: 2026-05-21T00:22:58Z
---

`isaac config schema comms.value` shows `:type` as a bare `string` with no hint of valid values. Goal: render the set of allowed values from the manifest registry so the CLI doubles as discoverability for comm types.

## Scope

1. Extend the spec→term renderer (`src/isaac/config/schema/term.clj`) to print an "options:" line when a spec carries an option source.
2. Define how the renderer discovers options:
   - For comm slots' `:type`, pull from `isaac.module/comm-kinds` (or the equivalent registry view of manifest `:comm` entries).
   - Plug-in point: spec map key `:options-from` (a keyword like `:comms`) that the renderer resolves at render time via a registered lookup function. Keeps the schema namespace free of module-registry deps.
3. Wire `comm-instance.:type` to use `:options-from :comms` (or similar).
4. Renderer should fall back gracefully when no system is loaded (e.g., `bb isaac config schema comms.value` outside a configured project — print the spec without options, or "(loaded at runtime)" — pick one).

## Out of scope

- Static `:options` for closed enums like `:context-mode`. Could be a follow-up bean; this one is dynamic-only.

## Definition of done

- `isaac config schema comms.value` lists the registered comm kinds (`console`, `acp`, `discord`, `telly`, etc., dependent on `:modules`).
- Renderer has a registration seam so other dynamic dispatches (slash commands, providers) can hook in later without changes to the term renderer.

## Acceptance criteria

- `bb features features/config/schema_cli_options.feature` passes (remove `@wip` before merge).
- `bb isaac config schema comms.value.type` lists registered comm kinds in an `options:` line.

## Summary of Changes

- Added options-line helper to src/isaac/config/schema/term.clj that renders an 'options: val1, val2, ...' line when a spec carries :options-from and a resolver is present in opts
- Wired options-line into leaf-block (for direct path lookups) and field-block (for map field views)
- Added :options-resolvers to spec->term opts — a map of keyword to zero-arg fn returning option strings
- Added comm-kinds fn to src/isaac/module/loader.clj that reads sorted comm kind names from the core manifest
- Added :options-from :comms to the :type field in comm-instance schema (src/isaac/config/schema.clj)
- Wired :options-resolvers {:comms module-loader/comm-kinds} into config/cli/schema.clj
- Removed @wip tag from features/config/schema_cli_options.feature

## Reopened (verify miss)

First implementation shipped `comm-kinds` reading only `core-index`, so user-loaded modules (e.g. discord on zanebot) did not appear. Worse, the core kinds it did surface — `acp`, `cli`, `hooks`, `memory`, `null` — are not user-pickable in a `:comms` slot. Result: the `options:` line was both incomplete and misleading.

### Additional scope

1. **Walk the full module index** (core + modules resolved from the loaded `:modules` config), not just core. The CLI may need to load the user config and discover modules before rendering — same discovery the validator already runs.
2. **Add `:configurable?` field on manifest `:comm` entries.** Defaults to `true`. Modules don't need to declare it.
3. **Mark internal core kinds as `:configurable? false`** in `src/isaac-manifest.edn`: `:acp`, `:cli`, `:hooks`, `:memory`, `:null`. These are not user-bindable comm slot types.
4. **Filter `comm-kinds` by `:configurable?`** before returning.
5. **Add new step `the stdout does not match:`** mirroring the existing `the stdout matches:` step, so the scenario can assert internal kinds are absent.

### Scenario revision

The `@wip` scenario in `features/config/schema_cli_options.feature` is rewritten to:
- Load the `isaac.comm.telly` module via `:modules`.
- Assert `options:.*telly` appears (module-provided, configurable).
- Assert `options:.*acp`, `options:.*cli`, `options:.*hooks`, `options:.*memory`, `options:.*null` do **not** appear.

### Acceptance

- `bb features features/config/schema_cli_options.feature` passes (remove `@wip` before merge).
- `bb isaac config schema comms.value.type` on a config with a comm-providing module installed lists that module's kinds, and does not list internal core kinds.
- `bb spec` and `bb features` stay green.

## Summary of Changes (Round 2 — Reopened)

- Updated `comm-kinds` in `src/isaac/module/loader.clj` to accept an optional module index, walk all modules (not just core), and filter out entries with `:configurable? false`
- Marked all five internal core comm kinds (`:acp`, `:cli`, `:hooks`, `:memory`, `:null`) as `:configurable? false` in `src/isaac-manifest.edn`
- Added `stdout-does-not-match` helper function and step registration in `spec/isaac/features/steps/cli.clj`
- Updated `cli/schema.clj` to load config and pass the full module index to the `comm-kinds` resolver, falling back to no-arg (core-only) when config is unavailable
- Removed `@wip` tag from `features/config/schema_cli_options.feature`
- Added 5 new spec examples for `comm-kinds` in `spec/isaac/module/loader_spec.clj`

All 1610 specs and 638 features pass.
