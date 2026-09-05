---
# isaac-5xn7
title: 'isaac-claude-code driver v2: Claude Code runs the native tool loop against isaac''s MCP tools; transcript from the stream-json feed'
status: todo
type: feature
priority: normal
tags:
    - claude-cli
    - module
created_at: 2026-09-03T23:07:34Z
updated_at: 2026-09-05T07:11:49Z
parent: isaac-tuk1
blocked_by:
    - isaac-1sdl
    - isaac-zocg
---

Implements the LoopDriver for the claude-cli provider inside isaac-claude-code. Spawn `claude -p --model <m> --tools "" --strict-mcp-config --mcp-config <isaac-mcp json for this turn> --permission-mode bypassPermissions --input-format stream-json --output-format stream-json --include-partial-messages --no-session-persistence --system-prompt <soul+boot+rules>`; write the conversation as stream-json user messages (history as text, as today — tool-pair replay is lossy, khgy L2); consume the event feed: text/thinking deltas → on-chatter/on-reckoning, assistant tool_use → on-cycle + (execution already happened via MCP; correlate by tool_use id), per-message usage → last-input-tokens per cycle (isaac-vuto decision 5 satisfied: input + cache_read + cache_creation), result → reply + totals; SIGTERM the process on cancel. Also in scope: (a) the fake-CLI test harness — a script that speaks stream-json and calls the mcp-bridge, so every scenario runs without the real binary (the existing set-stub! seam stays for the legacy path); (b) legacy fence path kept for providers/CLIs without MCP (isaac-jkx7 hardens it); (c) provider flag `:drives-tool-loop? true` on the :claude template; (d) find the switch for the per-invocation title-generation side call (khgy). Decisions for review: long-lived process per session vs per-turn spawn; how mid-turn compaction is handled (from the seam bean); whether thinking blocks are persisted as reckoning. Scenarios (@wip, features/llm/api/claude_driver.feature): (1) a turn with one tool call: MCP receives the call, the transcript has the pair and the final reply, per-cycle stamps recorded; (2) multi-cycle turn ordering matches the feed; (3) chatter/reckoning deltas reach the comm in order; (4) cancel mid-loop ends the turn :cancelled and kills the process; (5) an oversized tool result reaches the model as the capped text, not an error; (6) history is replayed as text and the model sees prior turns. Draft until reviewed.



## Decisions (2026-09-03, Micah: decisions look good)

1. One CLI process per turn: spawn with history rendered as text + the live prompt, read the feed to the result event, exit. Isaac stays the owner of the transcript; matches the seam's compact-between-turns rule.
2. Thinking blocks persist as `reckoning` transcript entries (excluded from prompt builder and recall, per scuttlebutt).
3. Title side call: find the switch; if none, accept and log its cost per turn.
4. Fallback: if the CLI lacks the needed flags or MCP fails to connect at init, run that turn on the legacy fence path and log `:claude/driver-fallback`; never fail the turn for it.

## Scenarios (`@wip`, isaac-claude-code `features/llm/api/claude_driver.feature` — committed once isaac-jllj scaffolds the repo)

1. a turn with one tool call: the fake CLI emits tool_use → the bridge calls the registry → the transcript has the toolCall/toolResult pair and the final reply; last-input-tokens = the final cycle's input + cache_read + cache_creation
2. a multi-cycle turn persists pairs in feed order
3. text and thinking deltas reach the comm as chatter and reckoning in order; reckoning is persisted and excluded from the next prompt
4. cancel mid-loop terminates the process and the turn ends cancelled
5. prior turns are replayed as text history and the model's stdin shows them before the live prompt
6. an older CLI (no stream-json input) or a failed MCP init falls back to the fence path for that turn, logged

(The oversized-result case is covered by isaac-zocg's registry scenarios; not repeated here.)

## Step ledger

| step | status |
|------|--------|
| isaac EDN file exists with / .env file contains / sessions exist / user sends / the response is / session has transcript matching / the following sessions match / the memory comm has events matching / the turn is cancelled … after N tool call / the turn result is / the log has entries matching | reuse |
| the claude binary is stubbed to return … / was invoked exactly once with: | reuse — the legacy stub seam, used only by scenario 6's fence-path assertion |
| **Given a fake Claude Code on the path scripted with:** (table: cycle, kind ∈ text/thinking/tool_use/usage, payload) | **NEW — process-level double: a script that speaks stream-json on stdout, invokes the configured mcp-bridge for each tool_use row and feeds its result back, and records its own stdin/argv** |
| **Then the fake Claude Code received on stdin:** (table of stream-json user messages, in order) | **NEW — asserts history-as-text replay + live prompt** |
| **And the fake Claude Code was terminated** | **NEW — cancel path: SIGTERM observed by the double** |
| **Given the fake Claude Code fails MCP initialization** / **reports version "…"** | **NEW — scripted failure modes for the fallback scenario** |

Four new steps, all fixture/assertion helpers around the fake CLI; no new domain step phrasing. The fake lives in the module's spec support and reuses the agent's provider-driven fake driver seam from isaac-1sdl where it can.



## Planted (2026-09-05)
Feature file committed: isaac-claude-code main 81c542f `features/llm/api/claude_driver.feature` (6 @wip scenarios, Background mirrors claude_cli.feature plus `drives-tool-loop? true` and `exec/run`). Ledger unchanged except one assertion step reused from the legacy set under a new subject: **the fake Claude Code was invoked with:** (same table shape as `the claude binary was invoked exactly once with:`) — implement as the fake's argv record, not a new matcher. Unblocked: isaac-1sdl (agent 0.1.47) and isaac-zocg (server 0.1.12) are deployed on zanebot; the module's manifest must bump its agent/server pins to those SHAs before `bb features` can see the LoopDriver seam and the MCP turn route. Status → todo; dispatch on Micah's release.
