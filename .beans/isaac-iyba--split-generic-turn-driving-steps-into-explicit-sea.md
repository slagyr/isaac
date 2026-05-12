---
# isaac-iyba
title: "Split generic turn-driving steps into explicit seams and waiting steps"
status: draft
type: task
priority: low
tags:
    - "deferred"
created_at: 2026-04-25T10:29:09Z
updated_at: 2026-04-25T10:29:10Z
---

## Description

Several feature steps hide too much behavior behind generic phrasing such as `the user sends ... on session ...`. Audit the turn-driving steps and separate seam selection from waiting so the feature text says which product path is being exercised and whether it is awaiting completion.

## Acceptance Criteria

1. Transport/seam-specific steps are explicit where behavior differs. 2. Waiting/awaiting semantics are separated from send/action steps where practical. 3. Feature text becomes clearer about whether it is exercising bridge, CLI, ACP, or memory-channel paths.

## Notes

Deferred cleanup from PLANNING.md step-discipline guidance.

