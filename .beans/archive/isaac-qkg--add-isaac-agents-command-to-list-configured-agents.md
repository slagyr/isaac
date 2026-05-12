---
# isaac-qkg
title: "Add isaac agents command to list configured agents"
status: completed
type: feature
priority: normal
created_at: 2026-04-13T01:55:06Z
updated_at: 2026-04-13T02:17:36Z
---

## Description

## Summary

`isaac agents` lists configured agents with their name, model, provider, and soul source.

## Output format

```
Name     Model   Provider   Soul
main     echo    grover     ~/.isaac/agents/main/SOUL.md
ketch    echo    grover     ~/.isaac/agents/ketch/SOUL.md
```

Inline souls show truncated text, file-based souls show the path.

When no agents are explicitly configured, the implicit default 'main' agent is shown.

## Notes

- Agent quarters are moving to `~/.isaac/agents/<name>/` (from `~/.isaac/workspace-<name>/`). This command should use the new layout.
- Soul source resolution uses `resolve-agent-context` from isaac-r7w.

## Acceptance

- `bb features features/cli/agents.feature` passes with @wip removed (3 scenarios)
- @wip removed from all scenarios

## Acceptance Criteria

All 3 @wip scenarios in features/cli/agents.feature pass with @wip removed.

