---
# isaac-0jn6
title: Validate :extra-schema via the apron/isaac meta-schema (not :type :any)
status: completed
type: task
priority: normal
created_at: 2026-06-13T11:58:55Z
updated_at: 2026-06-13T12:38:42Z
parent: isaac-n0a1
---

Manifest TODO at resources/isaac-manifest.edn (line ~52): the comm
berth's entry schema types `:extra-schema` as `{:type :any}`. It
should be validated as a well-formed schema spec via the meta-schema
(isaac.schema.meta/conform-spec! / spec-schema), so a malformed
contributed `:extra-schema` is rejected at manifest validation with a
located error.

- [ ] Comm berth entry schema: `:extra-schema {:type :any}` ->
      meta-schema validation (reuse isaac.schema.meta — the same
      validator the :isaac.config/schema berth uses for inline
      `:schema`).
- [ ] Marigold themed comm berth follows.
- [ ] A malformed `:extra-schema` (e.g. unknown :type) produces a
      structured manifest validation error.

Touches the comm berth `:manifest` region — SERIALIZE with the
flatten bean (same region).

## Acceptance
- bb spec + bb features green.
- A spec/feature proves a bad `:extra-schema` is rejected with a
  located error.

## Summary
Added a `:schema-map` builtin lexicon type that validates a value as an apron schema (field keyword → spec) via the meta-schema. Implemented `meta/conform-schema!` + `valid-schema?`; lexicon holds a validator atom meta populates at load, with a lazy `requiring-resolve` trigger so `:schema-map` is self-sufficient regardless of load order (no meta→lexicon cycle). Comm berth `:extra-schema {:type :any}` → `{:type :schema-map}` (server + marigold). A malformed contributed `:extra-schema` (unknown :type) now produces a located manifest validation error (new berth_contributions scenario).
