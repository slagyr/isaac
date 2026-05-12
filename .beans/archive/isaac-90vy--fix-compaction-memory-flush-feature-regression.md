---
# isaac-90vy
title: "Fix compaction memory flush feature regression"
status: completed
type: bug
priority: low
created_at: 2026-04-23T02:42:13Z
updated_at: 2026-05-05T23:34:46Z
---

## Description

Full bb features currently fails the scenario 'compaction-turn memory_write calls persist and the summary is produced' in the compaction with memory flush feature. Investigate the regression, restore the approved behavior, and get the scenario passing in the full feature suite.

## Notes

Full bb features passes 492/492 with 0 failures. Memory flush scenario green. Tool loop scenario green. Earlier failure was transient (stale classpath during mid-session provider API surface work; cleared on re-run).

