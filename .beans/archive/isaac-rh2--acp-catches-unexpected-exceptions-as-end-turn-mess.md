---
# isaac-rh2
title: "ACP catches unexpected exceptions as end_turn messages"
status: completed
type: bug
priority: high
created_at: 2026-04-16T04:52:20Z
updated_at: 2026-04-16T21:45:23Z
---

## Description

When an unexpected exception occurs during a turn (e.g. ClassCastException from malformed API response), the ACP server must send the error as an agent_message_chunk notification with stopReason: end_turn — not as a JSON-RPC internal error that crashes IDEA. Requires adding 'exception' type to grover scripted responses.

