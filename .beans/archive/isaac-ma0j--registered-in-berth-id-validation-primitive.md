---
# isaac-ma0j
title: '[:registered-in? :berth-id] validation primitive'
status: completed
type: feature
priority: normal
created_at: 2026-06-04T14:14:00Z
updated_at: 2026-06-04T18:42:14Z
parent: isaac-brth
blocked_by:
    - isaac-htkp
    - isaac-yb39
---

A foundation-provided, data-only validation primitive that asserts a
value is the id of some registered contribution under a named berth.
Used by config schemas to constrain discriminator fields (most
notably the `:type` key in user config slots).

## Usage

\`\`\`clojure
;; In a config berth's value-spec schema:
:type {:type        :keyword
       :validations [:present?
                     [:registered-in? :isaac.server/comm]]}
\`\`\`

When validation runs, the foundation looks up loaded contributions
to `:isaac.server/comm`, gets the set of registered ids, and checks
whether the value is one of them.

## Behavior

1. Validator is parametrized by a berth id (namespaced keyword).
2. At validation time, the foundation resolves the berth's
   contributions across all loaded modules and computes the set of
   registered ids.
3. Value present in that set ⇒ pass.
4. Value not in the set ⇒ fail with a clear message naming the
   accepted ids (or noting that no contributions exist if empty).
5. Berth id doesn't correspond to a declared berth in any loaded
   manifest ⇒ fail with a distinct \"unknown berth\" message — this
   is a configuration error in the BERTH SCHEMA itself (the schema
   author named a nonexistent berth), not in the user config.

## Acceptance

No new Gherkin. Speclj at
`spec/isaac/schema/registered_in_spec.clj` (or similar):

- Validating against a berth with one contribution accepts that
  contribution's id and rejects other values.
- Validating against a berth with multiple contributions accepts any
  registered id.
- Validating against a berth with no contributions always fails
  with a useful message (\"no registered impls\").
- Validating against a berth that doesn't exist in the module-index
  fails with a clear \"unknown berth\" message.
- Error messages list the accepted ids when the set is small (≤ N;
  pick N during impl); otherwise omit the list and just name the
  berth.

Integration validation lands in bean I (config berth processing)
where a user's `:type` field gets validated against the
`:registered-in?` primitive in the marigold.bridge/comm schema.

## Out of scope

- Caching the resolved set across validation passes. Perf concern
  for later.
- Cross-module-load incremental updates (recompute the set if a
  module is dynamically added after initial config-load). Not a
  thing isaac supports today; skip.
- Existence validators for OTHER source types (`:config-slice-keys`,
  etc. — the brth bean mentioned a small vocabulary of source
  shapes). Land as needed.

## Dependencies

- Blocked by isaac-htkp (need `:berths` data parsed).
- Blocked by isaac-yb39 (need contributions indexed so the validator
  can resolve them by berth-id).

## Notes for the worker

- This validator is the data-only counterpart to the multimethod-
  dispatch pattern from the design phase. The validator gates the
  user's input; the multimethod then trusts the type was already
  validated.
- The validator's parameter (`:isaac.server/comm`) is a literal
  keyword — no symbol resolution, no namespace loading. Stays in
  the closed-vocabulary discipline.
- Make the validator composable with other validations.
  `[:present? [:registered-in? :foo]]` should work as a normal apron
  validation chain.

## Summary of Changes

New `isaac.schema.registered-in` namespace:

- `registered-in?` validation **factory** — vector form `[:registered-in? :berth-id]` resolves at apron load time via the `:validations` lex.
- Reads the live module-index from a dynvar `*module-index*` (nil ⇒ treated as empty).
- Distinct failure messages:
  - **unknown berth** — `unknown berth: :foo/bar` (the schema author named a berth no installed module declares).
  - **empty contribution set** — `no registered impls for berth :foo/bar`.
  - **bad value, small set (≤ 5)** — `must be one of [:a :b :c]` (sorted for stability).
  - **bad value, large set** — `must be a registered contribution to :foo/bar`.
- Wired into apron's `:validations` lexicon at load time via `schema/update-lexicon!`, so schemas can use it without per-call setup.

Loader wiring (`isaac.module.loader`):

- `validate-contributions!` now binds `registered-in/*module-index*` to the current module-index. Any berth schema using `[:registered-in? ...]` automatically resolves against the loaded set during contribution validation; no per-caller boilerplate.

Spec coverage (`spec/isaac/schema/registered_in_spec.clj`, 8 examples):

- Single contribution: accepts the lone id; rejects others.
- Multiple contributions: accepts any registered id.
- Empty contribution set: `no registered impls`.
- Unknown berth: `unknown berth`.
- Small set lists accepted ids; large set just names the berth.
- nil `*module-index*` treated as empty (still produces `unknown berth`).
- Composes with `:present?` in a single validations chain.

bb spec 1833/0; bb features 738/0.
