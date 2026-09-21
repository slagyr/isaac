---
# isaac-7fab
title: Scaffold every default into the config on disk
status: todo
type: feature
priority: normal
created_at: 2026-09-21T16:22:55Z
updated_at: 2026-09-21T16:22:55Z
---

## Why (Micah, 2026-09-21)

Isaac's built-in defaults are hard to see and feel arbitrary (the four
tool-discipline rules are the example that started this). Keeping built-in
values is fine, but each one should be **visible on disk and easy to
override**. Every default gets scaffolded into the config.

## Today

`isaac init` (`isaac-foundation/src/isaac/cli/registry.clj:140`, `scaffold!`)
writes a minimal config: `isaac.edn` with `:defaults {:crew :skipper :model
:llama}`, a crew, a model, a provider and a heartbeat cron. Most defaults
(effort, compaction, cycle, retry timings, tool output caps, the
tool-discipline hint) exist only in code or in schema `:default` values.

## Design

1. **One source of truth: the schema's `:default` values.** Many fields
   already carry one (e.g. `:max-parallel :default 4`,
   `:provider-retry-after-ms :default 1800000`). Scaffolding writes those
   values out. It is not a second, hand-kept copy that can drift from the
   code. Fields whose default lives only in code get a schema `:default` as
   part of this bean.
2. **Scaffold the full `:defaults` block** in isaac-ruom's structure, every
   field present with its built-in value.
3. **Large defaults are scaffolded as files.** The tool-discipline text
   (isaac-5n68) is written to `config/prompts/tool-discipline.md`, and
   `:defaults :model :extra-system-prompt` points at it with
   `"${file:prompts/tool-discipline.md}"` (isaac-jl9p). The built-in value
   ships as a resource, so a missing reference still has a fallback.
4. **Each module scaffolds its own defaults.** `isaac init` lives in
   foundation, but most of these defaults belong to isaac-agent. Foundation
   should not learn crew or model names to write them (the isaac-1pi2 /
   isaac-bbe0 rule). Modules contribute their scaffold through a berth, the
   same way they declare config components.

## Trade-off to state in the docs

Once a default is scaffolded, the install owns that copy. When a later Isaac
release improves a built-in default, installs that scaffolded the old value
keep it. That's the point (nothing changes underneath you), but release notes
must call out changed defaults so owners can pull them in.

## Existing installs

Covered by isaac-ruom's hand migration: write the full structure and the
prompt file. Whether to also add a command that fills in *missing* defaults
without touching set values (e.g. `isaac config scaffold`) is open. It would
be a feature, not legacy support.

## Done when

- `isaac init` writes every `:defaults` field with its schema default, and the
  tool-discipline prompt file (specs)
- the scaffolded config validates and resolves the same effective values as an
  unscaffolded one (spec: scaffolded vs empty `:defaults` give identical
  resolution)
- each default's value comes from its schema `:default`, and no value is
  duplicated in scaffolding code
- modules contribute their own scaffold; foundation writes nothing it doesn't
  own
- `bb verify` and `bb jvm-spec` are both green
