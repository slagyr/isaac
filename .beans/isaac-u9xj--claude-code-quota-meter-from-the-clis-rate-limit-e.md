---
# isaac-u9xj
title: claude-code quota meter from the CLI's rate_limit_event
status: todo
type: task
priority: normal
tags:
    - claude-code
created_at: 2026-10-28T07:23:35Z
updated_at: 2026-10-28T07:23:35Z
blocked_by:
    - isaac-7q4c
---

## Why

`provider__quota` (the agent bean this is blocked by) needs a Claude meter. The Claude subscription is the one crews spend most.

## What

isaac-claude-code contributes a meter on `:isaac.agent/quota-meter` for template type `:claude-code`, so every provider of that type (whatever its name) is metered.

**Source: the CLI's own `rate_limit_event`.** Every claude turn's stream carries:

```json
{"type":"rate_limit_event","rate_limit_info":{"status":"allowed","resetsAt":1793188800,"rateLimitType":"five_hour","overageStatus":"rejected","isUsingOverage":false,
 "unifiedWindows":{"five_hour":{"utilization":0.01,"resetsAt":1793188800},"seven_day":{"utilization":0,"resetsAt":1793696400}}}}
```

The driver keeps the latest event per **provider id** (two claude-code providers on different logins must not share a report). The meter turns it into a report:

- each `unifiedWindows` entry ⇒ a window, `:id` the key (`five_hour`, `seven_day`), label from the agent's length rule (5-hour, 7-day), `used-percent` = utilization × 100, `resets-at` from `resetsAt` (epoch seconds).
- `limited?` true for the window named by `rateLimitType` when top-level `status` is `"rejected"`; otherwise false.
- `checked-at` = when the event arrived.
- No event yet since the server started ⇒ `:unknown`, error saying no turn has reported yet.

Both the driven (`stream-json`) path and the raw shell-out path should capture it where their output carries it; the fake Claude Code gets a `rate_limit` script kind that emits the event line.

## Acceptance

- Scenarios: isaac-claude-code `features/llm/quota.feature` (3 @wip; gate lines below).
- Agent pin bumped to the agent bean's landed sha (berth + `provider__quota`).
- `bb features` green.

## Planner notes

- **Why not `/api/oauth/usage`** (which reports percent directly): it needs scope `user:profile`. Checked on skiff 2026-10-28 with the provider's `CLAUDE_CODE_OAUTH_TOKEN` (a setup token): `403 permission_error, oauth_scope_insufficient`. All configured claude-code providers authenticate that way.
- **Unit:** `unifiedWindows.utilization` is a 0–1 fraction (skiff 2026-10-28, CLI 2.1.294: `0.01`; community SDK docs show `0.85` alongside `allowed_warning`). The endpoint above reports percent — don't mix them.
- Some community reports say older CLIs emit only the flat `rate_limit_info` (`utilization`, `rateLimitType`) without `unifiedWindows`. If `unifiedWindows` is absent, fall back to the flat fields as a single window of `rateLimitType`.

feature-baseline: isaac-claude-code 535b75f9a3cc820622f88c62de14ae4acc271796
feature-blob: isaac-claude-code features/llm/quota.feature e5ffd902c8bbc2ca3ac8abb57348708de94f336a
