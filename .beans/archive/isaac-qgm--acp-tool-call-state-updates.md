---
# isaac-qgm
title: "ACP: tool call state updates"
status: completed
type: task
priority: low
created_at: 2026-04-10T21:10:53Z
updated_at: 2026-04-10T21:55:49Z
---

## Description

Emit session/update notifications for tool call state transitions during a prompt turn.

## Scope
- When a tool call is dispatched, emit a session/update with sessionUpdate: 'tool_call', status: 'pending' (and toolCallId, toolName, input)
- After execution, emit a session/update with sessionUpdate: 'tool_call_update', status: 'completed' (and output/error)
- Optional: emit 'running' between pending and completed

## Open questions
- session/load (session resume) is part of session.feature but not in this bead. If simple enough, could be rolled in here or with isaac-7wk. Otherwise a follow-up bead.
- session/request_permission — for tools that need user consent — deferred until a use case drives it.

Parent epic: isaac-new
Feature file: features/acp/tools.feature (1 @wip scenario)

## Acceptance
Remove @wip and verify:
- bb features features/acp/tools.feature:20

Full suite: bb features and bb spec pass.

