---
# isaac-qab4
title: "ACP proxy reconnect request does not complete after reconnect"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T17:56:33Z
updated_at: 2026-04-28T18:05:37Z
---

## Description

features/acp/reconnect.feature currently fails in the scenario 'a request arriving during disconnect waits for reconnect and then completes'. After the loopback connection is restored, response 42 returns with nil result.stopReason instead of end_turn. This is unrelated to compaction logging work but keeps full bb features from going green. Investigate ACP proxy request/resume handling so in-flight requests complete after reconnect.

