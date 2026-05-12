---
# isaac-8pgh
title: "Handle structured websocket close events in ACP client readers"
status: completed
type: bug
priority: normal
created_at: 2026-04-30T01:17:40Z
updated_at: 2026-04-30T01:22:10Z
---

## Description

Why this issue exists and what needs to be done\n\nThe websocket transport now preserves close frames as structured maps with status and reason. Discord gateway handles these correctly, but other ws/ws-receive! consumers still assume only string, nil, or error values. In particular, the ACP CLI remote reader can misclassify a close map as a normal message. Update remaining websocket readers to treat close maps explicitly and preserve correct reconnect/disconnect behavior.\n\nWhat needs to be done\n- Audit remaining ws/ws-receive! consumers\n- Update ACP client remote reader(s) to handle {:type :close ...} explicitly\n- Add/adjust specs covering close-map handling\n- Run focused specs and full bb spec if code changes are made

