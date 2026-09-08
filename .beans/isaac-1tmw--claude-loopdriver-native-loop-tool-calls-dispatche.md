---
# isaac-1tmw
title: 'claude LoopDriver native loop: tool calls dispatched twice under Claude''s mcp__ name, one CLI spawn per cycle, reply text doubled'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-09-08T20:17:46Z
updated_at: 2026-09-08T21:56:41Z
parent: isaac-tuk1
---

Repo: isaac-claude-code (`claude_cli.clj` driven loop: tool_use handling, stdin replay per cycle, reply assembly). Child of isaac-tuk1; follow-up to isaac-kbu0. First successful server-origin native run 2026-09-08 20:14Z (agent 0.1.52, module 0.1.6, server 0.1.13) — smoke band + session pinned to claude-cli, hail 93544111.

## Evidence (server.log + session genuine-cedar)
- `:turn/loop-driver :driver :provider`; `POST /mcp/turns/<id>` → `:mcp/tools-listed`; `:claude/mcp-status :tools 15 :status "connected"`; `:claude/driver-exit :result-event true`; no fallback. The reply reached the user. So the bridge path is now live.
- BUT: 4 `:claude/driver-exit` lines for ONE turn (20:15:30, :41, :56, 20:16:08) — the driver respawns Claude Code per cycle (history replayed as text each time) instead of one process owning the loop (5xn7 decision 1).
- Transcript: 7 toolResults for one command — one `mcp-loop-ok` (the bridge executed it through the registry) and six `Error: unknown tool: mcp__isaac__exec__run` — the driver ALSO dispatches each tool_use through the drive's tool function using Claude's MCP name `mcp__<server>__<tool>` unmapped, records the error pair, and the model retries. 5xn7 decision: execution happens via MCP; the driver correlates by tool_use id and records the pair, it does not re-execute.
- Reply text doubled: `mcp-loop-okmcp-loop-ok` — text_delta chunks AND the trailing assistant message event are both appended.

## Required
1. Map `mcp__isaac__<tool>` → `<tool>` (isaac's name) when recording; the toolCall entry carries isaac's name.
2. A tool_use seen in the stream is recorded from the bridge's result (correlate by tool_use id via the registry entry), never re-dispatched through the drive's tool-fn; exactly one toolCall/toolResult pair per tool_use.
3. One CLI process per turn: the driven loop feeds nothing back per cycle; the process runs to its `result` event. (Mid-turn compaction stays deferred per 1sdl.)
4. Reply assembly: deltas OR the final message text, once.
5. Fake Claude Code emits the real shapes: tool_use names carry the `mcp__isaac__` prefix; the reply is emitted as text_delta chunks followed by the trailing assistant message with the full text.

## Scenarios (@wip, planted isaac-claude-code 24d29ee)
- an MCP tool call is executed once, under isaac's tool name, and recorded from the bridge's result
- one CLI process serves the whole turn — tool cycles do not respawn Claude Code
- the reply is assembled from the stream once
Steps: existing (fake scripted with, response is, transcript matching, has N transcript entries, log has entries matching) + **the fake Claude Code was invoked exactly once** (mirror of the legacy `the claude binary was invoked exactly once`; NEW if the fake lacks it).

## Acceptance
- `bb features features/llm/api/claude_driver.feature` → all 18 scenarios green with @wip removed; `bb features && bb spec` green
- Field check (planner or verifier via exec on zanebot after the train): `isaac hail send --band smoke --prompt '<echo mcp-loop-ok prompt>'` on the claude-cli-pinned smoke session → reply exactly `mcp-loop-ok`; server.log: ONE `:claude/driver-exit` for the turn, `:mcp/tools-listed` once, ONE tools/call POST; transcript: exactly one toolCall (exec__run) + one toolResult (`mcp-loop-ok`) + one assistant message.


## Handoff

branch: bean/isaac-1tmw @ ab6eaa3 (base origin/main@f017756)

One CLI spawn per turn; mcp__isaac__ names mapped to isaac names; drive tool-fn is not re-dispatched after the stream (fake CLI executes once via the registry-bound tool-fn); reply XOR deltas vs trailing assistant text.

Acceptance: `bb features features/llm/api/claude_driver.feature` 18 green with @wip off; `bb features && bb spec` green.
