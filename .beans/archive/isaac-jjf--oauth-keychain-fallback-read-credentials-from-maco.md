---
# isaac-jjf
title: "OAuth Keychain fallback - read credentials from macOS Keychain"
status: completed
type: task
priority: high
created_at: 2026-04-01T20:37:58Z
updated_at: 2026-04-02T00:24:17Z
---

## Description

oauth.clj only reads from ~/.claude/.credentials.json but Claude Code stores tokens in the macOS Keychain.

## Credential Resolution Chain
1. ~/.claude/.credentials.json → claudeAiOauth (current)
2. macOS Keychain → service "Claude Code-credentials" → parse JSON → claudeAiOauth (new)

## Keychain Access
security find-generic-password -s "Claude Code-credentials" -w
Returns JSON string with claudeAiOauth key containing {accessToken, refreshToken, expiresAt}.

## Feature File
features/providers/anthropic/auth_oauth.feature (new scenario: "Read OAuth token from macOS Keychain")

## Also Fixes
The @slow auth login tests in features/auth/commands.feature which fail because there's no credentials file.

