---
# isaac-w6ha
title: "ACP proxy survives remote disconnect close errors"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T00:23:21Z
updated_at: 2026-04-28T02:50:04Z
---

## Description

Real ACP proxy reconnect coverage is loopback-only. On a real server shutdown, the disconnect path may call ws-close! on an already-dead RealWs, and src/isaac/cli/acp.clj currently lets that exception escape. The outer catch then prints 'could not connect to remote ACP endpoint' and exits 1, which matches the observed Toad failure after server shutdown. Add regression coverage and make the disconnect path tolerant of close errors so the proxy stays alive and retries reconnecting.

## Notes

Added regression coverage for close errors on dead remote ACP sockets and made proxy disconnect cleanup tolerant of ws-close exceptions. bb spec spec/isaac/cli/acp_spec.clj passes, and full bb spec passes.

