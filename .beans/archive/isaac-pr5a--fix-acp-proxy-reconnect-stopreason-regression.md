---
# isaac-pr5a
title: "Fix ACP proxy reconnect stopReason regression"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T17:56:34Z
updated_at: 2026-04-28T19:35:52Z
---

## Description

The full feature suite currently has an unrelated failure outside isaac-aept: features covering ACP proxy reconnect report result.stopReason is nil when a request arrives during disconnect and waits for reconnect. Restore the expected end_turn stopReason for the reconnect path and add/adjust coverage as needed.

## Notes

Verification failed: the bead's target scenario still fails. Full bb features and targeted bb features features/acp/reconnect.feature:41 both fail with result.stopReason nil instead of end_turn for 'a request arriving during disconnect waits for reconnect and then completes'. bb spec passes, and the added step-level regression spec in spec/isaac/features/steps/acp_spec.clj only proves acp-agent-sends-response can skip an earlier same-id error in a simplified writer case. It does not make the real reconnect feature pass, so the acceptance criteria are not satisfied.

