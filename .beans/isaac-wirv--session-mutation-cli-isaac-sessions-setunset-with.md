---
# isaac-wirv
title: 'Session mutation CLI: isaac sessions set/unset with schema validation'
status: todo
type: feature
priority: normal
created_at: 2026-05-23T03:03:06Z
updated_at: 2026-05-23T05:17:14Z
blocked_by:
    - isaac-wr7d
    - isaac-x0b5
    - isaac-5qti
---

## Motivation

Session records carry many mutable fields — crew assignment, model,
context-mode, channel, compaction-disabled, and (per the tagging
bean isaac-wr7d) tags. Today these can only be edited by Isaac's
internal code paths; there is no operator-facing way to change a
session's crew or add a tag after creation.

isaac-wr7d (tagging) deferred post-creation session tag mutation
here because `isaac sessions set <id>.tags.wip` is really a general
session-record mutation surface, of which tag mutation is one
application. This bean adds that surface, with schema-driven
validation so any field change is checked against a single source
of truth.

## Scope

### Schema mutability metadata

`isaac.session.schema/Session` already declares every field's type
via c3kit.apron.schema. Extend each field with operator-mutability
metadata so the CLI can refuse changes to read-only and
system-managed fields:

```clojure
{:id         {:type :string :required? true :mutable? false}
 :name       {:type :string :mutable? true}
 :crew       {:type :string :mutable? true
              :validate crew-exists?}
 :tags       {:type :set :member-type :keyword :mutable? true}
 :created-at {:type :string :mutable? false}
 :input-tokens {:type :int :mutable? false}   ;; system-managed
 ...}
```

Three categories that fall out:

- **Operator-mutable** — `:crew`, `:model`, `:tags`,
  `:compaction-disabled`, etc.
- **Immutable** — `:id`, `:created-at`, `:sessionId`.
- **System-managed** — `:updated-at`, `:compaction-count`,
  `:input-tokens`, token counters. Isaac writes these; operators
  can't.

Field-level `:validate` catches semantic errors (setting `:crew` to
a name no crew has, setting `:tags` to non-keyword values, etc.).

### CLI surface

```
isaac sessions set   <id>.<key> [<value>]
isaac sessions unset <id>.<key>
```

Path parsing:

- Top-level: `<id>.<key>` → modify `session[:key]`.
- Set member: `<id>.tags.<keyword>` → add to / remove from the
  `:tags` set. The portion after `tags.` parses as a single
  keyword (so `tags.role/worker` → `:role/worker`).
- (Future) Map member: `<id>.<map-field>.<key>` — add if a use
  case arises; not in v1.

### Validation

On every set/unset:

- Parse the path against the schema.
- Reject unknown fields with a clear error ("no such field").
- Reject mutations to non-operator-mutable fields (immutable or
  system-managed) with a clear error.
- Coerce the value to the field's declared type, or error.
- Run field-level `:validate` (e.g., crew-exists?).
- All errors include the offending path and a hint.

### Idempotency and timestamps

- Setting an already-present value: no-op success.
- Unsetting an absent value: no-op success.
- Successful mutations bump `:updated-at` to current time.
- Failed mutations write nothing.

## Out of scope

- **Bulk mutation** across many sessions.
- **Map-field path syntax** beyond set members; add when a real
  use case arises.
- **`--json` patch mode** (`isaac sessions patch <id> '{...}'`) —
  additive later if scripting demands.
- **Schema migration tooling** for older session records.

## Acceptance

**Schema:**

- `isaac.session.schema/Session` gains operator-mutability metadata
  on each field (`:mutable?` or equivalent), and the meta-test for
  schema ownership passes.
- The `:tags` field is added per isaac-wr7d's spec.

**CLI:**

- `isaac sessions set <id>.tags.<keyword>` adds a tag.
- `isaac sessions unset <id>.tags.<keyword>` removes a tag.
- `isaac sessions set <id>.crew <crew-name>` reassigns crew,
  validating that the target crew exists.
- `isaac sessions set <id>.id <new-id>` errors with "immutable
  field."
- `isaac sessions set <id>.created-at <ts>` errors with "immutable
  field."
- `isaac sessions set <id>.input-tokens <n>` errors with
  "system-managed field."
- `isaac sessions set <id>.unknown-key <v>` errors with "no such
  field."
- `isaac sessions set <id>.crew nonexistent-crew` errors with
  "crew does not exist."
- `isaac sessions set <id>.tags.role/worker` correctly stores
  `:role/worker` (namespaced) via the dotted path.
- All operations are idempotent (set-equal / unset-absent are no-op
  successes).
- Successful mutations bump `:updated-at`.

**Feature scenarios** under `features/session/mutation.feature`
cover the cases above.

## Feature files

- `features/session/mutation.feature` — 10 scenarios: tag
  add/remove, scalar reassignment (`crew`) with cross-field
  validation, rejection of immutable / system-managed / unknown
  fields, set/unset idempotency, `:updated-at` bump on successful
  mutations.

The file carries `@wip` at the top — scenarios are excluded from
the default `bb features` and `bb ci` runs until the implementer
removes the tag.

Run targeted: `bb features features/session/mutation.feature`.

**Definition of done:** remove `@wip` from
`features/session/mutation.feature` and
`bb features features/session/mutation.feature` is green.

## Relationship to other beans

- **Blocked by isaac-wr7d (Tagging).** The `:tags` field shape and
  semantics come from there; both beans must agree on tag-set
  vocabulary.
- **Blocked by isaac-x0b5 (Step infrastructure).** Mutation
  scenarios assert via `the stdout JSON contains:` which lands with
  x0b5.
- **Blocked by isaac-5qti (Schema-aware path navigator).** The
  config-side mutation walker likely extracts into a shared
  abstraction that wirv reuses for session-side mutation. Cleaner
  than parallel implementations.
- **Supersedes the session-tag-mutation deferral in isaac-wr7d.**
  Once this bean ships, sessions can be tagged post-creation.
- **Companion to isaac-a1nu (Crew concurrency).** Sessions CLI
  surface grows here; doesn't conflict with the listing/show
  surface a1nu touches.
