---
# isaac-395
title: "Wire tools into chat loop"
status: completed
type: task
priority: high
created_at: 2026-04-07T23:57:47Z
updated_at: 2026-04-08T15:37:57Z
---

## Description

Connect the tool registry to the CLI chat loop. build-chat-request must pass tool definitions from agent config to the prompt builder. The chat loop must call chat-with-tools (not plain chat) when tools are present, using the registry's tool-fn. Agent config gains a tools field. References features/tools/execution.feature.

