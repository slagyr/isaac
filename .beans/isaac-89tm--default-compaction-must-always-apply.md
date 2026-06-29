---
# isaac-89tm
title: 'Default compaction configuration must always be present; never skip if not configured'
status: todo
type: bug
priority: high
created_at: 2026-06-27T18:00:00Z
updated_at: 2026-06-27T18:00:00Z
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
