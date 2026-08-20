---
# isaac-qxvl
title: 'Live episodes 4a: router, TTL lifecycle, compaction-close, seal-at-close'
status: todo
type: task
created_at: 2026-08-20T23:16:17Z
updated_at: 2026-08-20T23:16:17Z
parent: isaac-51xy
---

Phase 1.5 bean 4a per isaac-51xy decisions 19-20, 26-29. Episodes as managed sessions for :conversation :episodes pilot crews: router at dispatch entry (warm append / cold close+open with :parent-episode lineage), :episodes {:ttl-minutes 60}, compaction closes episode and seeds successor transcript with the summary, seal-at-close via existing segmentation machinery (flagged/partial reuse), :thread on the open episode record, lazy close + explicit episodes close CLI. No recall (4b), no mid-episode scene sealing (bean 5). Sessions untouched for non-pilot crews.
