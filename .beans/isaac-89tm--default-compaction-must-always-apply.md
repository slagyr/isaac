---
# isaac-89tm
title: Default compaction configuration must always be present; never skip if not configured
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-06-27T18:00:00Z
updated_at: 2026-06-29T16:00:00Z
---

## Summary
Compaction is essential for managing context size and token usage in sessions. Currently, if :compaction (strategy, threshold, head, etc.) is not explicitly configured in crew or session, it appears to be skipped or ineffective (e.g., compaction-count remains 0 even with massive token counts). There must always be a default compaction configuration applied.

## Problem
- Crews like prowl, scrapper, perceptor lack :compaction in their .edn (only marvin has {:strategy :slinky, :threshold "0.8"}).
- Global isaac.edn has no :defaults :compaction.
- Sessions end up with compaction-count=0 despite high token usage.
- Code in session/store and compaction.clj provides some internal defaults (rubberband, threshold from session-ctx), but these are not reliably applied or triggered without explicit crew/session config.
- Result: no auto-compaction for unconfigured crews/sessions, leading to bloat and high costs.

## Root cause sketch
Compaction config resolution in session/context.clj and compaction.clj only merges override if present; no mandatory default at loader/crew level for all cases. Crew templates and global config do not guarantee it.

## Acceptance criteria
- Add :compaction default to isaac.edn under :defaults (e.g. strategy :slinky or rubberband, threshold 0.8, head 0.2).
- Ensure crew .edn templates (including prowl/scrapper/perceptor) include or inherit default.
- Resolution always produces a valid compaction config (never nil/empty that causes skip).
- Update session creation/store to apply default.
- Existing unconfigured sessions start compacting.
- Tests/docs confirm default is always active.
- Verify with prowl/scrapper/perceptor crews that compaction triggers and reduces context.

## Scenarios (in isaac-agent/features/session/compaction_strategies.feature)
1. "default compaction is used when no compaction key in crew or session config" @wip (features/session/compaction_strategies.feature:24)
   reuses: the isaac EDN file "config/models/local.edn" exists with, the isaac EDN file "config/crew/main.edn" exists with, the following sessions exist, session "no-config-test" has transcript, the following model responses are queued, When the user sends "new input" on session "no-config-test", Then session "no-config-test" has compaction, And session "no-config-test" has 3 active transcript entries; no new steps invented.
   Review: keep. Directly exercises the AC that default compaction must apply (rubberband at 0.8/0.3) even with no :compaction key at all in crew config. Covers the "no auto-compaction for unconfigured crews/sessions" root cause. Fictional content, right abstraction.

## Implementation (work-2, 2026-06-29)

- **isaac-foundation** `0bc526a`: `normalize-config` injects `:defaults :compaction` (`rubberband`, `0.8`, `0.3`); minimal feature config seeds same.
- **isaac-agent** `496c4ca`: `resolve-compaction-config` layer-merges policy keys (session failure counters no longer mask crew/defaults); session create persists resolved compaction; new `session … has compaction` step; `compaction_strategies.feature` scenario green (4 examples).

Verification: `bb spec spec/isaac/session/context_spec.clj` (15, 0); `clojure -M:features features/session/compaction_strategies.feature` (4, 0).

## Verification failed (2026-06-29)
The agent-side proofs are green on the delivered heads:

- `isaac-foundation` `0bc526aa12d23742c122f3b986dd949566923ea1`
- `isaac-agent` `496c4caf80dd3b4d39e94830c16f553614cbe69f`

- `isaac-agent`: `bb spec spec/isaac/session/context_spec.clj` -> `15 examples, 0 failures, 35 assertions`
- `isaac-agent`: `clojure -M:features features/session/compaction_strategies.feature` -> `4 examples, 0 failures, 26 assertions`

But the changed foundation repo is not green on its own head. Running `bb spec` in `isaac-foundation` at `0bc526a` fails with one regression in [spec/isaac/config/loader_spec.clj](/Users/micahmartin/agents/verify/isaac-foundation/spec/isaac/config/loader_spec.clj:896):

- `normalize-config normalizes legacy crew lists nested models and provider vectors`
- expected `(:defaults result)` = `{:crew :main :model :grover}`
- got `{:crew :main :model :grover, :compaction {:async? false, :strategy :rubberband, :head 0.3, :threshold 0.8}}`

So the new default-compaction behavior is real, but the foundation suite still contains an outdated expectation. `89tm` is not verifier-green until that repo is green too.

## Fix (work-2, 2026-06-29)
**isaac-foundation** `c230d54`: update `loader_spec.clj:896` to expect injected `:defaults :compaction` alongside `:crew` and `:model`.
