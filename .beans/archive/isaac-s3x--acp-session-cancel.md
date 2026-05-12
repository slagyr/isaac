---
# isaac-s3x
title: "ACP: session/cancel"
status: completed
type: task
priority: low
created_at: 2026-04-10T21:10:41Z
updated_at: 2026-04-10T22:36:47Z
---

## Description

Handle session/cancel notifications to interrupt in-flight prompt turns.

## Scope
- Handler for `session/cancel` (notification, no response expected)
- Set an interrupt flag on the session
- The in-flight session/prompt handler checks the flag at chunk boundaries and aborts
- The pending session/prompt response returns `{stopReason: 'cancelled'}`

## Notes
Implementation needs to run the prompt dispatch on a background thread (or future) so the main RPC loop can receive and process the cancel notification while the prompt is still executing. This is the first place Isaac's ACP layer needs real concurrency.

Parent epic: isaac-new
Feature file: features/acp/cancellation.feature (1 @wip scenario)

## Acceptance
Remove @wip and verify:
- bb features features/acp/cancellation.feature:18

Full suite: bb features and bb spec pass.

