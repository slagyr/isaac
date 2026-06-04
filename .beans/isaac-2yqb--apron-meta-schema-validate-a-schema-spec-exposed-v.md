---
# isaac-2yqb
title: Apron meta-schema (validate-a-schema-spec) exposed via isaac wrapper
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-04T10:57:32Z
updated_at: 2026-06-04T19:03:36Z
parent: isaac-brth
blocked_by:
    - isaac-c2g5
---

Apron has no meta-schema today — no way to ask "is this thing a
valid apron schema spec?" The berth model needs that to validate
contributions: a manifest node's `:extra-schema` is a map of field
specs, and each value must be a well-formed apron-shaped spec. Bean
B (isaac-c2g5) creates the lexicon-extension primitive; bean C
builds the meta-schema on top of it.

## Surface

A schema spec (data, not code) named at an isaac-owned symbol —
candidate `isaac.schema.meta/spec-schema` — that validates an
arbitrary value is a valid apron-shaped schema spec.

Recognized field-level keys (from apron's docstring at the top of
`c3kit.apron.schema`):

- `:type` (required) — keyword; must be a known type. \"Known\" means
  one of apron's built-ins OR a type registered via isaac-c2g5's
  lexicon. The meta-schema lookup goes through the lexicon, NOT
  through a hard-coded list.
- `:coerce` — fn or seq of fns (symbol or fn value).
- `:validate` — fn or seq of fns (symbol or fn value).
- `:validations` — seq of `{:validate fn :message string}` maps OR
  seq of `[:validator-keyword args...]` shortcut vectors.
- `:message` — string.
- `:default` — any.
- `:description` — string.
- `:present` — fn or seq of fns.
- `:db` — any (passed through to consumers).

Container types add more keys:

- `:type :map`  → `:schema {kw -> spec}` OR `:key-spec spec` /
  `:value-spec spec` for open maps. Recursive: each leaf spec is
  itself meta-schema-valid.
- `:type :seq`  → `:spec spec` (single item shape; recursive).

The meta-schema MUST be recursive — a `:type :map :schema {...}`
spec validates each value in `:schema` as a meta-schema-valid spec.

## Acceptance

No new Gherkin. Speclj-only at
`spec/isaac/schema/meta_spec.clj` (or similar):

- A minimal valid spec like `{:type :string}` conforms.
- `{:type :map :schema {:name {:type :string}}}` conforms (recursive).
- `{:type :seq :spec {:type :keyword}}` conforms (recursive).
- An unknown `:type` (not in apron's built-ins, not lexicon-registered)
  fails with a clear error.
- A missing `:type` fails with a clear error.
- A `:schema` whose values aren't themselves valid specs fails,
  pointing at the offending key.
- Once the lexicon has `:symbol` registered (per isaac-c2g5),
  `{:type :symbol}` conforms.

Integration proof will land later: isaac-htkp's follow-up bean
(contribution validation against berth `:manifest :schema`) will
use this meta-schema to validate `:extra-schema` entries from
contributing modules.

## Out of scope

- Upstreaming to apron. Same \"prove inside isaac first\" frame as
  isaac-c2g5. File the upstream task separately when stable.
- `:dynamic-schema` resolution (separate later bean).
- Validating contributions against the meta-schema (separate later
  bean — contribution validation).

## Dependencies

- Blocked by isaac-c2g5 (lexicon). The meta-schema's
  `:type`-must-be-known check goes through the lexicon, so the
  lexicon needs a public way to ask \"is `:foo` a registered type?\".

## Notes for the worker

- The meta-schema is data, so it can be written as an EDN literal at
  the top of the isaac namespace. It IS self-validating in principle —
  a nice integration test is \"the meta-schema spec validates against
  itself.\"
- Apron's `corec` and `schema` namespaces have helpers worth
  reading first (`present?`, `nil-or`, etc.) so the meta-schema
  shells out to existing apron primitives rather than reinventing.
- Until upstreamed, manifests refer to the meta-schema via the
  isaac-owned symbol. The artifact at tmp/isaac-server-manifest.edn
  currently writes `c3kit.apron.schema/spec-schema` as a
  forward-looking placeholder; rename to the isaac-owned path once
  this bean lands so manifests can actually resolve it.
