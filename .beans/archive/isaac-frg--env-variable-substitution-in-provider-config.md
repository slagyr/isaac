---
# isaac-frg
title: "Env variable substitution in provider config"
status: completed
type: task
priority: normal
created_at: 2026-04-01T16:05:30Z
updated_at: 2026-04-01T16:08:18Z
---

## Description

Support ${ENV_VAR} syntax in provider config values, matching OpenClaw convention.

## Syntax
${VARIABLE_NAME} in any string value is replaced with the env var's value at config load time.

## Usage
Provider config:
  apiKey: "${ANTHROPIC_API_KEY}"

## Behavior
- ${VAR} resolves to env value
- Missing env var: leave as empty string or error? (decide during implementation)
- Nested substitution not needed
- Only applies to string values in config

## Where
isaac.config.resolution namespace — apply substitution during load-config or resolve-provider.

