---
# isaac-cfty
title: 'isaac-agent CI red: forward-fix compaction + template/comm_send pileup'
status: completed
type: bug
priority: high
created_at: 2026-06-24T00:55:25Z
updated_at: 2026-06-24T01:32:35Z
---

isaac-agent main has been red since hgf6 (20fb5dd); ~11 more commits merged on top of red CI. ~25 feature failures in 2 clusters. Spec green (1068/0). Forward-fix to green (work-1 owns; no one else on agent).

## Clusters
- C1 hgf6 compaction (8): Context Management(7)+compaction_memory_flush(1). estimate-based decision vs stale context-window:100 fixtures (prompt floor ~104).
- C2 later commits (~17): ca59165 prompt-root relocation + 9d13ad5/ad1a3b6 template renderer shift system-prompt size (tips Async/Strategies/Logging compaction scenarios + breaks Crew Command); e1946e7 comm_send tool ordering.

## Plan
- [ ] Pin down template/prompt-root prompt-size change; fix Crew Command
- [x] Tool-order: stale fixture — corrected to real order memory_write/memory_get/memory_search
- [x] context_management.feature GREEN (recipe proven: large window for don't-compact; cw~200+head 0.1+beefed transcript for compact; cw<172 for truncation)
- [x] Recalibrate remaining compaction feature files (logging, async, strategies, memory_flush, comm/memory, bridge/cli-prompt, bridge/logging)
- [x] bb ci green locally: spec 1068/0, features 529/0 (was 26 feature failures)
- [ ] push; CI green

## Summary of Changes

Fixed all 26 isaac-agent feature failures; CI green on dc9d1a6 (run 28068852046).

Root cause: hgf6 (20fb5dd) switched the compaction trigger to a live outbound-prompt estimate with a fixed ~104-token system-prompt floor (compaction only trims the transcript, not the floor). Fixtures using tiny context-windows (20/60/100) sat below the floor → compaction looped without progress and corrupted transcripts (grover echoes raw input when its queue drains). hgf6 was verified with bb spec only, never bb features.

Fix (pure test recalibration, no production code change):
- don't-compact scenarios → large context-window
- compact scenarios → context-window ~160-200 + per-session compaction.head 0.1 + transcript sized so one compaction makes progress
- truncation scenarios → context-window < tool-result-len/1.2
- corrected 2 stale fixtures: built-in tool order memory_write/memory_get/memory_search; shared CLI table renderer emits no underline rule row

context_management.feature done by hand to prove the recipe; remaining 8 files parallelized across subagents. bb ci: spec 1068/0, features 529/0. Pushed ad1a3b6..dc9d1a6.

Process note: this grew 8→26 because workers merged onto already-red main for ~2 days. Recommend a no-merge-on-red policy.
