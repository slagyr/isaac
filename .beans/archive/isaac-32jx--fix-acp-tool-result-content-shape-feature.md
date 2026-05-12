---
# isaac-32jx
title: "Fix ACP tool result content shape feature"
status: completed
type: bug
priority: normal
created_at: 2026-04-23T19:54:10Z
updated_at: 2026-04-23T20:10:27Z
---

## Description

bb features still fails in the ACP Tool Calls feature on the scenario "Tool result includes toolCallId, rawOutput, and expandable content". Investigate the current tool_call_update payload shape versus the approved feature expectation and restore the expected ACP tool result structure without regressing the reconnect changes.

## Notes

Verified current workspace behavior: features/acp/tools.feature passes, bb spec passes, and bb features passes. No code changes were required in this session because the issue was already resolved by existing workspace state.
Verified current workspace behavior: features/acp/tools.feature passes, bb spec passes, and bb features has 0 failures. The targeted ACP Tool Calls scenario is no longer failing. Remaining pending scenarios in unrelated bridge/model/crew command areas are outside this bead's scope.

