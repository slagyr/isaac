---
# isaac-rmc4
title: Wire compaction config-schema into crew schema
status: in-progress
type: feature
priority: normal
created_at: 2026-05-14T14:39:24Z
updated_at: 2026-05-15T03:11:53Z
---

Compaction is a crew-level feature. Its config schema exists (`src/isaac/session/compaction.clj:19-31`) but is only consumed locally by `resolve-config` — never wired into the main config schema. Result: putting `:compaction {...}` in a crew config triggers an unknown-key warning, and bad values (unknown `:strategy`, invalid types, head ≥ threshold) aren't surfaced at config load.

## Fix

Wire `compaction/config-schema` into the crew schema at `src/isaac/config/schema.clj:80`, alongside `:tools`:

```clojure
(:require [isaac.session.compaction :as compaction])

(def crew
  {:name   :crew
   :type   :map
   :schema {... existing fields ...
            :tools      tools
            :compaction compaction/config-schema}})
```

That's the meat. The schema itself already has:
- `:strategy` enumerated as `[:rubberband :slinky]`
- `:threshold` positive int
- `:head` positive int
- `:async?` boolean
- Cross-field rule: `head < threshold`

## Scope

- Crew-level only. No top-level or per-model `:compaction` until there's a real reason to promote it.
- Defaults stay in code (`compaction/default-threshold`, `default-head`, etc.) — schema doesn't duplicate them.
- The `:strategy` enum stays hard-coded (no `:strategy-exists?` validator) until a third strategy actually appears. YAGNI.

## New `@wip` scenario

- `features/context/compaction.feature:337` — Crew compaction config with unknown `:strategy` is rejected.

## Acceptance

- [x] `:compaction` added as a recognized field on the crew schema in `src/isaac/config/schema.clj`.
- [x] `compaction/config-schema` referenced (not duplicated) so changes flow from one source (extracted to `src/isaac/session/compaction_schema.clj`).
- [x] Crew configs setting `:compaction {...}` no longer log unknown-key warnings.
- [x] `@wip` scenario at `features/context/compaction.feature:337` passes after `@wip` removal.
- [x] Existing compaction scenarios continue to pass.
- [x] Run: `bb features features/context/compaction.feature` and the relevant spec suite.

## Out of scope (deferred)

- Promoting `:compaction` to top-level or per-model. Crew-only for now.
- Pluggable compaction strategies via a registry. Hard-coded enum for now.
- Runtime misconfig observability (ratcheting `:consecutive-failures`, silent token accumulation, etc.). Separate beans when needed.
- `:threshold` larger than the model's effective context window — a value-validity check that would require knowing which model the crew uses. Defer until the cross-field validation surface is clearer.

## Summary of Changes

- Extracted `config-schema` from `compaction.clj` into `src/isaac/session/compaction_schema.clj` to break the cyclic load dependency (`schema` → `compaction` → `store.file` → ... → `config.loader` → `schema`).
- `compaction.clj` re-exports `config-schema` as an alias to maintain the existing public API.
- Wired `:compaction {:type :map :schema compaction-schema/config-schema}` into the crew schema in `schema.clj`.
- Added `check-crew-compaction` in `loader.clj` (same pattern as `check-comms`) to validate `:strategy` against the known set `#{:rubberband :slinky}` at config-load time.
- Nil-guarded the `:*` cross-field validator in `compaction_schema.clj` so partial compaction configs (missing `:head` or `:threshold`) do not fail conformance and get stripped.
- 1625 specs, 0 failures; 13 compaction feature examples, 0 failures.



## Verification failed

`bb spec` passed (`1625 examples, 0 failures`) and `bb features features/context/compaction.feature` passed (`13 examples, 0 failures`). The feature-file change is authorized (`@wip` removal only), output was clean, and the timing gate passed.

I am reopening because this bean adds a new production namespace, `src/isaac/session/compaction_schema.clj`, but there is no corresponding `spec/isaac/session/compaction_schema_spec.clj`. Per this repo's testing discipline, every namespace in `src/` must have a corresponding spec in `spec/`, and a bean is not complete if new `src/` namespaces lack matching spec files. Please add a focused spec for `isaac.session.compaction-schema` (or otherwise eliminate the extra namespace) before re-handing off.
