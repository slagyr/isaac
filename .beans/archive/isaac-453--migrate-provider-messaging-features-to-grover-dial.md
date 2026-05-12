---
# isaac-453
title: "Migrate provider messaging features to grover dialect simulators"
status: completed
type: task
priority: normal
created_at: 2026-04-15T17:07:05Z
updated_at: 2026-04-16T04:13:31Z
---

## Description

Provider messaging features use real provider names with API keys and @slow tags. Migrated to grover:provider-name dialect simulators for fast, keyless, protocol-faithful tests.

- grover:openai — Oscar in his trash can
- grover:anthropic — Elmo in Elmo's world
- grover:grok — The Count counting bats
- grover:ollama — Ernie and his rubber ducky

Requires the grover dialect simulation from isaac-twl to be implemented first.

4 feature files updated.

## Acceptance Criteria

All provider messaging features pass with @wip removed. No real API keys needed. No @slow tags on messaging scenarios.

