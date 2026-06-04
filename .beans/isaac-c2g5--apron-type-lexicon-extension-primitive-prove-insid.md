---
# isaac-c2g5
title: Apron type lexicon extension primitive (prove inside isaac before upstreaming)
status: todo
type: feature
priority: normal
created_at: 2026-06-04T10:52:15Z
updated_at: 2026-06-04T10:52:25Z
parent: isaac-brth
---

Apron's `type-validators` and `type-coercers` are top-level `def`s
in `c3kit.apron.schema` — not multimethods or atoms — so the lookup
isn't extensible from outside. We need a way to register new schema
types from inside isaac without forking apron first. Once the
mechanism is stable here, contribute it back to apron.

## Surface

A new namespace, probably `isaac.schema.lexicon` (name negotiable),
that exposes:

- `register-type!` — registers a `{:name :validate :coerce? :message?}`
  entry. Validator is the predicate apron-style validators expect.
  Coercer is optional (most isaac-side types want symbols-in, not
  strings-in).
- A conform/coerce surface that supports both apron's built-in
  types AND lexicon-registered types. Likely a thin wrapper around
  `apron.schema/conform!` that walks the schema, detects unknown
  types, and dispatches through the lexicon before delegating to
  apron.

First type registered:

- `:symbol` — `validate symbol?`, no coercion. Lets manifest schemas
  declare `:factory {:type :symbol …}` cleanly instead of working
  around with `:type :any :validations [[:symbol?]]`.

## Acceptance

No new Gherkin feature file. Acceptance is speclj-level + the
integration scenario in isaac-htkp.

Speclj at `spec/isaac/schema/lexicon_spec.clj` (or wherever fits):

- Registering a new type makes it discoverable by the
  conform/coerce surface.
- A schema with `{:type :symbol}` and a symbol value conforms
  successfully.
- A schema with `{:type :symbol}` and a string value (or anything
  non-symbol) returns a clear validation error.
- A schema referencing an UN-registered type returns a clear error
  naming the type (not apron's bare "unhandled validation type").
- Clearing the registry between examples returns to baseline (no
  test leak).

Integration proof: `isaac-htkp`'s scenario "A manifest's top-level
`:factory` must be a symbol" passes once isaac-htkp's loader uses
this lexicon to validate the manifest.

## Out of scope

- Forking apron or opening an upstream PR. That happens once
  the mechanism is stable and we've shaken out the shape from real
  use. File the upstreaming as a separate task when ready.
- Any registered types other than `:symbol`. Those land in their
  own beans as the foundation needs them.
- Schema-merging via `:dynamic-schema`. Separate later bean.

## Notes for the worker

- Apron source (relevant pieces): `c3kit.apron.schema/type-validators`,
  `type-coercers`, `type-validator!`, `type-coercer!`. Today both
  lookups throw if the type isn't found. The cleanest wrapper
  catches that throw OR pre-checks the lexicon before calling apron.
- Keep the lexicon's registry side-effects test-friendly: provide
  a `clear!` (or `with-types` macro) so specs don't leak
  registrations across examples.
- Symbol validation should NOT accept strings, keywords, or vars
  — only `clojure.lang.Symbol` / equivalent (`symbol?`). Manifests
  in EDN already parse symbols literally; coercion would just hide
  bugs.
