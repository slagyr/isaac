---
# isaac-bom
title: "Add isaac sessions command to list stored sessions"
status: completed
type: feature
priority: normal
created_at: 2026-04-13T01:46:02Z
updated_at: 2026-04-13T02:09:07Z
---

## Description

## Summary

`isaac sessions` lists stored conversation sessions grouped by agent. Each session shows key, age, model, and context usage.

## CLI interface

- `isaac sessions` — list all sessions, grouped by agent
- `isaac sessions --agent ketch` — filter to one agent
- `isaac help sessions` — show usage

## Output format

```
agent: main
  Key                              Age       Model              Context
  agent:main:acp:direct:1be3f7f9   2h ago    grok-4-1-fast      5,000 / 32,768 (15%)
  agent:main:acp:direct:4aff2750   5h ago    qwen3-coder:30b    778 / 32,768 (2%)

agent: ketch
  Key                              Age       Model              Context
  agent:ketch:acp:direct:abc123    1d ago    grok-4-1-fast      12,000 / 32,768 (37%)
```

## Implementation

- Register `sessions` command in `src/isaac/cli/sessions.clj`
- Use `storage/list-sessions` to fetch sessions per agent
- Format age as relative time (Xm/Xh/Xd ago)
- Model and context info from session index entry

## Acceptance

- `bb features features/cli/sessions.feature` passes with @wip removed (5 scenarios)
- @wip removed from all scenarios

## Acceptance Criteria

All 5 @wip scenarios in features/cli/sessions.feature pass with @wip removed.

