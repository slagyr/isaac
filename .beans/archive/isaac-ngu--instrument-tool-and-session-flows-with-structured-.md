---
# isaac-ngu
title: "Instrument tool and session flows with structured logs"
status: completed
type: task
priority: high
created_at: 2026-04-08T16:52:54Z
updated_at: 2026-04-08T18:09:57Z
---

## Description

Add structured EDN logging to tool execution, session lifecycle, and context management flows.

## Scope
- Log tool execution start/result/error with tool and session context
- Log session create/resume lifecycle events
- Log compaction and transcript persistence failures
- Preserve structured context in all failure logs
- Add or update specs/features where logging behavior is part of the contract

## Reviewed feature expectations
- Acceptance tests should be able to configure logging with:
  Given config:
    | key        | value  |
    | log.output | memory |
- Establish the logging assertion pattern with a rotated table matcher:
  Then the log has entries matching:
    | level  | event                | tool |
    | :error | :tool/execute-failed | read |
- First feature target should be a deterministic tool failure scenario in features/tools/execution.feature
- File and numeric line should be assertable via regex columns when appropriate

## Worker note
- This feature direction has been reviewed and approved
- Use the config table pattern instead of a dedicated in-memory logging step
- Do not re-close this bead as duplicate/complete without implementing the approved feature updates and corresponding tests

## Notes
- Include file/line capture via the logging macros
- Prefer in-memory log sinks in specs/features over filesystem assertions where possible

