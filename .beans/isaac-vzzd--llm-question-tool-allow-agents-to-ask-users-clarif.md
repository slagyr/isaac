---
# isaac-vzzd
title: "LLM question tool: allow agents to ask users clarifying questions mid-turn"
status: todo
type: feature
priority: deferred
created_at: 2026-05-12T14:56:06Z
updated_at: 2026-05-12T14:56:49Z
---

## Description

## Background

Claude Code and OpenCode both offer a 'question' tool to the LLM, letting agents ask users for input mid-turn. The LLM calls the tool with a prompt string; the client surfaces it; the user answers; the result returns as the tool's value and the LLM continues.

Isaac has no equivalent today — agents must guess or proceed without clarification.

## Scope

This is an epic. Implementing the question tool requires cooperation between:

1. **Tool definition** — register a built-in 'question' tool (in tool/builtin.clj) with a 'prompt' parameter. Handler blocks until user responds.

2. **Each comm adapter** must be taught to surface the question and collect the answer:
   - `comm/cli.clj` — print the prompt to stdout, read a line from stdin
   - `comm/acp.clj` — send a 'question' event to the ACP client, await a 'question_response' message
   - `comm/memory.clj` / `comm/null.clj` — stub responses for test/headless contexts

3. **Routing** — the tool handler needs a way to reach the active comm. Likely via the system map or a per-turn context binding.

4. **Protocol** — ACP clients (telly, etc.) need to handle the new event type.

## Notes
- The blocking behavior is the hard part: the tool handler must suspend until the comm delivers an answer, without deadlocking the turn pipeline.
- Headless comms (memory, null) should return a configurable stub or empty string so automated tests don't block.
- Feature tests will require a new step: 'When the agent asks a question' / 'And the user answers X'.

