---
# isaac-0wt
title: "Anthropic OAuth - Claude Code credential reuse and token refresh"
status: completed
type: task
priority: low
created_at: 2026-04-01T16:21:11Z
updated_at: 2026-04-01T16:46:19Z
---

## Description

Implement OAuth authentication for Anthropic per features/providers/anthropic/auth_oauth.feature.

## Credential Reading
Read from ~/.claude/.credentials.json under key "claudeAiOauth":
- accessToken: the bearer token
- refreshToken: for renewal
- expiresAt: epoch ms

Fallback: macOS Keychain service "Claude Code-credentials" (stretch goal)

## Token Refresh
If expiresAt < now, use refreshToken to get a new accessToken.
Standard OAuth token refresh endpoint.
Write refreshed credentials back to ~/.claude/.credentials.json.

## Request Header
Authorization: Bearer <accessToken>

## Error Cases
- No credentials file: error "no OAuth credentials found"
- Expired + invalid refresh token: error "OAuth refresh failed"

## Testing
Fast scenarios mock the credentials file and refresh endpoint.
@slow integration test requires Claude Code to be logged in.

## Feature File
features/providers/anthropic/auth_oauth.feature

