---
# isaac-x45o
title: isaac-mcp one-shot hosts lose MCP tools under background connect
status: draft
type: bug
priority: high
tags:
    - mcp
created_at: 2026-09-25T02:15:59Z
updated_at: 2026-09-25T02:15:59Z
---

Split from isaac-aswr. One-shot hosts (`isaac prompt`, `isaac acp`) lose MCP tools under "a turn never waits": the only turn in the process runs before the background connect lands. `features/hosts.feature` is green on isaac-mcp origin/main `3bdc096` and red on `bean/isaac-aswr` (unknown tool: lens__catalog).

isaac-aswr must not absorb this. Do not restore synchronous `ensure-server!`. Do not wire boot `start!` (that re-blocks boot on a dead server).

## Wanted

A process-local warm-up so a one-shot host still offers `lens__catalog` on its single turn, without making a live turn wait on a dead server. Options: pre-connect when the host process starts (background, then the command awaits only if the crew allow-list names an MCP prefix and a short budget remains), or a documented `@wip` until that exists.

## Acceptance (draft — scenarios at promotion)

```
cd isaac-mcp && bb features features/hosts.feature
```

0 failures. `features/turn.feature` / `lifecycle.feature` / `catalog.feature` stay green with the await-connects step. A turn in a long-lived server still does not block on MCP connect.
