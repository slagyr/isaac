---
# isaac-jv3
title: "Wire tool result truncation into prompt builder"
status: completed
type: bug
priority: normal
created_at: 2026-04-09T16:41:14Z
updated_at: 2026-04-09T17:03:32Z
---

## Description

truncate-tool-result exists in context/manager.clj but nothing in the production path calls it. Large tool results pass through to the LLM untruncated, potentially exceeding the context window.

Wire truncation into the prompt builder so large tool results are truncated using the head-and-tail strategy before the prompt is sent.

Feature: features/session/context_management.feature (@wip) 'Large tool results are truncated in prompts'

Definition of Done:
- truncate-tool-result is called during prompt building for toolResult messages
- @wip removed from the scenario
- bb features and bb spec pass

## Acceptance Criteria

1. The @wip tag is removed from 'Large tool results are truncated in prompts' in features/session/context_management.feature
2. That specific scenario runs (not skipped) and passes in bb features
3. bb spec passes
4. No other tests broken

