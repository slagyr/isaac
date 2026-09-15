---
# isaac-f21o
title: Stop persisting resolved config in cache/cli.edn
status: in-progress
type: task
priority: high
tags:
    - foundation
    - cli
    - cache
created_at: 2026-09-15T19:37:03Z
updated_at: 2026-09-15T19:39:42Z
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
