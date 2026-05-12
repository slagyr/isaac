---
# isaac-5776
title: "Fix config composition malformed EDN assertion"
status: completed
type: bug
priority: normal
created_at: 2026-04-23T19:36:43Z
updated_at: 2026-04-23T20:06:03Z
---

## Description

`bb features` currently fails in `features/config/composition.feature` on the scenario `malformed EDN in a config file is reported with the file path`. Investigate whether the config loader now surfaces a different parse message than the approved feature expects, or whether the feature assertion path is wrong, and restore the approved behavior.

## Notes

Completed with bb spec green, features/config/composition.feature green, and features/config/hot_reload.feature still green. Full bb features still has one unrelated ACP Tool Calls failure; tracked separately in isaac-32jx.

