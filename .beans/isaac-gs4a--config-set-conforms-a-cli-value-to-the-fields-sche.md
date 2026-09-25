---
# isaac-gs4a
title: config set conforms a CLI value to the field's schema type; unset takes a set member
status: in-progress
type: bug
priority: high
tags:
    - config
    - unverified
    - foundation
created_at: 2026-09-25T14:46:21Z
updated_at: 2026-09-25T15:03:39Z
---

Repo: **isaac-foundation** (src/isaac/config/cli/mutate_common.clj). Scenarios live in **isaac-agent** (features/config/set_unset.feature), because the crew schema they exercise (`tags` is a set of keywords, `soul` is a string) is agent's.

## The problem (zanebot, 2026-09-25)

Micah tried to change an HTTP principal's scopes with `config set`:

- `config set http.auth.principals.micah-zaap.scopes :hooks` crashed with `Don't know how to create ISeq from: clojure.lang.Keyword`.
- `config set …scopes hooks` was refused with "must be a set of keywords".
- `config set …scopes '#{:hooks}'` can't work either. Nothing typed on the command line becomes a set; only stdin EDN does.
- `config unset …scopes :hail/send` ignored `:hail/send` and targeted the whole `scopes` key.

The cause is `parse-set-value` (mutate_common.clj:22). It guesses the type from the text's shape: digits become a long, a leading `:` becomes a keyword, else a string. It never asks the schema what the field holds, so `soul 42` becomes a long and fails validation, and `:hooks` for a set field reaches coercion as a bare keyword and throws.

## Change

**Conform the CLI string to the field's schema type.** The composed schema knows every field's type (`target-spec-for` already resolves it), so use it:

- keyword / id: `hooks` → `:hooks`, and `:hooks` → `:hooks`
- int / long / double: parse the number; an unparseable value is a validation error (exit 1), as today
- string: keep the text verbatim, digits included (`42` stays `"42"`)
- set of X: split on commas, conform each member as X, and build the set. `hooks` → `#{:hooks}`, `hooks,hail/send` → `#{:hooks :hail/send}`. A single keyword gives a one-member set.
- boolean: `true` / `false`
- no spec (open or undeclared path): fall back to today's shape-guessing

Any exception while conforming becomes an ordinary validation error with exit 1. A raw Clojure exception never reaches the user.

**`config unset <path> <member>`** on a set-typed field removes just that member, the same as the existing `config unset <path>.<member>` form. On a path that is not a set, an extra argument is refused (exit 1, "takes no value"), never silently ignored.

## Decisions

- Decision (2026-09-25, Micah): "All the types for the config are known and we can take advantage of that to make sure that we set valid values." The schema is how a value gets parsed, not a check applied after guessing.
- Decision (2026-09-25, planner): setting a whole set-typed field replaces the set. `config set crew.joe.tags jackalope` gives `#{:jackalope}`. Adding one member is still `config set crew.joe.tags.jackalope` (the existing path form, unchanged). This matches what "set" means for every other type.
- The existing scenario "config set refuses a value a schema validator rejects" (`tags jackalope` → exit 1) was rewritten in place into the first scenario below. Clean cutover: that input is now valid.

## Scenarios (isaac-agent, @wip)

- features/config/set_unset.feature:84 — bare name conforms to the keyword set (replaces)
- features/config/set_unset.feature:101 — keyword conforms to a one-member set, no crash
- features/config/set_unset.feature:114 — comma list conforms to a set of keywords
- features/config/set_unset.feature:127 — digits stay a string for a string field
- features/config/set_unset.feature:138 — `unset <path> <member>` removes only that member
- features/config/set_unset.feature:152 — `unset` refuses a value on a path that is not a set

## Acceptance

Implement in isaac-foundation, run isaac-agent's features against it via `:dev-local`, then land foundation and repin agent's foundation pin.

```
cd isaac-foundation && bb spec && bb ci
cd isaac-agent && bb features features/config/set_unset.feature:84 features/config/set_unset.feature:101 features/config/set_unset.feature:114 features/config/set_unset.feature:127 features/config/set_unset.feature:138 features/config/set_unset.feature:152
cd isaac-agent && bb features features/config/set_unset.feature && bb ci
```

Remove `@wip` from those six scenarios; all of set_unset.feature is green.


## Verify fail (attempt 1, 2026-09-25): agent foundation pin was not repinned, so the six acceptance scenarios execute against eaea445 and fail 5/6; additionally, colon-prefixed unset members are converted to ::name rather than :name.
