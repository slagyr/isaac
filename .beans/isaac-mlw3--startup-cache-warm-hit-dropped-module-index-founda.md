---
# isaac-mlw3
title: Startup-cache warm hit dropped :module-index; foundation 0.1.24 could not boot the server (hotfixed f5bdde0, needs scenario + decision)
status: in-progress
type: bug
priority: high
tags:
    - config
    - unverified
    - foundation
created_at: 2026-09-10T03:49:50Z
updated_at: 2026-09-12T15:58:29Z
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

## Implementation evidence (scrapper@isaac-work-1)

- Branch: `bean/isaac-mlw3` @ `f8dc5938c955437c1580f4123f07418b14f85cd5` (base `origin/main@187356baed01e6e4aa3f7da313eb5be46e2cadbb`).
- Added a startup-caching scenario that runs `config validate` cold then warm with `marigold.bridge`/`marigold.longwave`, and asserts the captured warm load result retains `:module-index`, accepts the contributed `:longwave` config type, has no validation errors, and exits zero. Reversing hotfix `f5bdde0` makes this scenario fail at the missing module-index assertion.
- Decision recorded at `doc/decisions/startup-cache-module-discovery.md`: keep discovery on warm config loads; measured fixture warm loads at 0.45–0.66s (median 0.65s), versus 0.56–0.59s fast-path process floor. Do not cache module index without profiling and complete manifest/transitive invalidation design.
- Added a Foundation CI server/config boot smoke that checks out `isaac-server`, configures a module-provided fixture type, and validates twice through the startup cache.
- Fixed pre-existing order-dependent `config check-compose` spec isolation by installing a mem-fs nexus.
- Final gates after rebase: startup-caching feature 8 examples/10 assertions green; config specs 338 examples/674 assertions green; `bb ci` specs 1019 examples/1841 assertions green and features 182 examples/486 assertions green.
- Note: a first `bb ci` attempt exposed stale tools.gitlibs fixture state left by an unrelated `modules_pins` branch verification checkout. The required feature/config gates and a subsequent clean full CI run passed; no product change was needed for this external cache contamination.
