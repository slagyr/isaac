---
# isaac-clic
title: CLI startup caching for fast commands (cache/cli.edn with timestamp invalidation)
status: in-progress
type: feature
priority: normal
created_at: 2026-07-08T00:00:00Z
updated_at: 2026-07-08T20:33:28Z
---

## Problem

Every `isaac` invocation (even `--version`) pays ~1s of upfront work: reading config, planning module classpath pairs (including implied transitive from deps.edn), calling `babashka.deps/add-deps` (or JVM equivalent), `discover!`, `reconcile-modules!`, `process-manifest-berths!` to register commands, etc.

This makes `isaac --version`, `isaac --help`, and short commands feel slow. The work is deterministic given the config and local module source trees.

## Evidence

- `time isaac --version` consistently ~1s (measured in local and zanebot envs).
- Code path: launcher.clj compose-classpath! (plan + add-deps) → main.clj register-module-cli-commands! (discover + berths) before the version/help cond.
- No cache today; every run recomputes.

## Desired behavior

The launcher and main short-circuit (or reuse) the expensive computations when the inputs haven't changed.

Cache file: `<isaac-root>/cache/cli.edn`

- On miss (no file or stale basis): compute the expensive bits (classpath-pairs, registered commands, etc.), write the cache with basis (watched file paths + their mtimes at compute time) + data, use the fresh results.
- On hit (basis mtimes all <= cached mtimes): load the data, skip the compute.
- Invalidation is mtime-based on:
  - the primary config file that supplied `:modules` (e.g. config/isaac.edn)
  - the :module-registry file (if declared)
  - for each :local/root: its isaac-manifest.edn and deps.edn (the files actually read during planning)
- Git modules are stable via their SHAs in the config (config mtime catches SHA changes).
- Cache survives across invocations; works with `--root`.
- `--version` and `--help` become near-instant on hit (and can early-exit even before full registration if we choose).
- Cache format is versioned; on format bump or manual clear we recompute.

## Scope

- isaac-foundation launcher + main paths (both bb packaged launcher and in-process for dev).
- The planning/composition in module/loader.clj (plan-module-classpath-pairs, preload, compose, config->launch-deps).
- The registration path that builds the command table.
- New cache logic (read/write/validate) under isaac-foundation/src/isaac (or a small cache ns).
- Tests: gherclj scenarios exercising real launcher runs (to hit the packaged path) against temp roots.
- Invalidation and hit/miss behavior visible in tests via cache file presence/content and (optionally) log events or timing.

Out of scope for this bean:
- Caching other things (e.g. full module manifests, session state).
- `isaac cache clear` command (can be follow-up).
- JVM classpath cache for the outer `clojure -Sdeps` in launchd (reuse the same planning cache where possible, but out of initial scope).

## Design decisions (to be recorded as we settle)

(Planning will append date-stamped decisions here.)

## Acceptance (gherkin, reuse existing CLI steps where possible; flag new ones)

Use the launcher step ("the isaac launcher is run with") for scenarios that must exercise the real packaged entrypoint and cache. Use "isaac is run with" for in-process where appropriate. Reuse "Given an empty Isaac root at", "the stdout matches", "exit code is", "Given the user home directory is", "the isaac file \"...\" exists", "the isaac file \"...\" EDN contains:", "the isaac file \"config/isaac.edn\" exists with:", "a module manifest at \"...\" :" etc.

New steps (flagged as needed):
- "the CLI used the cached data for startup" (observable via cache content unchanged, or side-effect absence)

Feature file written: isaac-foundation/features/cli/startup-caching.feature (with @wip)

Scenarios 1-3 approved and added.

---

## Scenarios (drafted per Planning Partner guidance; one at a time)

**Scenario 1 approved (2026-07-08)**

Given an empty Isaac root at "/test/cli-cache-miss"
And the isaac file "config/isaac.edn" exists with:
  | path    | value |
  | modules | {"local-mod" {:local/root "/test/local-mod"}} |
And a module manifest at "/test/local-mod/isaac-manifest.edn":
  | key     | value     |
  | id      | :local-mod |
  | version | "1.0.0"   |
When the isaac launcher is run with "--version"
Then the stdout matches:
  | pattern              |
  | ^isaac \d+\.\d+\.\d+ |
And the exit code is 0
And the isaac file "cache/cli.edn" exists
And the isaac file "cache/cli.edn" EDN contains:
  | path    | value |
  | version | 1     |

