---
# isaac-xrqv
title: "Fix cron isaac file last-run assertions"
status: completed
type: bug
priority: normal
created_at: 2026-04-23T19:15:36Z
updated_at: 2026-04-23T19:53:06Z
---

## Description

`bb features` currently fails in `features/cron/scheduling.feature` on the scenario `successful cron runs update the isaac file with last-run and last-status`. Investigate whether the EDN isaac file step rename changed path resolution or whether cron state persistence no longer writes the expected `last-run` / `last-status` fields, and restore the approved behavior.

