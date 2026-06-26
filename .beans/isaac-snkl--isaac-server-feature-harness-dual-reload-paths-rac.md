---
# isaac-snkl
title: 'isaac-server feature harness: dual reload paths race, making hot-reload comm tests nondeterministic'
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-26T20:40:56Z
updated_at: 2026-06-26T21:40:54Z
---

The isaac-server feature step harness (spec/isaac/server/server_steps.clj) drives config hot-reload via TWO concurrent paths on every config write, which race and make comm hot-reload feature tests ~50% flaky. Surfaced by isaac-yy88 (Discord token hot-reload): the discord `lifecycle.feature` "adding a Discord token mid-run starts the client" scenario is currently @wip'd in isaac-discord pending this fix.

## Root cause (traced in yy88)
On each config-file write, two consumers drain the SAME change-source via `runtime/poll!`:
1. **Synchronous** — `server_steps.clj` `sync-config-reload!` (called from the fs post-write hook and `notify-config-change!`) loops `poll!` + `runtime/reload!` on the TEST thread. Reconciles comms correctly.
2. **Async** — the config watcher `app/start!` launches (`start-config-reloader!`), a background future that also `poll!`s the source and `reload!`s on a WATCHER thread.

They race on `poll!`: a write's reload runs sync (test thread → comm connects reliably) or async (watcher thread). The async path's reload reconciles on the watcher thread whose nexus/instance interaction sometimes leaves the live comm integration (e.g. the Discord client) DISCONNECTED — so the post-reload assertion fails ~50% of runs. The production reconcile logic is correct (proven deterministic by isaac-discord `discord_app_spec`); only the dual-path harness is nondeterministic.

NOTE: an additional gotcha that masks this locally — `clojure -M:features` does NOT clean `target/gherclj`, so stale generated specs (e.g. from a prior un-@wip run) re-run excluded scenarios and inflate example counts. Always `rm -rf target/gherclj` between local runs when changing tags. (CI is unaffected — fresh checkout.)

## Constraint (don't regress)
- `c03b13f` ("log hot-reload watcher and reload lifecycle") added `features/server/hot_reload_logging.feature` "server start logs the hot-reload watcher it activated", which asserts the watcher IS started (`:config.watch/started` logged). So the fix MUST keep the watcher started — simply not handing the memory-source to `app/start!` makes that scenario fail (verified: 44/0 -> 47/1).

## Candidate approaches (pick one; verify both constraints)
1. **Single consumer**: make the async watcher the SOLE reloader and have the test AWAIT it (drop `sync-config-reload!`), OR make the sync drain the sole reloader while still STARTING the watcher for its activation log only.
2. **Separate sources**: give the watcher its own (never-notified) source so it logs activation but never reloads, and keep `sync-config-reload!` as the deterministic reloader on the real source. CAVEAT: confirm no `hot_reload_logging.feature` scenario asserts the watcher actually REACTING to a change (only activation) — grep that feature.
3. **Fix the async reload's nexus binding**: ensure the watcher-thread `reload!` reconciles the live nexus the same way the sync path does (closes the race-result divergence even if both run). More invasive (reloader threading).

## Acceptance
- `cd isaac-server && clojure -M:test:features` deterministically green across many runs (no flaky hot-reload scenario), with `target/gherclj` cleaned between runs.
- `features/server/hot_reload_logging.feature` stays green (watcher activation still logged).
- isaac-discord `lifecycle.feature` "adding a Discord token mid-run starts the client" runs deterministically once isaac-discord repins the fixed server-spec/test-support (and the @wip is removed there).

## Scope / repos
- isaac-server (spec/isaac/server/server_steps.clj — test harness; possibly the watcher/reloader if approach 3).
- Follow-up in isaac-discord: remove the @wip from the lifecycle scenario after repinning.

Surfaced 2026-06-26 from isaac-yy88.

## Implementation (work-2, 2026-06-26)

**Approach 1 (single consumer, sync path):** Added `:start-config-reloader?` to `app/start!` (default true). Feature harness `server-running` passes `false` so `sync-config-reload!` is the sole `poll!` consumer; watcher still starts via `runtime/start!` on the change source for activation logging.

**SHAs pushed:**
- isaac-server `f060735` — harness + `app/start!` opt
- isaac-foundation `6e81f78` — `isaac-edn-file-exists` merges into existing file (partial writes no longer wipe config)
- isaac-discord `abf6973` — repinned server/foundation, removed `@wip` on lifecycle add/remove scenarios, fixed log row order (`:discord.client/*` logs during reconcile before `:config/reloaded`)

**Verified locally:** `isaac-server` `bb spec` 156/0; `clojure -M:test:features` 47/0 ×5 (clean `target/gherclj` between runs); `isaac-discord` `bb spec` 66/0 + `clojure -M:test:features` 47/0 (pinned deps, no dev-local).
