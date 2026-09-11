---
# isaac-8slm
title: 'claude LoopDriver: text emitted before a tool call is glued onto the final reply (aside vs reply)'
status: completed
type: feature
priority: normal
created_at: 2026-09-08T22:10:01Z
updated_at: 2026-09-08T23:52:58Z
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

## Handoff
branch: bean/isaac-8slm @ 6c303e353035dd90e1e807bd092170995431d661 (base origin/main@087eecbdd33635857446275f2f721de65ce33ea0)

Parser splits stream-json by cycle: text sharing a cycle with tool_use is `:asides`; only the final tool-less cycle is the reply. LoopDriver fires on-cycle `:end` with the aside *before* the fake/MCP tool-fn so scuttlebutt can persist it, then replies with the last cycle's text. Scenario un-@wip. `bb features features/llm/api/claude_driver.feature` 19/0; `bb features` 33/0; `bb spec` 59/0 (3 pending @real). Manifest 0.1.8.



## Landed on main (2026-09-08)

main-sha: isaac-claude-code 2ca7faa75055f8b09573c2446ee5092f2391cb37



## Deployed + field check (2026-09-08 ~23:2xZ) — 0.1.8 (2ca7faa)
Counted smoke (hail 3fc68663): driver-exit 1, fallback 0, tools-listed 1, POSTs 2; the pre-tool aside is no longer glued on (contract met). Regression: assistant message `mcp-loop-okmcp-loop-ok` — reply doubled from two of the three text sources → isaac-g2z8.
