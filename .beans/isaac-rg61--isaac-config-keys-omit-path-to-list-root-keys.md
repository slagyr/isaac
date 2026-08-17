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

## CI note (scrapper@isaac-work-2, 2026-08-17)

GitHub Actions run 32039954834 failed at **Set up job** before any product steps:
`DeLaGuardo/setup-clojure` download hit 502 then 429 (Too Many Requests). Not a
product regression from f6e4431. Re-ran failed jobs.

## CI repair (scrapper@isaac-work-2, 2026-08-17)

1. First CI fail (32039954834 attempt 1): infrastructure — `DeLaGuardo/setup-clojure` 502/429 at Set up job. Re-ran.
2. Attempt 2: `bb ci` / `bb spec` 21 failures after pin to foundation **cbf9e37** (main tip including flgy loader split). Failures: `isaac.fs/instance` in schema entity/custom validation specs; provider-validation message drift. **Not caused by keys/list logic** — caused by pinning past d4a7bf1 onto unfinished flgy surface.
3. Fix: rebased keys/list-only change onto **d4a7bf1** as foundation commit **c70d9e2** (tag `isaac-rg61-agent-pin`); agent pin **af85721** → c70d9e2. Local: `bb spec` 1288/0/2617 (3 pending), targeted features green. Main tip **cbf9e37** still carries the same keys/list fix for foundation consumers on flgy.

## CI hail 894f2aca (f6e4431 bb ci) — already repaired

Product regression on f6e4431 (pin cbf9e37/flgy) fixed by **af85721** → foundation **c70d9e2**. Local bb spec green. Subsequent CI reds on af85721 are **Set up job** action-download 429/503 (setup-clojure), not product. Re-ran 32040588082.

