---
# isaac-wma
title: "Remove Anthropic OAuth code and features"
status: completed
type: task
priority: normal
created_at: 2026-04-04T03:57:39Z
updated_at: 2026-04-07T23:09:44Z
---

## Description

Anthropic OAuth via api.anthropic.com is not supported. The SDK approach (isaac-sdk) replaced it. Remove dead OAuth code.

## Files to remove or clean
- src/isaac/auth/oauth.clj — entire file (credential reading, token refresh)
- spec/isaac/auth/oauth_spec.clj — entire file
- features/providers/anthropic/auth_oauth.feature — entire file
- spec/isaac/features/steps/providers.clj — remove OAuth-related steps

## Files to clean up
- src/isaac/llm/anthropic.clj — remove OAuth auth path from auth-headers
- src/isaac/cli/auth.clj — remove OAuth login flow, keep API key and status/logout
- spec/isaac/llm/anthropic_spec.clj — remove OAuth-related specs
- spec/isaac/cli/auth_spec.clj — remove OAuth-related specs
- spec/isaac/features/steps/auth.clj — remove OAuth-related steps

## Beads to close
- isaac-8ut (Anthropic OAuth with beta headers) — no longer needed
- isaac-0wt (Anthropic OAuth credential reuse) — already closed, code being removed

