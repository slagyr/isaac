---
# isaac-f21o
title: Stop persisting resolved config in cache/cli.edn
status: completed
type: task
priority: high
tags:
    - cache
    - foundation
    - cli
created_at: 2026-09-15T19:37:03Z
updated_at: 2026-09-15T20:21:41Z
---

Stop writing the resolved config into `cache/cli.edn`. Keep the classpath pairs cache and the in-process memo (launcher threads the load; `config.api/load-resolved`).

## Problem

v1la put the normalized config next to classpath pairs so the *next* CLI process could skip parse+validate (~330 ms). That disk blob is keyed only on files from the last load (`:sources`) plus `config/isaac.edn`. A new entity file is invisible until something else invalidates the cache. Server boot also reads the blob (`load-config-result` → `try-cached-result`) — mlw3 (dropped `:module-index`) and 784x (live-root upgrade vs fresh root) are the same class of bug.

Each `isaac` process already loads once. The file is the extra layer.

## Decisions

- Decision (2026-09-15, Micah): drop the on-disk resolved-config cache. Keep classpath `cache/cli.edn` (`:classpath-pairs`, `:commands`). Keep once-per-process resolution.
- Decision (2026-09-15, Micah): clean cutover. No read-legacy of `:data :config`. Bump `cache-version` (3 → 4) so existing world-readable blobs with config in them are discarded on the next run.
- `skip-cache?` (isaac-784x) may remain as a no-op flag so mutate callers/specs stay; do not keep a disk-read path behind it.

## Scope (isaac-foundation)

- Remove `try-cached-result` from `load-config-result`.
- Stop writing `:data :config` / `:sources` / config `:errors` / `:warnings` in `write-classpath-cache!`.
- Delete `isaac.startup.config-cache` (and `config_cache_spec.clj`). Drop it from `foundation_boundary_spec` if listed.
- Leave `config.api` process memo and launcher `*extra-opts*` threading.

## Scenarios

`features/cli/config_resolution.feature` @ ae4eda6

- `:9` a fast-path command resolves the config exactly once — **keep** (in-process memo)
- `:16` a real command resolves the config exactly once — **keep**
- `:27` a second process still validates even when the classpath cache is warm — **new @wip**
- `:40` a new entity file is visible without deleting the classpath cache — **new @wip**
- `:56` cache/cli.edn has no config blob and no secret — **new @wip** (replaces the pre-substitution secret row)

Deleted (v1la disk-cache rows): warm hit skips validation; watched rewrite refreshes cached config; corrupted cached config fail-open.

`features/cli/startup-caching.feature`

- Classpath hit/miss/fail-open/timing rows — **keep**
- `:42` retitled to "a second config validate still discovers module-provided types" (was mlw3 warm-config-hit). Still passes without a config blob.

New steps invented: none.

At landing: remove `@wip` from `:27`, `:40`, `:56`.

## Exceptions

Authorized feature edits (2026-09-15): rewrite the `config_resolution.feature` description; delete the three v1la disk-cache scenarios; add the three @wip scenarios above; retitle the mlw3 startup-caching scenario as above.

## Acceptance

```
cd isaac-foundation
ISAAC_GIT=1 bb features features/cli/config_resolution.feature
ISAAC_GIT=1 bb features features/cli/startup-caching.feature
bb spec spec/isaac/startup spec/isaac/config/loader_spec.clj spec/isaac/config/mutate_spec.clj
bb ci
```

One-time checks (not scenarios):

- `git grep -n "config-cache\\|read-pre-sub\\|cacheable-config\\|try-cached-result" -- src` in isaac-foundation is empty.
- After a warm `isaac config get defaults`, `cache/cli.edn` has no `:data :config`.

## Work checkpoint (2026-09-15, scrapper@isaac-work-1)

Done: removed resolved-config disk reads/writes, deleted `isaac.startup.config-cache`, bumped startup cache version to 4, preserved classpath pairs/commands, and activated the three replacement scenarios. Added an inline module schema fixture so the new-entity scenario exercises a real entity directory. Focused config-resolution feature and classpath-cache spec are green; edited-file lint has zero errors (two pre-existing loader warnings).

Next: run the remaining acceptance specs and full CI, perform the one-time cache check, then rebase, push, and hand off. Resume at `src/isaac/main.clj:152` (updated six-argument classpath-cache writer call) with `bb spec spec/isaac/startup spec/isaac/config/loader_spec.clj spec/isaac/config/mutate_spec.clj`.

## Implementation (2026-09-15, scrapper@isaac-work-1)

- Removed the loader's resolved-config disk-cache read path and deleted `isaac.startup.config-cache` plus its spec.
- Startup cache version is now 4; `write-classpath-cache!` persists only `:classpath-pairs` and `:commands`.
- Kept process memoization and launcher-threaded load results; `:skip-cache?` remains accepted as a compatibility no-op.
- Activated all three replacement config-resolution scenarios. The new-entity scenario declares a minimal schema-providing module so `config/crew/*.edn` is a real entity directory in the foundation-only feature world.
- Branch: `bean/isaac-f21o` @ `f67fdcd80abf4d1f52b277fbae8b0c7f1a63f75c` (base `origin/main@ae4eda6904fbc609bde0c281007ad25779234d0d`).

Verification run:

- `bb lint ...` — 0 errors (existing warnings only).
- `ISAAC_GIT=1 bb features features/cli/config_resolution.feature` — 5 examples, 0 failures.
- `ISAAC_GIT=1 bb features features/cli/startup-caching.feature` — 8 examples, 0 failures.
- `bb spec spec/isaac/startup spec/isaac/config/loader_spec.clj spec/isaac/config/mutate_spec.clj` — 45 examples, 0 failures.
- `bb ci` — 1014 specs and 183 features, 0 failures (2 pre-existing pending scenarios). Initial CI run hit a stale generated gitlibs remote; deleting `~/.gitlibs/_repos/file/REL/fixture-agent` restored the fixture and the rerun passed.
- Required source grep is empty.
- Manual warm two-run check read cache version 4 with data keys exactly `(:classpath-pairs :commands)` and no `:config`.



## Landed on main (2026-09-15)

main-sha: isaac-foundation c89964d47db4ef6a7275d2f7c0f1ed5d7c8d5f82



## CI hail (2026-09-15, hail 1ab41fbb)

GitHub run 35018494124 failed `bb spec` on squash `c89964d` at `spec/isaac/log_viewer_spec.clj:344` (`tail! does not skip a line appended between the initial dump and follow seek`). Not caused by isaac-f21o (diff is config-cache only; this spec is unchanged since c12ebe3 / 2026-06-15). Subsequent main `b374929` CI Tests is green (run 35018639672). Isolated `bb spec spec/isaac/log_viewer_spec.clj` failed 5/5 locally on the same assertion — tracked as isaac-efb5 (todo). No reopen, no independent repair.
