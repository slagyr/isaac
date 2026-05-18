---
# isaac-q9b0
title: ACP turn cancellation does not work
status: completed
type: bug
priority: normal
tags:
    - deferred
created_at: 2026-05-01T16:09:47Z
updated_at: 2026-05-18T17:26:15Z
blocked_by:
    - isaac-yr1x
    - isaac-0c9x
    - isaac-y0s2
---

## Description

## Symptom

Cancelling a running turn via ACP (session/cancel) does not actually
cancel the turn. The model continues streaming and the assistant
response lands in the transcript as if no cancel had been issued.

## Reproduction

Drive a turn via an ACP client, send a session/cancel mid-stream
before the turn completes, observe that:
  - the turn keeps running to completion
  - the final assistant message is appended to the transcript
  - no cancellation acknowledgement is observed

## Likely areas to investigate

- isaac.acp / session/cancel handler — does it dispatch to
  bridge/cancel! (or whatever the in-process equivalent is)?
- isaac.drive.turn cancellation path — the in-memory step
  'the turn is cancelled on session {key}' uses bridge/cancel!;
  does the ACP path reach the same machinery?
- Is the cancel notification being received at all, or is it being
  swallowed before reaching the turn loop?

## Notes

- In-memory turn cancellation (the 'the turn is cancelled on session'
  step) appears to work in unit tests — implies the bug is in the
  ACP-to-turn wiring, not the cancellation primitive.
- Filed deferred per request — pick up when ACP cancel becomes a
  blocker for clients.


## Summary of Changes

ACP `session/cancel` handler at `src/isaac/comm/acp/server.clj:158-162` properly dispatches to `bridge-cancel/cancel!`, reaching the same in-process cancellation machinery as the unit-test path. Coverage in `features/acp/cancellation.feature` — two scenarios, both green:

- `session/cancel during a turn stops processing` — verifies the in-flight `session/prompt` response carries `stopReason: cancelled`
- `session/cancel arrival is logged at info` — verifies `:acp/session-cancel-received` log event

`bb features features/acp/cancellation.feature` → 2 examples, 0 failures, 3 assertions.

Blockers (isaac-yr1x, isaac-0c9x, isaac-y0s2) all resolved upstream.
