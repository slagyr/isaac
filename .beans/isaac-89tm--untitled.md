---
# isaac-89tm
title: Untitled
status: todo
type: task
created_at: 2026-06-27T17:40:26Z
updated_at: 2026-06-27T17:40:26Z
---

---
# isaac-dcmp
title: 'Default compaction configuration must always be present; never skip if not configured'
status: todo
type: bug
priority: high
created_at: 2026-06-27T18:00:00Z
updated_at: 2026-06-27T18:00:00Z
---

## Summary
Compaction is essential for managing context size and token usage in sessions. If :compaction (strategy, threshold, head, etc.) is not explicitly configured in crew or session, it must not be skipped. There must always be a system default applied.

## Problem
- Crews like prowl, scrapper, perceptor lack :compaction in their .edn (only marvin has {:strategy :slinky, :threshold "0.8"}).
- Global isaac.edn has no :defaults :compaction.
- Sessions end up with compaction-count=0 despite high token usage.
- Code provides some internal defaults in resolve (rubberband, threshold from ctx), but they are not enforced/applied uniformly when no config present.
- "We can't skip it if it's not configured" — default must kick in always.

## Root cause
Compaction config resolution in session/context.clj and compaction.clj only merges override if present; no mandatory default at loader/crew level for all cases. Crew templates and global config do not guarantee it.

## Acceptance criteria
- Add :compaction default to isaac.edn under :defaults (e.g. strategy :slinky or rubberband, threshold 0.8, head 0.2).
- Ensure crew .edn templates (including prowl/scrapper/perceptor) include or inherit default.
- Resolution always produces a valid compaction config (never nil/empty that causes skip).
- Update session creation/store to apply default.
- Existing unconfigured sessions start compacting.
- Tests/docs confirm default is always active.
- Bean for related: wire compaction-schema properly (see existing isaac-rmc4).
