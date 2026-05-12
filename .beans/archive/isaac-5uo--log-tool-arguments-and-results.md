---
# isaac-5uo
title: "Log tool arguments and results"
status: completed
type: task
priority: normal
created_at: 2026-04-10T03:12:16Z
updated_at: 2026-04-10T03:24:37Z
---

## Description

Tool execution logs only include the tool name. Arguments and results are missing, making the logs useless for debugging.

Add arguments to :tool/start and result preview to :tool/result. Truncate large results (e.g. first 200 chars) to avoid blowing up logs.

Feature: features/tools/execution.feature (2 @wip scenarios)

## Acceptance Criteria

Remove @wip from both scenarios and verify:
  bb features features/tools/execution.feature:11
  bb features features/tools/execution.feature:22

Full suite: bb features and bb spec pass.

