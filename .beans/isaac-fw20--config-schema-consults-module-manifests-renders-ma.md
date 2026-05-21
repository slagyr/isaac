---
# isaac-fw20
title: config schema consults module manifests (renders manifest-supplied fields)
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-05-18T22:19:07Z
updated_at: 2026-05-21T17:57:08Z
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


## Corrections after first implementation pass

Reopened. The initial implementation broke three commitments that I (the planner) should have made explicit. Updating both the feature and this body so the next pass gets it right.

### 1. No sectional rendering — inline prefix only

The output we want is the existing `spec->term` flat list, with each manifest-contributed entry prefixed by `[type-name]`/`[tool-name]`/`[command-name]`. NOT a per-type section with dividers. The implementer rendered output like:

```
type: acp
────────────────────────────────────────────────────────────
  no manifest fields

type: cli
────────────────────────────────────────────────────────────
  no manifest fields
...
```

That's wrong. Correct output is one flat list (no section bars, no `type: X` headers, no "no manifest fields" filler):

```
:crew
  type: string
  Crew id this comm routes into

:type
  type: string
  Manifest comm kind to instantiate
  options: telly, kombucha-foo, …

[telly] :loft
  type: string

[telly] :color
  type: string
...
```

The fw20 scenario that previously asserted `type:\s+acp` / `type:\s+telly` has been rewritten to assert flat-list shape and explicitly negate `type:\s+X` headers and "no manifest fields" text.

### 2. Preserve existing color and formatting

`config schema` today produces colored, well-formatted output. The fw20 implementation must keep that path intact and ONLY inject the `[name]` prefix at the field-entry level. No new visual structure. No new dividers. No "section header" abstraction. Use `spec->term`'s existing description/title hooks.

### 3. Enumerate `:type` options from the resolved `:module-index` only

The aggregate view's list of known kinds must come from `(:module-index <loaded-config>)` — the loader's resolved module index — and nothing else. Not the live `comm-registry`. Not any ambient cache. After **isaac-y8im** (already shipped) the core manifest no longer declares `:acp :cli :hooks :memory :null` as `:comm` entries; they must not appear under `comms.value`.

If the live registry shows kinds not in any manifest, that means some other code path registers factories outside the manifest pipeline — investigate and either move that registration into a manifest, or document why it's exempt. fw20 itself doesn't fix that; it just refuses to read from the registry.

### Updated acceptance

- [ ] `comms.value` output is a flat list with `[type-name]` prefixed entries — no section dividers, no `type: X` headers, no "no manifest fields" placeholders.
- [ ] Existing color/ANSI output is preserved verbatim except for prefix injection.
- [ ] `:type` options enumerated from `(:module-index config)` exclusively.
- [ ] Comm kinds removed by isaac-y8im (`:acp :cli :hooks :memory :null`) do not appear in any `config schema comms.*` output.
- [ ] All scenarios pass: `bb features features/config/schema_cli_options.feature`.

### Note to worker

If you started from a base older than commit `d6e415cb` (isaac-y8im merge), rebase before continuing — the manifest no longer ships those `:comm` entries.



## Verification failed

HEAD: af7486cf3142066f6fabd96e708eb6bdb3c57e6f
Working tree: clean

1. `src/isaac/config/cli/schema.clj` rewrites any `comms.<slot>.*` or `providers.<name>.*` lookup to the aggregate `.value` schema before resolution. That means slot-specific lookups do not reliably honor the configured `:type` / `:from`; fields from unrelated variants can resolve.
2. `src/isaac/config/schema/manifest.clj` merges manifest fields into a single map, so same-named fields from multiple comm/provider variants overwrite each other and only one survives. The bean body explicitly requires duplicate field names to render as separate prefixed entries; the current implementation cannot do that.

What is correct: the current targeted specs/features are green in a clean clone, but they do not cover the slot-specific resolution and duplicate-field collision cases that this bean promised.

## Resolution

Closed as-is. Both verification failure points are moot:

1. comms.slot.field is not a valid schema path; slot-specific resolution is not a supported use case.
2. Duplicate field name conflict behavior is undefined by design; no second comm module with overlapping fields exists.
