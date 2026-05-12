---
# isaac-8ut
title: "Anthropic OAuth with beta headers for Max subscription"
status: completed
type: task
priority: low
created_at: 2026-04-04T00:33:41Z
updated_at: 2026-04-07T23:09:32Z
---

## Description

The public api.anthropic.com endpoint rejects OAuth tokens without specific beta headers.

## What's Needed
Same endpoint (api.anthropic.com/v1/messages) but with additional headers:
- Authorization: Bearer <oauth-token> (instead of x-api-key)
- anthropic-beta: claude-code-20250219,oauth-2025-04-20

## How OpenClaw Does It
OpenClaw checks if the API key starts with "sk-ant-oat". If so, injects the beta headers.
See: openclaw/src/agents/pi-embedded-runner/anthropic-stream-wrappers.ts

## Gray Area
Anthropic gates OAuth to Claude Code clients via these beta headers. Using them from Isaac works (OpenClaw does it) but is not officially sanctioned for third-party clients.

## Implementation
In isaac.llm.anthropic/auth-headers:
- Detect OAuth token prefix "sk-ant-oat"
- Add anthropic-beta header with claude-code-20250219,oauth-2025-04-20

## Feature File
features/providers/anthropic/auth_oauth.feature

