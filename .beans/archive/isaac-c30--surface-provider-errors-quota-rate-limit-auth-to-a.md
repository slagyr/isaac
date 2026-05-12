---
# isaac-c30
title: "Surface provider errors (quota, rate limit, auth) to ACP client"
status: completed
type: bug
priority: normal
created_at: 2026-04-15T06:09:50Z
updated_at: 2026-04-15T14:04:57Z
---

## Description

When OpenAI returns 429 quota exceeded (or any provider error), the ACP client sees a generic 'Error during prompt turn' with no explanation. The provider's error message must flow through to the client as a readable stopReason:error with the message.

features/acp/provider_errors.feature — 2 scenarios

## Acceptance Criteria

Both @wip scenarios pass with @wip removed. Quota and connection errors show readable messages in IDEA/Toad.

