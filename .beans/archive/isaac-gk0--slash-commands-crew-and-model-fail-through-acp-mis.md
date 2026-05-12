---
# isaac-gk0
title: "Slash commands /crew and /model fail through ACP — missing context"
status: completed
type: bug
priority: high
created_at: 2026-04-15T06:07:48Z
updated_at: 2026-04-15T13:40:12Z
---

## Description

The bridge's /crew and /model handlers check crew-members and models maps from the context, but the ACP prompt handler doesn't pass these into the bridge context. Result: unknown crew and unknown model errors for valid configured values.

Root cause: acp/server.clj builds the turn context but doesn't include the full crew-members map or models map that the bridge needs for slash command resolution.

features/acp/slash_command_wiring.feature — 2 scenarios

## Acceptance Criteria

Both @wip scenarios pass with @wip removed. /crew ketch and /model grok work in IDEA and Toad.

