---
# isaac-c4g
title: "Record LLM errors as transcript entries"
status: completed
type: bug
priority: normal
created_at: 2026-04-09T23:37:34Z
updated_at: 2026-04-09T23:47:04Z
---

## Description

LLM errors (connection refused, auth failures, etc.) are currently logged and printed to stdout but not recorded in the session transcript. Since the transcript is the single source of truth that all UIs read from, errors need to be entries in the transcript.

The chat flow should append an error entry (type: message, role: error) to the transcript when the LLM fails, rather than just logging and printing.

Feature: features/session/llm_interaction.feature (@wip) 'LLM errors are recorded in the session transcript'

Supersedes isaac-4qn (channel-agnostic error propagation) — this is the implementation.

## Acceptance Criteria

1. Remove @wip from 'LLM errors are recorded in the session transcript'
2. bb features features/session/llm_interaction.feature:67 passes
3. bb features and bb spec pass

