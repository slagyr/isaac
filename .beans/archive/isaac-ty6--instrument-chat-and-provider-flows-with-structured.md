---
# isaac-ty6
title: "Instrument chat and provider flows with structured logs"
status: completed
type: task
priority: high
created_at: 2026-04-08T16:52:54Z
updated_at: 2026-04-08T21:56:43Z
---

## Description

Add structured EDN logging to the existing chat and provider boundaries using the logging foundation.

## Scope
- Log chat request lifecycle events at appropriate levels
- Log provider request start/finish/failure with provider/model/session context
- Log streaming lifecycle and terminal outcomes
- Log structured exception data for provider and chat failures
- Add or update specs/features where logging behavior is part of the contract

## Reviewed feature expectations
- Acceptance tests should be able to configure logging with:
  Given config:
    | key        | value  |
    | log.output | memory |
- First feature target should include a deterministic provider failure scenario with:
  Then the log has entries matching:
    | level  | event                 | provider | session |
    | :error | :chat/response-failed | ollama   | ...     |
- Successful chat response storage should log at :debug, not :report
- Streaming completion should log at :debug
- Use the rotated log matcher table style for feature assertions

## Notes
- Supports a future features/chat/logging.feature or equivalent provider-specific coverage
- Include file/line capture via the logging macros
- Use :error, :warn, :report, :info, and :debug appropriately, but prefer :debug for normal chat lifecycle noise

