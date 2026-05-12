---
# isaac-a3b
title: "CLI chat command - interactive REPL for testing sessions"
status: completed
type: task
priority: low
created_at: 2026-03-31T19:49:12Z
updated_at: 2026-03-31T23:19:15Z
---

## Description

Implement `bb isaac chat` as the first interactive command.

## Invocation
bb isaac chat                    # new session with default agent
bb isaac chat --agent researcher # new session with named agent
bb isaac chat --resume           # resume most recent session
bb isaac chat --session <key>    # resume specific session

## REPL Loop
1. Resolve agent config and model
2. Create or resume a cli:direct:<user> session
3. Display session info (agent, model, session key)
4. Loop:
   a. Print prompt indicator, read user input
   b. Append user message to transcript
   c. Build prompt (soul + history + tools)
   d. Stream response from LLM, display chunks as they arrive
   e. Append complete assistant message to transcript
   f. If tool calls: execute tools, append results, re-prompt (display tool activity)
   g. Update token counts in session index
5. On empty input or EOF: exit gracefully

## Session Key
agent:<agentId>:cli:direct:<username>
Username from system env (e.g., USER or LOGNAME).

## Streaming Output
Display assistant response tokens as they stream in. After completion, print a blank line before the next prompt.

## Signal Handling
- Ctrl-C during response: abort current generation, keep partial response in transcript
- Ctrl-C at prompt: exit cleanly
- Ctrl-D: exit cleanly

## Session Resumption
--resume with no arg loads the most recent cli session for the agent.
--session <key> loads a specific session by key.
On resume, display a brief summary (message count, last activity).

## Dependencies
This is the vertical integration point — uses session storage, session keys, prompt builder, LLM client, and config resolution.

## No feature file
This is a CLI command, not tested via Gherkin. Manual testing and possibly integration specs.

