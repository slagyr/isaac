---
# isaac-g2z8
title: 'claude LoopDriver: reply text doubled again on 0.1.8 — the stream carries the final text three times; use one source'
status: in-progress
type: bug
priority: normal
created_at: 2026-09-08T23:52:56Z
updated_at: 2026-09-08T23:53:43Z
parent: isaac-tuk1
---

Repo: isaac-claude-code (driven loop reply assembly). Child of isaac-tuk1; follow-up to isaac-8slm (0.1.8, deployed 2026-09-08 ~23:2xZ).

## Evidence (server-origin smoke, hail 3fc68663, session genuine-cedar)
Counts: driver-exit 1, fallback 0, tools-listed 1, /mcp/turns POSTs 2 — the loop is clean; the aside is no longer glued on. But the assistant message persisted as `mcp-loop-okmcp-loop-ok`. The real 2.1 stream carries the final text as (a) stream_event/content_block_delta text_delta chunks, (b) an `assistant` message event with the full text, (c) the `result` event's `result` field. 0.1.6 concatenated (a)+(b); 0.1.8 concatenates (b)+(c) (or (a)+(c)). The fake CLI has never emitted all three, so each fix passed the suite and failed the field.

## Required
1. Reply assembly picks ONE source in a fixed order: result.result when present, else the assistant message event text, else the accumulated deltas — never a concatenation across sources. Deltas still stream to the comm as chatter.
2. Fake Claude Code emits all three for every text reply (new fixture kind `result_text`, plus the trailing assistant message the fake already emits for `text`).

## Scenario (@wip, planted isaac-claude-code dc6c80a)
- the final text appears three times in the real stream and is used exactly once — existing steps; one new fixture kind.

## Acceptance
- `bb features features/llm/api/claude_driver.feature` → all 20 scenarios green with @wip removed; `bb features && bb spec` green
- Field check via the smoke band (planner): assistant message exactly `mcp-loop-ok`.
