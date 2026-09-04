---
# isaac-v1la
title: 'CLI floor is redundant config resolution: every command loads and validates the config 3–5 times (~1.3 s of the 1.6 s --version)'
status: todo
type: bug
priority: high
tags:
    - foundation
    - cli
    - performance
created_at: 2026-09-04T16:30:55Z
updated_at: 2026-09-04T16:47:34Z
---

Micah (2026-09-04): "find out why the floor has grown — I don't think foundation has grown significantly." It hasn't. Profiled on zanebot (foundation 0.1.23, brew HEAD-8dc5d5e, bb 1.12.214, wrapper env with CLJ_CONFIG/DEPS_CLJ_DIR=deps-home) by instrumenting `isaac.config.loader/load-config-result` with a call counter + timer and driving `isaac.launcher/-main`:

| command | wall | config loads | time in config loads |
|---|---|---|---|
| `--version` | 1307 ms | **3** | 1296 ms (99%) |
| `config get defaults` | 2171 ms | **5** | 2003 ms |
| `sessions list` | 5626 ms | **5** | 2039 ms (+3.6 s elsewhere — separate) |

Stage costs: bare bb 30 ms; bb + launcher bb.edn 30 ms (deps cached under the wrapper env; 1.2 s only when CLJ_CONFIG points elsewhere); `require isaac.launcher` 220 ms; classpath compose from cache 3 ms; module discovery 12 ms; schema compose 6 ms; `cache/fresh?` 0 ms. One `load-config-result` ≈ 410–650 ms, of which ≈75 ms is the 82 entity files and ≈330 ms is parse + validate + normalize of the root isaac.edn (the big :discord/channels map etc.). Not JVM (none spawned), not cache misses (basis current, cache not rewritten).

Where the loads come from (all on the --version fast path): `isaac.launcher/-main` → `read-user-config` (1); `isaac.main/run` → `read-user-config` (2); `configure-cli-logging!` → `read-user-config` (3). Real commands add `register-module-cli-commands!` / command handlers reading it again (4–5). The ogiu profile's "Hotspot #1: 1.33 s bb source-load floor, untouchable" was misattributed — it is this. isaac-eqkb (daemon) was justified by that misattribution.

## Fix
1. **Resolve once per process.** Memoize `read-user-config` / `load-config-result` on (root, fs, watched-file mtimes) for the life of the CLI process, or thread the resolved config from the launcher into main and logging explicitly (preferred: explicit — the launcher already has it; pass it via `*extra-opts*` / a nexus slot). Target: 1 load per command.
2. **Cache the resolved config in the startup cache** (basis-keyed like the classpath, fail-open): a warm hit deserialises the normalized config instead of re-validating (≈330 ms → tens of ms). Hot-reload semantics are unaffected (the server re-resolves on change; the CLI's basis already watches every config file mtime).
3. Follow-up, not this bean: `sessions list` spends 3.6 s outside config resolution (session directory walk / token gauges over 30+ sessions) — measure and bean separately. isaac-kids' measurement item is superseded by this table.

Expected: `--version` ≈1.3 s → ≈0.5 s with (1) alone, ≈0.15 s with (2); every crew shell-out (hundreds per bean) drops by ~1 s.

## Scenarios (@wip, worker writes in isaac-foundation; features/cli/startup_cache.feature or a new features/cli/config_resolution.feature)
1. a fast-path command resolves the config exactly once (spy/counter on load-config-result, like tki3's planning-spy scenario) — `--version`, `--help`
2. a real command resolves the config exactly once — `config get defaults`
3. with a fresh startup cache the resolved config is served from the cache and validation is not invoked (spy on validate/normalize); any watched file change re-resolves and rewrites (mirrors tki3's invalidation scenario)
4. a cache read failure falls back to full resolution silently (fail-open)
Unit specs: memoization keyed on watched mtimes; server path unaffected (hot-reload still re-resolves).

## Acceptance
    cd isaac-foundation && bb features features/cli && bb spec
    zanebot after the brew train: `/usr/bin/time -p isaac --version` under 0.6 s (fix 1) / under 0.3 s (fix 2); record the table above re-measured.



## Features (`@wip`) — isaac-foundation `features/cli/config_resolution.feature` @ 392ba85
1. a fast-path command resolves the config exactly once
2. a real command resolves the config exactly once
3. a warm startup cache serves the resolved config without validating
4. a watched config change re-resolves and refreshes the cached config
5. a corrupted cached config falls back to full resolution

## Step ledger (mirrors features/cli/startup-caching.feature)

| step | status |
|------|--------|
| an empty Isaac root at / isaac EDN file exists with / isaac file exists with / isaac is run with / the exit code is 0 / the stdout contains / the classpath cache file is corrupted so apply fails | reuse |
| **the config resolution spy is armed** / **was invoked exactly N times** | **NEW — same shape as `the classpath plan spy …` (startup-caching.feature): counts `isaac.config.loader/load-config-result` calls in the CLI process** |
| **the config validation spy is armed** / **was invoked exactly N times** / **at least N times** | **NEW — counts root validate+normalize (fix 2's skip)** |
| **a warm startup cache exists from a prior run** | **NEW phrasing — generalises `a warm classpath cache exists from a prior non-fast-path run`; implement as an alias of that step** |
| **the startup cache was refreshed after replan** | **NEW phrasing — alias of `the classpath cache was refreshed after replan`** |

Four new step phrasings, two of which are aliases of existing classpath-cache steps; the two spies copy the classpath-plan spy pattern.



## Decision (2026-09-04, Micah): cache the resolved config PRE-substitution
The cached config must never contain substituted secrets (cache/cli.edn is world-readable on zanebot). Cache the normalized config with ${VAR} placeholders intact and re-apply env substitution on the warm read (env/lock-dotenv! + the existing substitute step), so a warm hit still reflects the current .env. Add a scenario: the cache file never contains a substituted token value (fixture .env with a known secret; grep the cache).