(reuses: launcher run, stdout matches table, exit code, isaac file exists, isaac file EDN contains, isaac file exists with, module manifest)

**Scenario 2 approved (2026-07-08)**

Ledger: reuses empty root, "the isaac file ... exists with:", module manifest, launcher run, stdout matches, exit code, isaac file exists, isaac file EDN contains. No new steps.

```gherkin
Scenario: unchanged inputs hit the cache (fast path)
  Given an empty Isaac root at "/test/cli-cache-hit"
  And the isaac file "config/isaac.edn" exists with:
    | path    | value |
    | modules | {"local-mod" {:local/root "/test/local-mod"}} |
  And a module manifest at "/test/local-mod/isaac-manifest.edn":
    | key     | value     |
    | id      | :local-mod |
    | version | "1.0.0"   |
  And the isaac file "cache/cli.edn" exists with:
    | path          | value          |
    | version       | 1              |
    | basis.config  | 1234567890000  |
  When the isaac launcher is run with "--version"
  Then the stdout matches:
    | pattern              |
    | ^isaac \d+\.\d+\.\d+ |
  And the exit code is 0
  And the isaac file "cache/cli.edn" exists
  And the isaac file "cache/cli.edn" EDN contains:
    | path          | value          |
    | version       | 1              |
    | basis.config  | 1234567890000  |
```

(Pre-populated cache + unchanged content proves hit/reuse.)

**Scenario 3 approved (2026-07-08)**

Ledger: reuses empty root, isaac file exists with, module manifest, launcher run, stdout matches, exit code, isaac file exists, isaac file EDN contains. No new steps.

```gherkin
Scenario: config change invalidates the cache
  Given an empty Isaac root at "/test/cli-cache-inval"
  And the isaac file "config/isaac.edn" exists with:
    | path    | value |
    | modules | {"local-mod" {:local/root "/test/local-mod"}} |
  And a module manifest at "/test/local-mod/isaac-manifest.edn":
    | key     | value     |
    | id      | :local-mod |
    | version | "1.0.0"   |
  And the isaac file "cache/cli.edn" exists with:
    | path          | value          |
    | version       | 1              |
    | basis.config  | 1111111111111  |
  When the isaac launcher is run with "--version"
  Then the stdout matches:
    | pattern              |
    | ^isaac \d+\.\d+\.\d+ |
  And the exit code is 0
  And the isaac file "cache/cli.edn" exists
  And the isaac file "cache/cli.edn" EDN contains:
    | path          | value          |
    | version       | 1              |
    | basis.config  | #*             |
```

(Overwrite of config updates mtime; new basis timestamp proves recompute.)

**Scenario 4 approved (2026-07-08)**

The local module "changes" by having its manifest file re-written after the cache is set up. The second "And a module manifest..." step writes the file, bumping its filesystem mtime. On the subsequent launcher run, the cache logic sees the manifest mtime is now newer than the mtime recorded in the cached basis → cache is stale → work is recomputed and a fresh cache with updated basis timestamp is written.

```gherkin
Scenario: local module manifest change invalidates the cache
  Given an empty Isaac root at "/test/cli-cache-inval-local"
  And the isaac file "config/isaac.edn" exists with:
    | path    | value |
    | modules | {"local-mod" {:local/root "/test/local-mod"}} |
  And a module manifest at "/test/local-mod/isaac-manifest.edn":
    | key     | value     |
    | id      | :local-mod |
    | version | "1.0.0"   |
  And the isaac file "cache/cli.edn" exists with:
    | path          | value          |
    | version       | 1              |
    | basis.local   | 1111111111111  |
  And a module manifest at "/test/local-mod/isaac-manifest.edn":
    | key     | value     |
    | id      | :local-mod |
    | version | "1.0.0"   |
  When the isaac launcher is run with "--version"
  Then the stdout matches:
    | pattern              |
    | ^isaac \d+\.\d+\.\d+ |
  And the exit code is 0
  And the isaac file "cache/cli.edn" exists
  And the isaac file "cache/cli.edn" EDN contains:
    | path          | value          |
    | version       | 1              |
    | basis.local   | #*             |
```

(Second manifest declaration updates its mtime → invalidates → new basis.)

Feature file updated with this scenario.

