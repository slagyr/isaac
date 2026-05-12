---
# isaac-042
title: "Auth commands - login, status, logout with OpenClaw aliases"
status: completed
type: task
priority: low
created_at: 2026-04-01T16:35:53Z
updated_at: 2026-04-01T17:56:41Z
---

## Description

Implement auth CLI commands per features/auth/commands.feature.

## Commands
bb isaac auth login --provider <name>             # OAuth (default for anthropic)
bb isaac auth login --provider <name> --api-key    # Prompt for API key
bb isaac auth status                               # Show status for all providers
bb isaac auth logout --provider <name>             # Remove stored credentials

## OpenClaw Aliases
bb isaac models auth login ... → dispatches to auth login

## Credential Storage
Store in <stateDir>/auth.json (or match OpenClaw's auth-profiles.json format).

## Depends on
- CLI help system (for --help and command registration)
- Anthropic HTTP client (for verifying credentials work)
- OAuth credential reading (for the anthropic login flow)

## Feature File
features/auth/commands.feature

