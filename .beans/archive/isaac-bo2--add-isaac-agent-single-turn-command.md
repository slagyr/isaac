---
# isaac-bo2
title: "Add isaac agent single-turn command"
status: completed
type: feature
priority: normal
created_at: 2026-04-11T17:41:06Z
updated_at: 2026-04-11T23:13:42Z
---

## Description

Add a new CLI command 'isaac agent' that mirrors openclaw's agent command. Each invocation processes one prompt and exits. Conversations persist across invocations via --session. Drop-in mental model for users coming from openclaw, shell-composable, scripting-friendly.

## Command surface

  isaac agent -m <message> [options]

  -m, --message <text>   Message body for the agent (required)
  --session <key>        Session key (default: agent:main:main)
  --agent <id>           Agent id (default: main)
  --model <alias>        Override agent's default model
  --json                 Output result as JSON

## Behavior

- Required --message. Without it, error and exit non-zero.
- Default session key is agent:main:main (matches openclaw).
- --session <key> resumes an existing session, or creates if absent.
- Writes the assistant response to stdout (final text, not streaming).
- --json outputs {"session": "...", "response": "..."} for scripting.
- Exit 0 on success, non-zero on provider error.

## Implementation

- New namespace: isaac.cli.agent
- Parse -m/--message, --session, --agent, --model, --json in main.clj
- Register command in cli registry
- Delegate to process-user-input! via a non-streaming CLI channel (or a new AgentChannel that collects the final response)
- Print the final content at turn end

## Feature file

features/cli/agent.feature (6 @wip scenarios)

## Acceptance

Remove @wip from all 6 scenarios and verify each passes:
  bb features features/cli/agent.feature:17
  bb features features/cli/agent.feature:27
  bb features features/cli/agent.feature:41
  bb features features/cli/agent.feature:62
  bb features features/cli/agent.feature:68
  bb features features/cli/agent.feature:77

Manual: bb isaac agent -m 'What is 2+2?' prints a real answer from ollama/qwen
Full suite: bb features and bb spec pass

