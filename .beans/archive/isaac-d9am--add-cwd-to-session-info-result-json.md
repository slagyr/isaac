---
# isaac-d9am
title: "Add cwd to session_info result JSON"
status: completed
type: feature
priority: low
created_at: 2026-04-29T15:34:14Z
updated_at: 2026-04-29T15:38:21Z
---

## Description

session_info's result JSON omits :cwd. The session record stores it,
and per-crew filesystem boundaries (read/write/exec) gate on it, so
the LLM benefits from knowing where it's working.

## Spec

features/tools/session_info.feature scenario 1 already updated:

  And the tool result JSON has:
    | path | value         |
    | cwd  | /work/project |
    ...

The 'the following sessions exist:' table now includes a 'cwd'
column for the test session.

## Implementation

src/isaac/tool/builtin.clj `build-session-state` — include
:cwd (:cwd session) in the returned map.

## Read-only by design

session_info is read-only; there is no session_cwd mutator. cwd is
a session-creation-time concern set by the human launching the
session. The agent already has exec :workdir for transient
working-directory shifts that don't escalate filesystem access.

## Definition of done

- features/tools/session_info.feature scenario 1 passes with the
  new cwd assertion
- bb spec and bb features green

