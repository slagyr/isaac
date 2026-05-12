---
# isaac-fat
title: "Make WS handlers reloadable via c3kit.wire lazy-routes"
status: draft
type: task
priority: deferred
tags:
    - "deferred"
created_at: 2026-04-13T02:44:17Z
updated_at: 2026-04-17T04:29:23Z
---

## Description

## Summary

The ACP WebSocket handler in `src/isaac/server/acp_websocket.clj` is wired directly in routes. For dev-mode hot reload, it should use c3kit.wire lazy-routes so handler changes take effect without restarting the server.

## Depends on
- dev-reload feature (already has @wip scenarios in features/server/dev-reload.feature)

## Acceptance Criteria

Server --dev mode hot-reloads WS handler changes without restart.

