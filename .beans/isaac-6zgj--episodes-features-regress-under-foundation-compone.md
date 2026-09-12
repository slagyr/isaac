---
# isaac-6zgj
title: Episodes features regress under Foundation component-runtime pin
status: in-progress
type: bug
priority: high
tags:
    - episodes
    - foundation
created_at: 2026-09-11T21:13:19Z
updated_at: 2026-09-12T16:01:06Z
---

Discovered while verifying isaac-oc3f.

Episodes feature coverage regresses when Foundation advances from pre-component `e0dc789` to component-runtime `8b4a33b`, independently of the Episodes component cutover.

Proof: detached `isaac-episodes` `origin/main@19c48d6` with **only** `deps.edn` / `bb.edn` Foundation-family pins changed to `8b4a33b` reproduces exactly the three failures seen on `bean/isaac-oc3f@ec2fc54`:

- `recall/embedding.feature:88`: unknown provider validation expected on stderr
- `recall/embedding.feature:100`: unknown embedding source validation expected on stderr
- `episodes/recall_logging.feature:53`: expected `:recall/scene` log absent

Run: `ISAAC_TEST_TIMEOUT_MS=180000 bb features` → 78 examples, 3 failures, 515 assertions.

Investigate and restore feature compatibility with Foundation 0.1.25+ without coupling the repair to the lifecycle berth migration.

## Work checkpoint (scrapper@isaac-work-1)

RED confirmed on `origin/main@089a764` with Foundation `8b4a33b`: the three focused scenarios report 3 failures/4 assertions. Investigation isolated the two config-validation failures to Foundation `c305e13`: `isaac.main` threads only `:config` into command opts and `isaac.config.cli.common/load-result` fabricates an empty-error result, discarding the already-computed loader errors. The recall log remains red after awaiting the turn and after proving the Episodes tool berth is registered; it requires further charge/tool-dispatch tracing. No source edits are currently pending. Next: add a Foundation regression spec for preserving the threaded full load result, then implement that seam; separately trace the recall tool's charge `:module-index`. Resume at Foundation `src/isaac/main.clj:121` / `src/isaac/config/cli/common.clj:244`, then Episodes `spec/isaac/episodes/episode_steps.clj:28`.

## Work checkpoint (scrapper@isaac-work-1, cache-source tracing)

Done: real-path tracing proves Foundation resolves an Episodes module index, but the prompt load still sees Cordelia without the newly written `:tools.allow`; the turn therefore logs `allowed-tools nil` and `unknown tool: recall__scene`. Agent prompt CLI now preserves threaded config (`bean/isaac-6zgj@a4862b5`; focused prompt spec green: 30 examples, 75 assertions). Foundation config-error preservation remains at `bean/isaac-6zgj@80ee20e`. The latest cross-repo recall scenario is RED: 1 example, 1 failure, 2 assertions.

Next: TDD the startup-cache source witness. `write-classpath-cache!` currently omits `load-result :sources`, so `read-pre-sub` cannot invalidate when a crew entity file changes after `episodes index` writes the cache. Add RED coverage that cache payloads persist source paths and reject a newer source; thread sources from `main/run`, then rerun the focused recall scenario. Resume at `isaac-foundation/src/isaac/startup/classpath_cache.clj:67`, `src/isaac/startup/config_cache.clj:20`, and `src/isaac/main.clj:152`.
