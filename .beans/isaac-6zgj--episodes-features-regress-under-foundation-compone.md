---
# isaac-6zgj
title: Episodes features regress under Foundation component-runtime pin
status: in-progress
type: bug
priority: high
tags:
    - episodes
    - foundation
    - unverified
created_at: 2026-09-11T21:13:19Z
updated_at: 2026-09-12T17:33:06Z
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

## Implementation evidence (scrapper@isaac-work-1)

Root causes and repairs:

- Foundation CLI config threading discarded resolved validation errors. `isaac.main` now carries the full `:load-result`, and CLI config consumers preserve it.
- Foundation startup cache watched only root config and local module manifests. It now persists resolved config `:sources`, resolves relative source paths beneath the Isaac root, and invalidates when an entity source changes. This prevents a preceding command from hiding a later `config/crew/*.edn` tools update.
- Agent prompt CLI reloaded config instead of consuming Foundation's resolved `:config`. It now validates/installs the threaded config, preserving both crew tools and `:module-index` into the charge.

Branches (rebased on current origin/main):

- `isaac-foundation bean/isaac-6zgj @ 47fc2ccbaddf018cacb0dbfa0873ab902f65619b` (base `origin/main@bd9dd027b9400bde8509f026e888cdb99ba3e6e8`)
- `isaac-agent bean/isaac-6zgj @ a4862b5e1a757a67b535c1a5a3418432eaf055e8` (base `origin/main@a6c27f893192d7fd76c883f7e007a9c33fc92bf9`)
- Episodes needs no source change; it is the cross-repo acceptance consumer.

Verification:

- Foundation `bb spec`: 1022 examples, 0 failures, 1844 assertions.
- Foundation `features/cli/config_resolution.feature`: 6 examples, 0 failures, 8 assertions.
- Foundation full `bb features`: 185 examples; only 2 unrelated `modules_pins.feature` environment failures from stale `/Users/zane/.gitlibs/_repos/file/REL/fixture-agent` pointing at a removed verify checkout.
- Agent prompt CLI spec: 30 examples, 0 failures, 75 assertions.
- Agent full `bb spec` was run repeatedly; each run had one different pre-existing async `session_steps_spec` flake. Both observed examples pass focused (`session_steps_spec.clj:100` and `:150`).
- Episodes `bb spec` against both branches: 205 examples, 0 failures, 548 assertions.
- Episodes full `bb features` against both branches: 78 examples, 0 failures, 520 assertions.
- Original three regressions focused against both branches: 3 examples, 0 failures, 9 assertions.
- Recall logging also passes with only Foundation fixed and the original Agent pin, proving the accepted fix is independent of Episodes lifecycle migration.

Resume verification at `isaac-foundation/src/isaac/startup/config_cache.clj:20` and `isaac-agent/src/isaac/bridge/prompt_cli.clj:116`.
