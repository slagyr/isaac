---
# isaac-5iv
title: "Surface compaction errors and prevent infinite loop"
status: completed
type: bug
priority: high
created_at: 2026-04-09T03:46:11Z
updated_at: 2026-04-09T04:58:10Z
---

## Description

When compact! returns an error, check-compaction! silently discards the return value. No log entry is written, nothing is shown to the user, and totalTokens stays at the pre-compaction value. On the next message, should-compact? fires again — causing an infinite compaction loop with no LLM response.

## Feature
features/context/compaction.feature — 'Compaction failure is logged and chat proceeds without looping'

## Root Cause
- compact! returns the error map when the LLM call fails (context/manager.clj ~line 35)
- check-compaction! ignores the return value of (ctx/compact! ...) — no error handling (cli/chat.clj ~line 248)
- No log entry with :event :context/compaction-failed is ever emitted

## Fix
- In check-compaction!, inspect the return value of compact!
- If it is an error map, log it at :error level with :event :context/compaction-failed
- Skip the compaction for this turn (proceed to send the user message normally)
- Grover mock may need to support an 'error' response type for the test scenario

## Definition of Done
- @wip removed from compaction.feature 'Compaction failure is logged' scenario
- bb features passes
- bb spec passes

