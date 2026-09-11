---
# isaac-mlw3
title: Startup-cache warm hit dropped :module-index; foundation 0.1.24 could not boot the server (hotfixed f5bdde0, needs scenario + decision)
status: todo
type: bug
priority: high
tags:
    - foundation
    - config
created_at: 2026-09-10T03:49:50Z
updated_at: 2026-09-10T03:49:50Z
---

Repo: **isaac-foundation** (`src/isaac/config/loader.clj` `try-cached-result`,
`src/isaac/startup/config_cache.clj`).

## What happened (2026-09-10, zanebot)

Deploying foundation 0.1.24 (v1la startup cache) took zanebot down for ten
minutes: every boot logged `:config/validation-error "unknown :type
\"discord\""` / `"imessage"` and launchd looped. The launcher writes the
startup cache (`cache/cli.edn`) before the JVM loads config, and
`cacheable-config` strips `:module-index` from the blob. On the warm hit
`try-cached-result` re-attached only `:root`, so the server's comm validator
(`isaac-server config/install.clj comm-validation-errors`, which uses
`(:module-index cfg)` to recognise module-provided comm impls) saw no
modules at all. A cache miss did not help: the same process wrote a fresh
cache and then read it.

Hotfixed in f5bdde0: the warm path runs `discovery/discover!` under a nested
nexus exactly like the cold path and attaches `:module-index` plus discovery
errors. Landed without a scenario, under outage pressure.

## Needed

1. **Pin it.** A feature scenario in `features/cli/startup-caching.feature`:
   a warm hit (second command, cache fresh) still yields a load result whose
   config carries `:module-index` for the declared modules, and a
   module-provided comm type validates. Marigold fixtures; no real modules.
2. **Decide whether discovery belongs on the warm path.** v1la's win was
   config resolution once per process; discovery (gitlib preload, manifest
   reads) is now paid on every warm hit again. Options: (a) keep as is and
   measure the CLI floor; (b) cache the module index too (it is not secret,
   but must be serialisable and invalidated when `:modules` or any manifest
   changes); (c) let callers that need the index (server boot, reconcile)
   ask for a cold load. Recommend measuring first.
3. **Boot smoke for foundation releases.** The formula's `def install`
   smoke runs `help` and `--version` only. A `server` boot against a root
   with one comm module would have caught this before the tag. Consider a
   CI job in isaac-foundation that boots isaac-server with a stub comm
   module on the release commit.

## Acceptance

```
cd isaac-foundation
bb features features/cli/startup-caching.feature
bb spec spec/isaac/config
bb ci
```