Feature file: isaac-foundation/features/cli/startup-caching.feature (with @wip, Scenarios 1-5: miss, hit, config invalid, local invalid, --help)

The 5 cover the core (miss/hit + both invalidation types + broader commands). 

Per-root isolation is the main remaining from drafts if you want extra robustness.

Core coverage good. Per-root isolation would be solid addition for #6 if you want full robustness.

**Scenario 5 (proposed)**

Ledger: reuses empty root, isaac file exists with, module manifest, launcher run, stdout matches, exit code, isaac file exists, isaac file EDN contains. No new steps.

```gherkin
Scenario: --help also benefits from cache
  Given an empty Isaac root at "/test/cli-cache-help"
  And the isaac file "config/isaac.edn" exists with:
    | path    | value |
    | modules | {"local-mod" {:local/root "/test/local-mod"}} |
  And a module manifest at "/test/local-mod/isaac-manifest.edn":
    | key     | value     |
    | id      | :local-mod |
    | version | "1.0.0"   |
  And the isaac file "cache/cli.edn" exists with:
    | path          | value          |
    | version       | 1              |
    | basis.config  | 1234567890000  |
  When the isaac launcher is run with "--help"
  Then the stdout contains "Usage: isaac"
  And the exit code is 0
  And the isaac file "cache/cli.edn" exists
  And the isaac file "cache/cli.edn" EDN contains:
    | path          | value          |
    | version       | 1              |
    | basis.config  | 1234567890000  |
```

(Pre-populated cache + unchanged proves hit for --help too.)

**Scenario 6 (proposed)**

Ledger: reuses empty root, isaac file exists with, module manifest, launcher run, stdout matches, exit code, isaac file exists, isaac file EDN contains. No new steps. Uses two roots.

```gherkin
Scenario: cache is per-root
  Given an empty Isaac root at "/test/cli-cache-root1"
  And the isaac file "config/isaac.edn" exists with:
    | path    | value |
    | modules | {"mod1" {:local/root "/test/mod1"}} |
  And a module manifest at "/test/mod1/isaac-manifest.edn":
    | key     | value |
    | id      | :mod1 |
    | version | "1.0" |
  When the isaac launcher is run with "--version"
  Then the isaac file "cache/cli.edn" exists
  Given an empty Isaac root at "/test/cli-cache-root2"
  And the isaac file "config/isaac.edn" exists with:
    | path    | value |
    | modules | {"mod2" {:local/root "/test/mod2"}} |
  And a module manifest at "/test/mod2/isaac-manifest.edn":
    | key     | value |
    | id      | :mod2 |
    | version | "1.0" |
  When the isaac launcher is run with "--version"
  Then the isaac file "cache/cli.edn" exists
  And the isaac file "cache/cli.edn" under "/test/cli-cache-root1" does not match the one under root2
```

(Cache is scoped to each Isaac root.)

Given two empty roots R1 and R2 with different module sets
When launcher run against R1 (miss, writes cache for R1)
And launcher run against R2 (miss, writes cache for R2)
Then each root has its own cache/cli.edn with its own basis

**Scenario 7: cache survives across invocations, used on second run**

(combine 1 and 2)

## Decisions (append here as settled with Micah)

Decision (2026-07-08, planning): Use `<root>/cache/cli.edn` (dedicated dir for future caches). Single file for CLI calculations. Name: cli.edn.

Decision (2026-07-08): Invalidation strictly on mtimes of watched files listed in basis at write time. Config mtime covers module-set changes; local manifests/deps cover local source changes.

(Record more as we clarify: early exit for version, exact data stored in :data, whether to also speed --help registration, new step names, etc.)

## Open questions / deferred (to settle before final scenarios)

- Exact contents of :data (pairs only? full command map?).
- Whether launcher does an argv early-out for --version before any config read (even better than cache hit).
- Do we also cache for the `isaac modules deps --edn` JVM launch path explicitly?
- Cache format version policy and when we bump.
- Test strategy for timing assertions (or just "used cache" via observable side-effect absence).

## Scope / ripple

- Affects: launcher.clj, main.clj (early paths + registration), module/loader.clj (plan + preload + config->launch-deps), new cache read/write logic.
- Feature file in isaac-foundation/features/cli/cli-startup-caching.feature (or similar).
- May touch cli_steps.clj for new Given/Then (cache presence, basis, "used cached").
- No change to user config schema.
- Future: other caches under same dir.
