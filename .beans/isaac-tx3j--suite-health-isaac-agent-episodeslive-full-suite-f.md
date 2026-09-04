---
# isaac-tx3j
title: 'Suite health (isaac-agent): episodes/live full-suite flake at :604'
status: draft
type: bug
priority: high
tags:
    - suite-health
created_at: 2026-09-04T06:54:16Z
updated_at: 2026-09-04T06:54:16Z
---

Ambient full-suite flake on `isaac-agent` that blocks beans whose own surface is elsewhere. Filed 2026-09-04 while adjudicating **isaac-j2v0**.

## Observed (2026-09-04, scrapper@isaac-work-1, `bean/isaac-j2v0` @ `8a79a55`, base `origin/main@2277cb8`)

The parallel tool-batch work is green on its own surface:
- `bb features features/session/parallel_tool_batches.feature features/session/parallel_tool_calls.feature` → `9 examples, 0 failures, 23 assertions`
- `bb features features/comm/scuttlebutt.feature features/llm/tool_loop_driver.feature features/bridge/cancel.feature` → green
- `bb spec spec/isaac/llm/tool_loop_spec.clj spec/isaac/drive` → green
- full `bb spec` → `1625 examples, 0 failures, 3342 assertions, 3 pending`

But the bean's required full `bb features` gate is not suite-stable in this workspace:
- one full run passed: `762 examples, 0 failures, 2034 assertions`
- reruns fail intermittently at `features/episodes/live.feature:604`
- failing scenario: `without embedding, drift is inert but the size cap still seals`
- symptom: expected `1` scene, got `0`
- the same scenario passes in isolation: `bb features features/episodes/live.feature:577`

That is cross-feature state leakage / suite isolation drift, not a parallel tool-batch contract.

## This bean owns

Make the `features/episodes/live.feature` scenario suite-stable without weakening its intent.

1. Reproduce the red on current `isaac-agent` main in a fresh worktree.
2. Identify the leaking prior feature / shared state that causes the full-suite-only failure.
3. Fix the leak in fixture/setup/teardown or product state ownership. Do **not** rewrite the scenario to fit the leak.
4. Record whether the stable gate is `bb features features/episodes/live.feature` alone, unwrapped `clojure -M:features`, or both.

## Acceptance

    cd isaac-agent
    bb features features/episodes/live.feature

0 failures on repeated runs. Then:

    clojure -M:features

unwrapped, repeated enough to show the `features/episodes/live.feature:604` scenario no longer flakes. Record run counts and wall times. If other unrelated reds remain, name them with owning bean ids instead of silently absorbing them.
