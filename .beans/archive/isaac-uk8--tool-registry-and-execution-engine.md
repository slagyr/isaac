---
# isaac-uk8
title: "Tool registry and execution engine"
status: completed
type: task
priority: high
created_at: 2026-04-07T23:57:43Z
updated_at: 2026-04-08T15:24:30Z
---

## Description

Implement the tool registry and execution dispatch. A tool is defined with name, description, parameter schema, and handler fn. The registry collects tools and provides a tool-fn callback for chat-with-tools loops. Unknown tools return errors. References features/tools/execution.feature.

