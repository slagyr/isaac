---
# isaac-fw20
title: config schema consults module manifests (renders manifest-supplied fields)
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-05-18T22:19:07Z
updated_at: 2026-05-19T00:01:24Z
blocked_by:
    - isaac-4cao
---

## Goal

`isaac config schema <path>` becomes manifest-aware. Today it consults only the static schema in `src/isaac/config/schema.clj`, so paths like `config schema comms.value.loft` return "Path not found" even when the user has declared the telly module. After this bean, schema rendering walks each declared module's manifest and surfaces its contributed fields with an automatic `[type-name]` / `[tool-name]` / `[command-name]` prefix in the field's description.

## Behavior

- `config schema comms.value` — renders base (`:type`, `:crew`) plus every known comm `:type` variant. Each manifest-supplied field gets a `[type-name]` description prefix.
- `config schema comms.value.<field>` — resolves `<field>` across every known `:type` variant. When two variants happen to declare the same field, both render as separate entries distinguished by their prefix.
- `config schema comms.<slot>` — if `:comms <slot> :type` is set in config, resolves to that one variant's merged schema; otherwise falls back to the aggregate view.
- Same shape for `:providers.value`, `:tools.<name>`, `:slash-commands.<name>`.
- Without modules declared, only the base schema renders (silent — no hint).
- A manifest-supplied path with the module not declared → "Path not found" stderr, exit 1.
- The `[type-name]` prefix is auto-applied by isaac; module authors do NOT write it into their manifest's `:description`.

## Implementation hooks

- `src/isaac/config/cli/schema.clj` (`print-schema!`) currently calls `config-schema/schema-for-path`. Extend it to read `:modules` from the config (it already loads enough config to resolve `:options-resolvers` for `:type`), build the same `:module-index` the loader uses, and pass it to the rendering layer.
- `src/isaac/config/schema/term.clj` (`spec->term`) gains a manifest-index input. When walking under one of the four extensible surfaces, it merges manifest schema fields into the rendered output and wraps each entry's description with `[provenance] ` prefix.
- Reuse `module-loader/comm-kinds` / equivalent surfaces to enumerate available kinds — already proven for `:options-resolvers`.

## Depends on

- **isaac-4cao** (B3) — must land first so manifest field schemas are uniform apron shapes across all four surfaces. Rendering then has one shape to interpret.

## Feature

`features/config/schema_cli_options.feature` — header gains a paragraph describing the manifest-aware policy and the provenance-prefix convention. Seven new `@wip` scenarios cover:

1. Manifest-supplied comm field renders with `[telly]` prefix.
2. `comms.value` lists every known `:type` variant with prefixed module fields.
3. `comms.value` with no modules declared shows only base fields.
4. `comms.value.<unknown-when-not-declared>` errors with "Path not found".
5. Provider field renders with `[kombucha]` prefix.
6. Tool field renders with `[web_search]` prefix (core manifest, no module needed).
7. Slash-command field renders with `[echo]` prefix.

## Acceptance

- [ ] `print-schema!` reads `:modules`, builds module-index, passes to renderer.
- [ ] `term/spec->term` accepts module-index and auto-prefixes descriptions.
- [ ] All seven `@wip` scenarios pass; tags removed.
- [ ] Existing `comm slot :type lists user-configurable comm kinds from manifests` scenario continues to pass.
- [ ] Run: `bb features features/config/schema_cli_options.feature`

## Related

- **isaac-4cao** (blocker) — uniform manifest schemas.
- See also B4/B5 (separate beans) for related manifest-validation concerns surfaced during design (duplicate tool/slash-command names; base-key shadowing).
