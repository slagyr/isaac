---
# isaac-48w
title: "Advertise slash commands to ACP clients via available_commands_update"
status: completed
type: feature
priority: high
created_at: 2026-04-14T23:29:21Z
updated_at: 2026-04-15T00:29:06Z
---

## Description

ACP clients (Toad, IDEA) intercept / prefixed input as their own commands. Isaac's bridge slash commands never reach the agent because the client doesn't know about them.

The ACP spec defines available_commands_update notification — the agent tells the client what commands are available after session creation. The client renders them in its UI and sends them as normal prompt text when invoked.

Isaac needs to:
1. After session/new, send available_commands_update listing status, model, crew
2. Bridge already handles /status, /model, /crew — just need the advertisement

features/acp/slash_commands.feature — 3 scenarios

## Acceptance Criteria

All 3 @wip scenarios pass with @wip removed. Toad and IDEA show Isaac's slash commands in their UI.

## Notes

Verification failed: unit specs broken. spec/isaac/server/acp_websocket_spec.clj has uncommitted changes that use str/trim-newline but the ns declaration is missing [clojure.string :as str]. Fix: add [clojure.string :as str] to the :require block and commit the file. Feature scenarios all pass (210 examples, 0 failures).

