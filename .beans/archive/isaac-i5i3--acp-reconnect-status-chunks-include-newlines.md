---
# isaac-i5i3
title: "ACP reconnect status chunks include newlines"
status: completed
type: bug
priority: low
created_at: 2026-04-28T01:03:16Z
updated_at: 2026-04-28T03:01:37Z
---

## Description

During ACP proxy disconnect/reconnect, src/isaac/cli/acp.clj emits two adjacent agent_thought_chunk notifications with bare text: 'remote connection lost' and 'reconnected to remote'. Toad appears to concatenate adjacent thought chunks inline, producing 'remote connection lostreconnected to remote'. Make reconnect status chunks include a trailing newline separator so clients that concatenate chunks still render readable status transitions. Add regression coverage around the exact JSON payloads.

