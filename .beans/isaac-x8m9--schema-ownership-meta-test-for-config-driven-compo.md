---
# isaac-x8m9
title: Schema-ownership meta-test for config-driven components
status: todo
type: task
priority: high
created_at: 2026-05-16T19:01:50Z
updated_at: 2026-05-16T19:04:23Z
blocked_by:
    - isaac-3fuy
---

## Problem

Today nothing enforces that a new top-level config key has a lifecycle owner. The hooks bug we hit on 2026-05-16 was exactly this: `:hooks` had a `HooksModule` deftype implementing `Reconfigurable`, but the framework's registry was hardcoded to `[:comms]`, so HooksModule was never invoked through `reconcile!`. The parallel `reconcile-config-hooks!` pathway was a workaround that "did something" without satisfying the lifecycle contract.

We want CI to fail when someone adds a new entity-collection key to the schema without declaring how it's lifecycled.

## Design

A spec test that introspects `isaac.config.schema/root` and the generalized registry from isaac-3fuy, then asserts:

> Every entity-collection key in the schema is either
>
> 1. **Owned by a registered Reconfigurable component** (path appears in the generalized registry), OR
> 2. **Explicitly tagged `:snapshot-only? true`** in its schema entry, meaning "read fresh on demand, no lifecycle needed"
>
> Anything else fails the test.

Adding a new key like `:foo` to the schema forces a deliberate choice: implement a Reconfigurable component for it, or mark it `:snapshot-only? true`. No third option.

### What counts as an "entity-collection key"

Schema entries where the value type is a map of `id → entity` (e.g. `:crew {:main {...}}`, `:hooks {:heartbeat {...}}`). Scalars (`:dev`, `:tz`) and singleton-config blobs (`:server`, `:gateway`) are out of scope — they don't have lifecycle implications.

The test should walk the schema and identify entity-collections by their shape (presence of `:key-spec`/`:value-spec` on the schema node, per the existing patterns at `src/isaac/config/schema.clj:218-273`).

### Initial categorization (the worker fills this in)

When this bean is worked, the implementer must categorize every existing entity-collection. Initial guess:

| Key | Category | Notes |
|---|---|---|
| `:comms` | owned (`reconcile!`) | existing comm tree |
| `:hooks` | owned (HooksModule, post-3fuy) | after isaac-3fuy lands |
| `:cron` | owned (cron module, post-3fuy) | after isaac-3fuy lands |
| `:crew` | `:snapshot-only? true` | read fresh per turn via `resolve-crew-context` |
| `:models` | `:snapshot-only? true` | read fresh per turn |
| `:providers` | `:snapshot-only? true` | read fresh per turn |
| `:tools` (from manifest) | n/a | manifest, not user config |
| `:slash-commands` | n/a | manifest, not user config |

Confirm or correct during implementation.

## Acceptance

- [ ] New spec at `spec/isaac/configurator_spec.clj` (or `spec/isaac/config/ownership_spec.clj`) asserts the property
- [ ] Each schema entry for an entity-collection either appears in the generalized registry or has `:snapshot-only? true`
- [ ] Test name and failure message guide the author: "key `:X` has no owner — register a Reconfigurable for `[:X]` or add `:snapshot-only? true` to its schema entry"
- [ ] Schema entries for `:crew`, `:models`, `:providers` tagged `:snapshot-only? true`
- [ ] `bb spec` — green

## Out of scope

- Gherkin scenarios — this is a property of the codebase, not user-facing behavior. The clojure spec test IS the contract. (Per plan-with-features: when scenarios are awkward and one-line spec assertions are direct, the spec wins.)
- Migrating snapshot-only consumers to use Reconfigurable callbacks — they work fine via fresh reads; the tag just makes the categorization explicit.

## Sequencing

**Blocked-by isaac-3fuy.** The generalized registry must exist for the test to introspect. Promote this bean from `draft` → `todo` once 3fuy lands.

## Related

- **isaac-3fuy** — generalizes the registry this test introspects
- Conversation 2026-05-16 audit: identified hooks and cron as the silently-broken consumers this meta-test would have caught
