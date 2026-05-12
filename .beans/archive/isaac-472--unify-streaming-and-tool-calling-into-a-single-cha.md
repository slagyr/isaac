---
# isaac-472
title: "Unify streaming and tool-calling into a single chat dispatch path"
status: completed
type: bug
priority: high
created_at: 2026-04-09T23:14:54Z
updated_at: 2026-04-10T03:55:52Z
---

## Description

The chat flow has two mutually exclusive paths in process-user-input!: print-streaming-response (streaming, prints to stdout, no tools) vs dispatch-chat-with-tools (non-streaming, silent, handles tools). This causes a regression where enabling tools suppresses all terminal output — the model responds but nothing prints.

Streaming and tool calling should coexist in one path: stream the response, print text as it arrives, handle tool calls inline when they appear.

This is a TUI concern — the session transcript correctly stores responses regardless of dispatch path. The fix is in the CLI chat loop, not the core chat flow.

## Acceptance Criteria

1. 'isaac chat' with ollama prints assistant text responses to the terminal
2. Tool calls are executed (not printed as raw markup) and results sent back to the model
3. The model's final response after tool execution is printed
4. bb features and bb spec pass

## Notes

Two confirmed symptoms:
1. Assistant text responses are not printed to the terminal (silent)
2. Tool calls are printed as raw XML markup instead of being executed

Both stem from the streaming/tool-call path split in process-user-input!. The streaming path doesn't handle tool calls; the tool path doesn't print.

