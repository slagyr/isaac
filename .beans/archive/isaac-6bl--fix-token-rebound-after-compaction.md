---
# isaac-6bl
title: "Fix token rebound after compaction"
status: completed
type: bug
priority: high
created_at: 2026-04-09T13:54:59Z
updated_at: 2026-04-09T17:03:06Z
---

## Description

Compaction currently updates only :totalTokens in the session index. On the next assistant response, update-tokens! recomputes :totalTokens from stale :inputTokens/:outputTokens, causing the total to jump back above threshold and re-trigger compaction every turn.

Feature: features/context/compaction.feature (@wip) 'Successful compaction does not immediately re-trigger on the next user turn'

Definition of Done:
- After compaction, token accounting no longer rebounds to stale pre-compaction totals
- A second user turn does not compact again unless context actually exceeds threshold
- @wip tag removed from the scenario
- bb features and bb spec pass

## Acceptance Criteria

1. The @wip tag is removed from 'Successful compaction does not immediately re-trigger on the next user turn' in features/context/compaction.feature
2. That specific scenario runs (not skipped) and passes in bb features
3. bb spec passes
4. No other tests broken

