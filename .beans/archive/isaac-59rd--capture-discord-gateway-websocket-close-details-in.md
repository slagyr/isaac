---
# isaac-59rd
title: "Capture Discord gateway websocket close details in production"
status: completed
type: bug
priority: normal
created_at: 2026-04-30T14:20:29Z
updated_at: 2026-04-30T18:31:33Z
---

## Description

## Why\nDiscord probe reaches READY and GUILD_CREATE with intents 4609, but isaac-live logs only a generic :discord.gateway/disconnected reason closed and no READY in production. We need production logging/tests that preserve actual websocket close status and errors so Discord disconnects can be diagnosed and the gateway can reconnect appropriately.\n\n## What\nInstrument the Discord websocket client/gateway path to preserve close status/reason and websocket exceptions instead of collapsing them to {:reason "closed"}. Add specs covering callback-driven close/error payload handling and the reader-loop fallback path.\n\n## Acceptance Criteria\n- production websocket close handling logs close status/reason when available\n- websocket errors are logged with structured details\n- specs cover callback-driven and polling close payload paths\n- bb spec passes

