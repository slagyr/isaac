---
# isaac-8do
title: "Test tool calling flow with Grover"
status: completed
type: task
priority: normal
created_at: 2026-04-09T16:32:59Z
updated_at: 2026-04-09T17:03:41Z
---

## Description

Grover is currently excluded from tool-capable providers, so tool calling is never tested through the production chat flow without hitting real APIs.

Fix:
1. Remove grover from the tool-capable-provider? exclusion set in chat.clj
2. Remove @wip from the tool call scenario in features/session/llm_interaction.feature

Feature: features/session/llm_interaction.feature (@wip) 'Model requests a tool call and receives the result'

Definition of Done:
- Grover supports tool dispatch through process-user-input!
- @wip removed from the scenario
- bb features and bb spec pass

## Acceptance Criteria

1. The @wip tag is removed from 'Model requests a tool call and receives the result' in features/session/llm_interaction.feature
2. That specific scenario runs (not skipped) and passes in bb features
3. bb spec passes
4. No other tests broken

