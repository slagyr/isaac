---
# isaac-ypwz
title: "Make ACP feature prompt requests synchronous by default"
status: completed
type: task
priority: normal
created_at: 2026-04-27T17:29:00Z
updated_at: 2026-04-27T17:57:23Z
---

## Description

ACP feature scenarios that use direct session/prompt requests currently dispatch through a background future even when the product path is fast and deterministic. Split the harness so normal ACP request steps run synchronously, and keep an explicit async request step only for cancellation, reconnect, and ordering scenarios that truly need background behavior.

## Acceptance Criteria

1. The default ACP request step runs synchronously for direct feature-harness dispatch. 2. An explicit async ACP request step exists for scenarios that need background behavior. 3. Cancellation/reconnect/ordering ACP scenarios still pass with the async step. 4. The slow ACP connection-refused scenarios become measurably faster. 5. bb features and bb spec pass.

## Notes

Follow-up from ACP timing analysis: direct product path is ~9ms; feature harness async coordination dominates wall time.

