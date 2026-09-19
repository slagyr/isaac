---
# isaac-fun8
title: 'config set writes schema-invalid values: value-validator errors are dropped as reference errors'
status: in-progress
type: bug
priority: high
tags:
    - config
    - foundation
created_at: 2026-09-19T00:08:20Z
updated_at: 2026-09-19T00:12:43Z
---

## Bug

`isaac config set crew.yopp.tags jackalope` on yopp (2026-09-18T23:59Z) exited 0, logged `:config/set` at info, and wrote `:tags "jackalope"` into `config/crew/yopp.edn`. `isaac config validate` immediately afterwards reports `crew.yopp.tags - must be a set of keywords`. A mutation must never persist a value the schema's validators reject.

## Root cause (isaac-foundation)

`isaac.config.mutate/set-config` validates the staged plan and does catch the error. The CLI (`isaac.config.cli.mutate-common/set-config!`, also `set-member!`/`unset-member!`) always passes `:skip-ref-validation? true` so operators can reference entities not yet defined. That option removes every new error for which `reference-error?` is true, and `reference-error?` is `(contains? e :bad-value)`. But `validation-error-entry` (`isaac.config.validation`) stamps `:bad-value` on **every** entry it builds, including the lexicon value validators (`:keyword-set?`, `:positive?`, `:absolute-path?`, `:cwd-or-path?`, …). So value-validator failures are misclassified as reference errors, dropped, and the write proceeds. Only apron type errors (`:int`, `:string`, …) still block, which is why `config set crew.joe.effort not-a-number` is refused but the tags case is not.

Existence refs are the ones built by `exists-ref` in `isaac.config.validation-lexicon` (`:model-exists?` `:crew-exists?` `:gauge-exists?` `:berth-exists?`); they are the only defs carrying `:known`.

## Fix

- Tag error entries that come from an existence ref (the ref-def has `:known`, or mark `exists-ref` defs explicitly, e.g. `:reference? true`) and make `reference-error?` test that tag. Value-validator errors are then "new errors" under `skip-ref-validation?` and block the mutation with the normal `:invalid` path (stderr error line naming the path and message, exit 1, no write).
- Keep the reference lenience exactly as documented in the `set-config` docstring; do not add a CLI flag for it (Micah, 2026-09-18).
- `config set` / `config unset` print a one-line confirmation on success to stdout: `set <path> = <value> (<file>)`, `set <path> += <member> (<file>)` for set-typed member paths, `unset <path> (<file>)` (and `-= <member>` for member removal). Suppressed when `--edn`/`--json` is requested; the structured record is the only stdout then. Values print as EDN (`pr-str`).
- `config set --help` (and `unset --help`) document the set-typed member path form with the wording in the scenario: "Set-typed fields take the member in the path" + example `isaac config set crew.marvin.tags.role/worker` (no value). This is the documentation Micah asked for; there is no other config CLI doc to update.

## Scenarios (committed `@wip` in isaac-agent `a0c7a78`, `features/config/set_unset.feature`, branch `bean/isaac-fun8`)

| line | scenario |
|---|---|
| refuses | config set refuses a value a schema validator rejects |
| lenience | config set still accepts a reference to an entity that is not defined yet |
| confirm set | config set confirms what it wrote and where |
| confirm member | config set confirms a set member it added |
| confirm unset | config unset confirms what it removed |
| structured | config set --edn prints only the structured record |
| help | config set --help documents the set-member path form |

Existing coverage to keep green: `features/config/set_unset.feature` (type error on `effort`), `features/tagging/crew_tags.feature:180-215` (member add/remove).

## Step ledger

| step | status |
|---|---|
| default Grover setup / the isaac EDN file … exists with: / isaac is run with … / the stderr contains … / the stdout contains … / the stdout does not contain … / the stdout matches: (pattern column) / the exit code is … | reuse |

No new steps.

## Repos

- Production fix in **isaac-foundation** (`mutate.clj` `reference-error?`, `validation.clj` entry tagging or `validation_lexicon.clj` `exists-ref`, `cli/mutate_common.clj` confirmations, `cli/set.clj` + `cli/unset.clj` help). Bump foundation version. Unit specs for `reference-error?` classification and the confirmation lines.
- Scenarios live in **isaac-agent** (crew tags schema is agent's). Pin agent's foundation dep to the fixed sha on `bean/isaac-fun8`, remove `@wip`.

## Acceptance

Definition of done: `@wip` removed and

```
cd isaac-foundation && bb spec && bb ci
cd isaac-agent && bb features features/config/set_unset.feature && bb features features/tagging/crew_tags.feature && bb ci
```

Not in scope: pretty-printing the written EDN (isaac-2nkg, dispatched separately, same files — rebase carefully).

Dispatched: hail b743fe64 2026-09-19T00:10:22Z (band isaac-work)
