---
# isaac-zy2
title: "Error entries must not poison the prompt with invalid roles"
status: completed
type: bug
priority: high
created_at: 2026-04-14T18:27:52Z
updated_at: 2026-04-14T19:05:25Z
---

## Description

When an LLM request fails, Isaac stores the error in the transcript as role:error. OpenAI rejects this role, causing a cascade: each retry fails, writes another error entry, which poisons the next retry. The session becomes permanently broken. Caused the loss of the quiet-badger session.

Two fixes:
1. Storage: errors stored as type:error (not type:message with role:error)
2. Prompt builder: exclude error entries from the prompt

features/session/error_handling.feature — 2 scenarios

## Acceptance Criteria

Error responses stored as type:error not role:error. Prompt builder skips unrecognized roles. A session with prior errors can still be prompted without cascading failures.

