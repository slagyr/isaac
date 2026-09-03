---
# isaac-5xn7
title: 'isaac-claude driver v2: Claude Code runs the native tool loop against isaac''s MCP tools; transcript from the stream-json feed'
status: draft
type: feature
tags:
    - claude-cli
    - module
created_at: 2026-09-03T23:07:34Z
updated_at: 2026-09-03T23:07:34Z
parent: isaac-tuk1
blocked_by:
    - isaac-1sdl
    - isaac-zocg
---

Implements the LoopDriver for the claude-cli provider inside isaac-claude. Spawn `claude -p --model <m> --tools "" --strict-mcp-config --mcp-config <isaac-mcp json for this turn> --permission-mode bypassPermissions --input-format stream-json --output-format stream-json --include-partial-messages --no-session-persistence --system-prompt <soul+boot+rules>`; write the conversation as stream-json user messages (history as text, as today — tool-pair replay is lossy, khgy L2); consume the event feed: text/thinking deltas → on-chatter/on-reckoning, assistant tool_use → on-cycle + (execution already happened via MCP; correlate by tool_use id), per-message usage → last-input-tokens per cycle (isaac-vuto decision 5 satisfied: input + cache_read + cache_creation), result → reply + totals; SIGTERM the process on cancel. Also in scope: (a) the fake-CLI test harness — a script that speaks stream-json and calls the mcp-bridge, so every scenario runs without the real binary (the existing set-stub! seam stays for the legacy path); (b) legacy fence path kept for providers/CLIs without MCP (isaac-jkx7 hardens it); (c) provider flag `:drives-tool-loop? true` on the :claude template; (d) find the switch for the per-invocation title-generation side call (khgy). Decisions for review: long-lived process per session vs per-turn spawn; how mid-turn compaction is handled (from the seam bean); whether thinking blocks are persisted as reckoning. Scenarios (@wip, features/llm/api/claude_driver.feature): (1) a turn with one tool call: MCP receives the call, the transcript has the pair and the final reply, per-cycle stamps recorded; (2) multi-cycle turn ordering matches the feed; (3) chatter/reckoning deltas reach the comm in order; (4) cancel mid-loop ends the turn :cancelled and kills the process; (5) an oversized tool result reaches the model as the capped text, not an error; (6) history is replayed as text and the model sees prior turns. Draft until reviewed.
