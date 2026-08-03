---
# isaac-4lx5
title: 'foundation/cli: surface the hidden ''help'' command + richer topic help'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-18T18:07:35Z
updated_at: 2026-08-03T14:25:31Z
---

## Summary

Surface the top-level `help` command in the CLI menu and extend it with non-command **topics** (v1: `root`). Lives in **isaac-foundation**.

## Feature file

`isaac-foundation/features/cli/cli.feature` (@wip scenarios for this bean)

| # | Scenario | Line |
|---|----------|------|
| 1 | Top-level usage lists the help command | :50 |
| 2 | isaac help with no target prints usage | :60 |
| 3 | isaac help for a known command prints that command's help | :70 |
| 4 | isaac help root prints root resolution topic | :77 |
| 5 | Help for an unknown command or topic | :18 |
| 6 | isaac help help documents usage and lists topics | :87 |

## Design decisions (2026-08-03, Micah)

1. **Register `help` as a real `:isaac/cli` berth entry** (like `init`) so it appears in `all-commands`, startup cache, and the Commands menu. Not a synthetic-only usage line.
2. **Topics are a small map** (name → help text/fn), separate from the command registry. Lookup: **command first, then topic**.
3. **v1 topics: only `root`.** Content reuses `isaac.config.root/root-lookup-precedence` (same strings as `config sources`). Do not duplicate config/modules as topics — those already work via command help.
4. **Topic list lives on `isaac help help`** under a `Topics:` section, generated from the same map so it cannot drift.
5. **Default help output** (`isaac`, `--help`, bare `isaac help`) **mentions that topics exist** and points operators at `isaac help help` (footer or equivalent). Full topic list is not required on the top-level menu.
6. **Unknown target copy:** `Unknown command or topic: <name>` (exit 1). Clean cutover from `Unknown command:`.
7. **No option help** for now (`help --root` etc. out of scope).
8. **Out of scope:** `man isaac`; broader topic catalog.

## Implementation notes

- Repo: **isaac-foundation** (`src/isaac/main.clj`, `src/isaac/cli/registry.clj`, foundation `:isaac/cli` in `src/isaac-manifest.edn`; small help ns optional).
- Menu summary for help: `"Show help for a command or topic"`.
- Prefer removing the hard-coded `help` special-case in `main` once the berth command's `run` handles no-arg / command / topic resolution.
- Update unit specs in `spec/isaac/main_spec.clj` for the new unknown-message and `help help` / topic paths.
- Definition of done: remove `@wip` from the six scenarios above; they pass.

## Acceptance

```bash
# from isaac-foundation checkout
bb features features/cli/cli.feature:50
bb features features/cli/cli.feature:60
bb features features/cli/cli.feature:70
bb features features/cli/cli.feature:77
bb features features/cli/cli.feature:18
bb features features/cli/cli.feature:87
bb features features/cli/cli.feature   # full CLI feature green, no @wip left for this bean
bb spec spec/isaac/main_spec.clj
bb lint
```
