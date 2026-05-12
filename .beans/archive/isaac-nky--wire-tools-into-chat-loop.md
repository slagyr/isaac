---
# isaac-nky
title: "Wire tools into chat loop"
status: completed
type: task
priority: high
created_at: 2026-04-09T01:19:04Z
updated_at: 2026-04-09T01:26:07Z
---

## Description

Connect the tool registry to the CLI chat loop. build-chat-request must pass tool definitions from agent config to the prompt builder. The chat loop must call chat-with-tools (not plain chat) when tools are present, using the registry dispatch as the tool-fn. Agent config gains a tools field listing which tools the agent can use. References features/tools/execution.feature.

