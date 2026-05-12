---
# isaac-modj
title: "Make spec-structure-check runnable in this repo"
status: draft
type: task
priority: normal
tags:
    - "deferred"
created_at: 2026-04-25T10:29:07Z
updated_at: 2026-04-25T10:29:07Z
---

## Description

The project guidance says to run the Speclj structure checker after every spec edit, but the documented clj path is not currently runnable in this repository. Add a real repo-root command that works with the project classpath so the workflow is enforceable instead of aspirational.

## Acceptance Criteria

1. A repo-root command exists for spec structure checking on a file or directory. 2. The command works for spec/isaac/session/bridge_spec.clj and spec/isaac/cli/chat_spec.clj without manual classpath repair. 3. The working command is documented in project guidance.

## Notes

Deferred cleanup from PLANNING.md tooling guidance.

