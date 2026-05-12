---
# isaac-zpxl
title: "Delete dead cli/agents.clj and drop agents command"
status: completed
type: task
priority: low
created_at: 2026-04-23T01:32:52Z
updated_at: 2026-04-23T20:19:23Z
---

## Description

src/isaac/cli/agents.clj (124 lines) is a leftover duplicate of the renamed src/isaac/cli/crew.clj. Both files register CLI commands and both are required from src/isaac/main.clj:15.

Remove:
- src/isaac/cli/agents.clj
- the isaac.cli.agents require in src/isaac/main.clj

The 'agents' subcommand has no feature of its own; features/cli/crew.feature covers the replacement. Incidentally removes cli/agents.clj:41's stale "/.isaac/agents/<id>/SOUL.md" path which would write to the wrong directory.

Acceptance:
1. src/isaac/cli/agents.clj is deleted
2. src/isaac/main.clj no longer requires isaac.cli.agents
3. 'isaac agents' returns a not-found error (not the crew listing)
4. bb features and bb spec pass

