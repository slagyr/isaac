---
# isaac-2va
title: "Cancelled tool calls must send tool_call_update with status:cancelled"
status: completed
type: bug
priority: normal
created_at: 2026-04-15T03:51:05Z
updated_at: 2026-04-15T04:12:54Z
---

## Description

When a turn is cancelled while a tool call is in flight, the agent sends the cancelled response but never sends tool_call_update with status:cancelled. The client UI stays stuck showing the pending hourglass on the tool call.

The cancel path in the bridge/channel needs to emit a tool_call_update notification with status:cancelled for any in-flight tool call before returning the cancelled response.

features/acp/cancel_tool_status.feature — 1 scenario

## Acceptance Criteria

@wip scenario passes with @wip removed. Toad/IDEA clears the tool call pending indicator on cancel.

