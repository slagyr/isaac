---
# isaac-fzrx
title: Schema CLI renders allowed values; comm :type lists registered comm kinds
status: completed
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-05-18T19:05:47Z
updated_at: 2026-05-18T20:17:13Z
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
