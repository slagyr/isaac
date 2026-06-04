---
# isaac-2ecl
title: Schema :dynamic-schema directive (gather + merge from manifest contributions)
status: completed
type: feature
priority: normal
created_at: 2026-06-04T14:12:28Z
updated_at: 2026-06-04T18:51:11Z
parent: isaac-brth
blocked_by:
    - isaac-htkp
    - isaac-yb39
---

A new apron-shaped schema directive `:dynamic-schema` for `:map`
specs. Its value is a relative path into each contribution's
`:manifest` shape. At validation time, the foundation gathers the
value at that path from every contribution to the paired berth's
`:manifest`, merges them into the surrounding `:schema` map, and
validates the user config against the composed result.

## Shape

A map spec gains an optional `:dynamic-schema` field:

\`\`\`clojure
{:type :map
 :schema {:type {...} :crew {...}}
 :dynamic-schema [:value :extra-schema]
 :factory ...}
\`\`\`

`:dynamic-schema` value is a path **relative to each contribution's
:manifest entry** (NOT the berth's whole declaration — the
contribution itself, as seen by the foundation when it indexes
manifest nodes).

In the comm-berth example, the value `[:value :extra-schema]` means
\"for each contribution to `:isaac.server/comm`, look at the entry's
`:value :extra-schema` field.\" The foundation gathers every loaded
contribution's `:extra-schema` map and folds them into the
surrounding `:schema` map at validation time.

## Behavior

1. Validation walks the schema. On every `:map` node with
   `:dynamic-schema`, the foundation needs to know which berth this
   schema belongs to (the enclosing `:config`'s berth id).
2. Look up all manifest contributions to that berth.
3. For each contribution, follow the relative path to get a partial
   schema map.
4. Merge all gathered maps into the surrounding `:schema`.
5. Validate the user-config value against the composed schema.

## Collision policy

If two contributions both define the same field at the merge target
(e.g., both Discord and a hypothetical Discordian contribute
`:discord/token`), the foundation **errors at config-load**, naming
both contributors. Namespacing (`:require-namespaced-keys` on the
target) makes accidental collisions vanishingly rare; intentional
collisions are almost certainly bugs.

## Acceptance

No new Gherkin. Speclj at `spec/isaac/schema/dynamic_spec.clj` (or
similar):

- A schema with `:dynamic-schema [:value :foo]` and no contributions
  validates against the base schema only.
- With one contribution providing `:foo {:bar {:type :string}}`,
  validating against `{:bar 42}` errors (bar must be a string).
- With one contribution providing `:foo {:bar {:type :string}}`,
  validating against `{:bar \"hi\"}` succeeds.
- With two contributions providing disjoint `:foo` fields, validation
  composes both.
- With two contributions providing the same `:foo` field, config-load
  errors naming both contributors.
- Recursive walk: nested `:dynamic-schema` markers fire correctly at
  each depth.

Integration proof: bean I (config berth processing) uses this
directive end-to-end against the marigold.bridge/comm berth.

## Out of scope

- `:dynamic-schema` on non-`:map` specs. If we ever need it on `:seq`
  (e.g., a berth where contributions add elements to a vector), that's
  a separate later bean.
- Cross-berth gathering (one config's `:dynamic-schema` pulling from
  a different berth's contributions). The path is relative to the
  CURRENT berth's contributions only.
- Caching the merged schema across validation passes — a perf concern
  for later.

## Dependencies

- Blocked by isaac-htkp (need `:berths` shape parsed).
- Blocked by isaac-yb39 (need contributions indexed and reachable
  by berth-id for the gather step).
- NOT blocked by isaac-c2g5 or isaac-2yqb — the directive operates
  on values, not on types-in-the-lexicon.

## Notes for the worker

- The walk needs to KNOW which berth's contributions to gather from.
  Cleanest path: the enclosing `:config` always lives inside a berth
  declaration; the foundation walks schema with the berth-id in its
  context. The `:dynamic-schema` directive does NOT name the berth
  itself — it's inferred from the enclosing berth.
- Merge order matters for error messages but NOT for semantics
  (collisions are errors). Sort contributions by `:id` for stable
  output.
- The directive is data-only (no symbols, no code references). It
  fits the same constraint as the validation vocabulary: pure data,
  no module-shipped code.
