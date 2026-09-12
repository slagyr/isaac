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
