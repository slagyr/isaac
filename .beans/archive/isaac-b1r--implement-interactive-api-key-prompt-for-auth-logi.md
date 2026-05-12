---
# isaac-b1r
title: "Implement interactive API key prompt for auth login"
status: completed
type: feature
priority: low
created_at: 2026-04-09T16:56:39Z
updated_at: 2026-04-09T18:16:54Z
---

## Description

The 'auth login --provider anthropic --api-key' command should prompt the user to enter their API key interactively, then store it.

Feature: features/auth/commands.feature (@wip) 'Login with Anthropic API key'

Definition of Done:
- Interactive prompt reads API key from stdin
- Credential is stored via auth/store
- @wip removed from the scenario
- bb features and bb spec pass

## Acceptance Criteria

Remove @wip and verify:
  bb features features/auth/commands.feature:9

Full suite: bb features and bb spec pass.

