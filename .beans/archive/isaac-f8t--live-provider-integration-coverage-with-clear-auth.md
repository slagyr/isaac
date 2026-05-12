---
# isaac-f8t
title: "Live provider integration coverage with clear auth-missing failures"
status: completed
type: feature
priority: high
created_at: 2026-04-08T15:03:58Z
updated_at: 2026-04-08T15:16:09Z
---

## Description

Add or refine live integration scenarios for each provider so Isaac verifies real-service compatibility, while failing clearly when required authentication is not available.

## Scope
- Add live messaging/auth smoke coverage for each supported provider in provider-specific feature files
- Ensure scenarios that require real credentials report a clear missing-auth / missing-credential error when auth is unavailable
- Avoid ambiguous generic failures when environment variables, stored tokens, or local login state are missing
- Keep provider-specific live coverage organized under features/providers/*

## Acceptance ideas
- Anthropic live scenarios fail with a clear auth-missing message if ANTHROPIC_API_KEY is unavailable
- OpenAI live scenarios fail with a clear auth-missing message if OPENAI_API_KEY or required login state is unavailable
- OpenAI Codex live scenarios fail with a clear auth-missing message if device-code login has not been completed
- Grok live scenarios fail with a clear auth-missing message if GROK_API_KEY is unavailable

## Notes
- These are slow/integration scenarios
- Real-provider coverage should validate messaging behavior, not just auth setup
- Missing-auth handling is part of the contract and should be explicitly specified

