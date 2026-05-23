---
# isaac-5qti
title: 'Schema-aware path navigator: isaac config set/unset with apron.schema awareness'
status: in-progress
type: feature
priority: normal
created_at: 2026-05-23T04:53:23Z
updated_at: 2026-05-23T05:11:34Z
---

## Motivation

`isaac config set crew.joe.tags.wip` needs to walk the dotted path
against the config schema to make sense of the leaf — `tags` is a
set (per the tagging bean's schema), so `wip` is interpreted as a
set member to add, not as a nested map key.

Naïve dotted-path navigation (e.g., `clojure.core/get-in` over a
parsed path) doesn't know about set membership or other
type-specific semantics. The schema knows. We need a layer that
consults the schema while walking.

This bean adds that layer, implemented in Isaac on top of
`c3kit.apron.schema`. Upstreaming to apron is possible later once
the API shape is proven.

## Scope

### Path parser

Parse dotted-path strings into a sequence of access steps. Splits
on `.`; preserves segments that include `/` (for namespaced
keywords). Examples:

- `crew.joe.tags.wip` → `["crew" "joe" "tags" "wip"]`
- `crew.joe.tags.role/worker` → `["crew" "joe" "tags" "role/worker"]`
- `session.abc.compaction.threshold` → `["session" "abc" "compaction" "threshold"]`

### Schema-aware walker

Walks the path against the configured schema; at each step the
schema declares the type at that point, which determines how the
next segment is interpreted:

| Schema type at current node | Next segment interpreted as |
|-----------------------------|------------------------------|
| Map                         | Keyword key (string segment → `:keyword`) |
| Vector                      | Integer index                |
| Set                         | Set member (string segment → `:keyword`); terminal |

When a Set is reached, the next (and final) segment is treated as
the set's member — the value the operator wants to add/remove.

### `set!` and `unset!` operations

Public functions:

- `(set! schema config path)` — produces a new config with `path`
  populated. For a set-typed leaf, adds the member (path's terminal
  segment); for a scalar, sets the value (needs a value arg);
  idempotent.
- `(unset! schema config path)` — produces a new config with `path`
  cleared. For a set, removes the member; for a scalar, removes the
  key; idempotent.

Both return `{:ok? true :new-config <m>}` on success or
`{:ok? false :error <reason> :hint <hint>}` on failure (path
doesn't match schema, terminal type doesn't support the operation,
etc.).

### CLI wiring

Two new subcommands on `isaac config`:

```
isaac config set   <path> [<value>]
isaac config unset <path>
```

- `set` reads the value argument as text and coerces against the
  leaf type per the schema. For set-typed leaves (where the path
  terminates in a set), the value is implied by the final path
  segment — no separate value arg needed
  (`isaac config set crew.joe.tags.wip` adds `:wip`).
- `unset` doesn't take a value.
- Both persist the updated config back to its source file.
- Idempotent: re-running succeeds without error and without
  changing state.

## Out of scope (deferred)

- **Upstreaming to apron.schema.** Once the API stabilizes, propose
  adding it to apron. v1 lives in Isaac.
- **Non-config stores.** This bean targets `~/.isaac/config/`.
  Session-store mutation (`isaac sessions set/unset`) is
  isaac-wirv's territory — that bean reuses path-parsing but has
  its own schema (session metadata) and persistence path.
- **Multi-file source resolution.** If config is composed from
  multiple files, the writer needs to know which file to mutate.
  v1 may use a simple rule (mutate the most-specific file for the
  path's prefix); refine if real cases break.

## Acceptance

- A schema-aware walker function exists, takes `(schema, config,
  path-string)`, returns the type at the path's terminus or an
  error.
- `set!` and `unset!` functions exist; both return success/error
  maps with clear hints on failure.
- Set-typed leaves: `set!` adds a member, `unset!` removes one;
  both idempotent.
- Scalar leaves: `set!` writes the value (coerced per schema),
  `unset!` removes the key; both idempotent.
- `isaac config set <path>` and `isaac config unset <path>` CLI
  subcommands wired up.
- Path-parsing preserves namespaced keyword segments
  (`tags.role/worker` works).
- Feature scenarios under `features/config/set_unset.feature`
  cover: scalar set/unset, idempotency (set-present and
  unset-absent), error on unknown path, error on bad value type.
- Set-typed mutation and namespaced-keyword path parsing are
  exercised through `features/tagging/crew_tags.feature` scenarios
  13–16 (which use `config set crew.joe.tags.role/worker` and
  similar); no duplicate coverage needed here.
- Library-level functions (walker, `set!`, `unset!`) covered by
  Speclj unit specs alongside the implementation.

## Feature files

- `features/config/set_unset.feature` — 6 scenarios: scalar
  set/unset, idempotency, errors (unknown path, bad value type).

The file carries `@wip` at the top — scenarios are excluded from
the default `bb features` and `bb ci` runs until the implementer
removes the tag.

Run targeted: `bb features features/config/set_unset.feature`.

**Definition of done:** remove `@wip` from
`features/config/set_unset.feature` and
`bb features features/config/set_unset.feature` is green.

## Relationship to other beans

- **Required by isaac-wr7d (Tagging).** The crew mutation surface
  (`isaac config set/unset crew.<name>.tags.<keyword>`) needs
  schema-aware path interpretation to treat `tags.<keyword>` as a
  set member.
- **Companion to isaac-wirv (Session mutation CLI).** isaac-wirv
  reuses the path parser and walker concepts for
  `isaac sessions set/unset`, but against the session schema and
  store. Some abstraction may emerge.
- **Independent of isaac-ugx7 (Hail).** Hail's matcher reads tags
  but doesn't mutate config.
