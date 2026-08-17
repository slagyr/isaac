---
# isaac-rg61
title: 'isaac config keys: omit path to list root keys'
status: draft
type: bug
priority: normal
created_at: 2026-08-17T14:23:35Z
updated_at: 2026-08-17T14:23:35Z
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
