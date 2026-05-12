---
# isaac-4qn
title: "Propagate LLM errors through channel-agnostic response path"
status: completed
type: task
priority: low
created_at: 2026-04-09T16:06:47Z
updated_at: 2026-04-09T23:37:39Z
---

## Description

Currently LLM errors (connection refused, auth failures, etc.) are printed directly to stdout, which only works for the CLI channel. When Discord, iMessage, web UI, or other channels are added, errors need to be communicated back through the same channel the user is on.

The chat flow should return an error result that channel adapters can present appropriately, rather than printing to stdout directly.

**Why:** Multi-channel support requires a uniform error response mechanism.
**How to apply:** When adding new channels or touching error handling in the chat flow.

