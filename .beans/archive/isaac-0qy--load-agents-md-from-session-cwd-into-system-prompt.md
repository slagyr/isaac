---
# isaac-0qy
title: "Load AGENTS.md from session cwd into system prompt"
status: completed
type: feature
priority: high
created_at: 2026-04-14T15:11:41Z
updated_at: 2026-04-14T17:53:48Z
---

## Description

The drive must include project boot files (AGENTS.md) from the session's cwd in the system prompt alongside the soul. This gives crew members project context — conventions, tools, workflow — without requiring it in the transcript.

System message = soul + AGENTS.md (if present). Rebuilt every turn from current files. Not stored in transcript.

features/session/boot.feature — 2 scenarios

New step definitions needed:
- the file {path} exists with: (doc string)
- the system prompt contains {text}
- the system prompt does not contain {text}
- sessions exist table accepts cwd column

## Acceptance Criteria

Both @wip scenarios in features/session/boot.feature pass with @wip removed. AGENTS.md content appears in system prompt. Missing AGENTS.md is handled gracefully.

