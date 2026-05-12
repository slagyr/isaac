---
# isaac-se23
title: "Replace ACP harness sleeps with explicit signaling"
status: completed
type: task
priority: normal
created_at: 2026-04-27T15:44:24Z
updated_at: 2026-04-27T17:57:23Z
---

## Description

The ACP feature harness currently uses Thread/sleep in a few places to wait for prompt dispatch, loopback connection establishment, and output visibility. Replace those sleeps with explicit signaling primitives where practical so ACP scenarios become less flaky and less timeout-driven.

## Acceptance Criteria

1. ACP step harness no longer relies on fixed sleeps for prompt dispatch or loopback connection drop timing. 2. Output/response waiting uses explicit event signaling or queue-based delivery instead of StringWriter polling sleeps where practical. 3. ACP feature scenarios continue to pass, including features/acp/tools.feature. 4. bb features and bb spec pass.

## Notes

Follow-up from performance review of ACP feature timings. Goal is deterministic async coordination rather than best-effort delays.

