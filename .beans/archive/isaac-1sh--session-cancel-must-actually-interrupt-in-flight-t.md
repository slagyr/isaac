---
# isaac-1sh
title: "session/cancel must actually interrupt in-flight tool calls and LLM requests"
status: completed
type: bug
priority: high
created_at: 2026-04-14T18:14:11Z
updated_at: 2026-04-14T18:41:39Z
---

## Description

session/cancel sets an interrupted flag but only checks it at the top of the turn. If a tool call is running (exec subprocess, LLM HTTP request), the cancel has no effect. The process hangs until the tool finishes or times out. IDEA spawns zombie acp processes when this happens.

Bridge cancel (channel-agnostic): features/bridge/cancel.feature — 3 scenarios
ACP cancel (protocol wiring): features/acp/cancel.feature — 3 scenarios

Implementation needs:
- Interrupt running exec subprocesses (Process.destroy)
- Abort in-flight HTTP requests
- Bridge exposes a cancel function that channels call
- ACP session/cancel handler calls the bridge cancel
- Return cancelled response immediately
- Session remains usable for next prompt

## Acceptance Criteria

session/cancel interrupts a running exec tool call within 1 second. session/cancel interrupts a running LLM request within 1 second. The session remains open for the next prompt.

