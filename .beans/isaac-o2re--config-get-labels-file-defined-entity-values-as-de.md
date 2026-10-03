---
# isaac-o2re
title: config get labels file-defined entity values as (default), and labels maps as a whole
status: completed
type: bug
priority: normal
created_at: 2026-10-02T23:35:45Z
updated_at: 2026-10-03T00:42:44Z
---

Likely repo: **isaac-foundation**. Bug in isaac-dnib's `(default)` annotation. Field report 2026-10-02 (yopp).

## Why

`isaac config get models.claude-opus` printed a model defined in a config file
as `{…} (default)`. `config/cli/get.clj` `defaulted-value?` labels a path
"defaulted" when it is absent from the loader's `:raw-root`, and `:raw-root`
holds **root-level fields only, not entity-dir files** (loader.clj comment).
So every value that lives in `config/<dir>/<id>.edn` (crew, models,
providers, any module entity table) is labeled a default. Separately, a map
is labeled as a whole, which cannot say which field defaulted.

## Design (Micah: per-field labels)

- The default check compares against the raw config **including entity
  files**: a value set in any file is never a default. Keep "the CLI
  resolves the config once per command" (isaac-v1la): carry what's needed on
  the one load result, no second load.
- A defaulted scalar prints `<value> (default)` as today.
- A map prints each defaulted field with a trailing `; default` comment
  (output stays valid EDN), set fields unlabeled, and no trailing label after
  the map. Nested maps label their own leaves the same way.
- `--edn` / `--json` / `--raw` unchanged (plain data, no labels).

## Acceptance

- isaac-foundation `features/cli/config_default_labels.feature` — "a value set in an entity file is not labeled a default"
- isaac-foundation `features/cli/config_default_labels.feature` — "an entity map labels each defaulted field, and only those"
- isaac-foundation `features/cli/config_default_labels.feature` — "an entity map with nothing defaulted carries no labels"
- `features/cli/config_defaults.feature` (isaac-dnib) and the rest of config_default_labels.feature stay green.

feature-baseline: isaac-foundation a417452b5cffcc77eb80e2102fda7e4f8aaae55a
feature-blob: isaac-foundation features/cli/config_default_labels.feature 9a965ef7b660b0491fb5b4f41fce1725244ee557 40
feature-blob: isaac-foundation features/cli/config_default_labels.feature 9a965ef7b660b0491fb5b4f41fce1725244ee557 60
feature-blob: isaac-foundation features/cli/config_default_labels.feature 9a965ef7b660b0491fb5b4f41fce1725244ee557 74

## Landed on main (2026-10-02)

main-sha: isaac-foundation e7253f0089f8a73a6907016e3ec3682ff69207f5
