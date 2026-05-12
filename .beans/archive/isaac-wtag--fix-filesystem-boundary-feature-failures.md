---
# isaac-wtag
title: "Fix filesystem boundary feature failures"
status: completed
type: bug
priority: normal
created_at: 2026-04-22T16:49:02Z
updated_at: 2026-04-22T17:05:01Z
---

## Description

Why: the full bb features run is blocked by four failures in features/tools/filesystem_boundaries.feature, and those failures are outside the scope of cron scheduling work. What: investigate and fix the scenarios for whitelisted directories, :cwd access, and grep boundary enforcement so the broader feature suite is green again.

