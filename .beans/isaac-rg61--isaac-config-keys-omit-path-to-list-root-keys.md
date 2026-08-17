---
# isaac-rg61
title: 'isaac config keys: omit path to list root keys'
status: completed
type: bug
priority: normal
created_at: 2026-08-17T14:23:35Z
updated_at: 2026-08-17T14:45:44Z
---

## Goal

`isaac config keys` with no path should list the resolved config's top-level keys. Today it prints `config path is required` and exits 1.

## Settled design (2026-08-17, Micah)

- Omit the path → list one level of keys on the loaded/merged config (the same map `config get` prints). No values. Sorted. `--json` / `--edn` unchanged.
- Includes loader-injected keys (`defaults`, `crew`, `models`, `providers`, `cron`) and load-time `:module-index` / `:root`. No extra filter.
- `config list` gets the same optional path (same `require-path!` gate).
- Help becomes `keys [config-path]` / `list [config-path]`.
- Home: isaac-foundation (CLI). Scenarios on existing keys/list section in `isaac-agent/features/config/cli.feature`.

## Scenario plan (2)

1. `config keys` with no path lists root keys (and not values)
2. `config list` with no path lists those keys with source files

## Implementation notes

- `require-path!` in `isaac.config.cli.inspect` is the only blocker; `dotted-child-key` already handles a blank path.
- Treat nil/blank like `config get` — do not pass nil to `path/data-at` (it throws).
- Slash-mode `isaac config keys /` also normalizes to `""` and currently dies on `require-path!`.

## Scenario review (2026-08-17, Micah)

1. keep — `config keys with no path lists root keys`. Present keys from the resolved map; `module-registry` absent because it is not in this config; values (`llama3.3`) never printed.

2. keep — `config list with no path lists root keys and sources`. Root keys report `config/isaac.edn` (single-segment path; `config-source-file` has no entity id).

## Acceptance

Repo: **isaac-agent** (scenarios). Implement in **isaac-foundation**.

```
bb features features/config/cli.feature:930
bb features features/config/cli.feature:963
```

DoD: remove `@wip` from both scenarios; those commands pass. Help: `keys [config-path]` / `list [config-path]`.

## Worker notes

- Drop `require-path!` from `keys`/`list` (only callers). Blank/nil path = root, same as `config get`. Do not pass nil to `path/data-at`.
- TDD in `isaac-foundation/spec/isaac/config/cli/` (no keys/list spec yet).

## Implementation (scrapper@isaac-work-2, 2026-08-17)

- **isaac-foundation** `cbf9e37`: drop `require-path!` from keys/list; blank path = resolved root via `value-at-path` (nil-safe); help `keys [config-path]` / `list [config-path]`; top-level help summary updated. Specs: `keys_spec.clj`, `list_spec.clj`.
- **isaac-agent** `f6e4431`: pin foundation product SHAs to `cbf9e37`; remove `@wip` from both root-path scenarios.

### Verified
- `bb spec -F spec/isaac/config/cli/` (foundation) ✅ 72/0
- `bb features features/config/cli.feature:930` ✅
- `bb features features/config/cli.feature:963` ✅
