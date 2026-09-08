---
# isaac-8slm
title: 'claude LoopDriver: text emitted before a tool call is glued onto the final reply (aside vs reply)'
status: todo
type: bug
priority: normal
created_at: 2026-09-08T22:10:01Z
updated_at: 2026-09-08T22:10:01Z
parent: isaac-tuk1
---

Repo: isaac-claude-code (driven loop reply assembly). Child of isaac-tuk1; follow-up to isaac-1tmw (0.1.7, deployed 2026-09-08 22:08Z).

## Evidence (server-origin smoke, hail 064911f6, session genuine-cedar)
Counted turn on 0.1.7: driver-exit 1, fallback 0, tools-listed 1, /mcp/turns POSTs 2, mcp-status 15 tools connected, exactly one toolResult `mcp-loop-ok` — all of isaac-1tmw's contract holds. The assistant message persisted as `OKmcp-loop-ok`: the model's text before the tool_use ("OK") was concatenated with the final cycle's text.

## Required
Scuttlebutt contract: text in a cycle that ends in tool calls resolves into an **aside** (comm on-aside, persisted as an assistant aside per the existing default-loop behaviour); only the final tool-less cycle's text is the reply. The driven loop must segment the stream by cycle (a tool_use closes the cycle) and route text accordingly.

## Scenario (@wip, planted isaac-claude-code 3ae2534)
- text before a tool call is an aside, not part of the reply — reuses fake scripted with, via memory comm, memory comm events, transcript matching. New steps: none.

## Acceptance
- `bb features features/llm/api/claude_driver.feature` → all 19 scenarios green with @wip removed; `bb features && bb spec` green
- Field check via the smoke band (planner): reply exactly `mcp-loop-ok`, transcript assistant message exactly `mcp-loop-ok`, memory/comm aside `OK` if the model chatters.
