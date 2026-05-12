---
# isaac-8nk
title: "Implement Anthropic and OpenAI integration tests"
status: completed
type: task
priority: low
created_at: 2026-04-09T16:33:07Z
updated_at: 2026-04-09T20:22:46Z
---

## Description

The provider feature files under features/providers/anthropic/ and features/providers/openai/ have @wip scenarios covering auth headers, response parsing, SSE streaming, cache tokens, and tool calling in provider-specific wire formats.

These are integration tests that require real API credentials. Remove @wip and implement the step definitions to make them pass against real provider APIs.

Files:
- features/providers/anthropic/messaging.feature (7 @wip scenarios)
- features/providers/anthropic/auth_api_key.feature (3 @wip scenarios)
- features/providers/openai/messaging.feature (entire feature @wip)
- features/providers/openai/auth.feature (2 @wip scenarios)
- features/providers/grok/auth.feature (2 @wip scenarios)

Requires API keys for Anthropic, OpenAI, and Grok to run.

## Acceptance Criteria

Remove @wip from remaining 3 provider scenarios and verify each passes:

Grok auth (1 scenario):
  bb features-slow features/providers/grok/auth.feature:14

Anthropic messaging (1 scenario):
  bb features-slow features/providers/anthropic/messaging.feature:56

OpenAI messaging (1 scenario):
  bb features-slow features/providers/openai/messaging.feature:27

Ollama integration (feature-level @wip, 2 scenarios):
  bb features-slow features/providers/ollama/integration.feature

Full suite: bb features, bb features-slow, and bb spec all pass with 0 failures.

## Notes

Progress: 8nk worker implemented ~9 scenarios and fixed the OpenAI slow test failure. 6 @wip scenarios remain — response parsing, streaming, and tool calling for Anthropic/OpenAI/Grok, plus all 3 Ollama integration scenarios.

