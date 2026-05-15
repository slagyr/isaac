---
# isaac-32xb
title: Upgrade apron to 2.8.0 and adopt :present-when? entity-scoped ref
status: todo
type: feature
created_at: 2026-05-15T04:14:15Z
updated_at: 2026-05-15T04:14:15Z
---

Upgrade `com.cleancoders.c3kit/apron` from 2.7.0 to 2.8.0 and adopt the new entity-scoped ref feature to express "field X is required when sibling field Y is Z" inline on the field, instead of lifting cross-field rules into the `:*` synthetic key.

## What 2.8.0 brings

A registered ref can declare `:scope :entity`. Its `:validate` / `:coerce` then receives `(entity field-key)` instead of `(value)` and runs after the field-level pass — letting cross-field rules live next to the field they constrain. Error key-paths land on the actual field, not on a `:*` synthetic.

## What this bean introduces

Register `:present-when?` in `isaac.config.loader` alongside the existing existence-refs:

```clojure
(cs/register-ref! :present-when?
  (fn [other-key expected]
    {:validate (fn [entity field-key]
                 (or (not= expected (get entity other-key))
                     (cs/present? (get entity field-key))))
     :scope    :entity
     :message  (str "is required when " (name other-key) " is " expected)}))
```

## First application

User provider schema in `src/isaac/config/schema.clj` (the `provider` def around line 111). Add to the `:api-key` field:

```clojure
:api-key {:type        :string
          :validations [[:present-when? :auth "api-key"]]}
```

Effect: a self-defined user provider with `:auth "api-key"` and no `:api-key` is rejected at config load with the error landing on the missing field.

## Merge-layer consideration (for implementer)

User configs that reference a manifest template via `:type` don't include `:auth` directly — it's inherited from the template's `:template` map at resolution time. The validation should run against the **resolved/effective config** (post-merge) so it correctly catches the case where a user does `{:type :anthropic}` and forgets `:api-key`. The exact placement (before/after `resolve-provider*`) is for the implementer to pick, but the test scenario uses a self-defined provider to avoid that ambiguity at the contract level.

## New `@wip` scenario

- `features/modules/provider_extension.feature:143` — Self-defined provider with `:auth "api-key"` but no `:api-key` is rejected.

## Acceptance

- [ ] `com.cleancoders.c3kit/apron` bumped to `2.8.0` in `deps.edn` (two occurrences) and `bb.edn`.
- [ ] `:present-when?` registered in `isaac.config.loader` via `cs/register-ref!`.
- [ ] `:api-key` field in the provider schema (`config/schema.clj`) declares `:validations [[:present-when? :auth "api-key"]]`.
- [ ] Validation runs against the resolved/effective provider config so `:type`-templated configs are also covered.
- [ ] `@wip` removed from `features/modules/provider_extension.feature:143`; scenario passes.
- [ ] Existing provider scenarios continue to pass.
- [ ] Run: `bb features features/modules/provider_extension.feature` + spec suite.

## Out of scope (deferred)

- Sweep of other conditional fields across schemas — apply `:present-when?` opportunistically as those schemas get touched.
- Composing entity-scoped refs (the 2.8 docs flagged this isn't yet supported in 2.8; deferred upstream).
- `:mutually-exclusive` or similar refs for cron `:expr` vs `:at` — defer to [[isaac-nnns]].
- Replacing the dynvar-based existence-refs with entity-scoped equivalents — they need doc-wide context, not just sibling access, so the dynvar pattern stays for now.
