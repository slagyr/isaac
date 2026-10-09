---
# isaac-dnzb
title: 'claude-code: per-turn token totals count each message once, not once per content block'
status: draft
type: bug
priority: normal
created_at: 2026-09-30T00:46:06Z
updated_at: 2026-09-30T00:46:06Z
---

Follow-up from isaac-6ef2 (2026-09-29). Per-turn token totals on the claude-code provider are inflated several times over: skiff 2026-09-28 17:21Z reported turn prompt-tokens 27,817,085 while the CLI's own turn total was 4,757,749.

## Cause (planner's reading)

In the real stream-json output, an assistant message repeats once per content block, with the same `message.id` and the same `usage` (captured 2026-09-29: each message appeared twice). The driver records one cycle per block, so a message with N blocks counts its usage N times (see memory note "claude-code lane: cycles are not requests"). `provider-cycles` counts blocks, not requests, too.

## Wanted

Count usage once per `message.id`. Turn spend (prompt, cache-read, cache-write, output) and `provider-cycles` count requests, not blocks. The gauge (first request's prompt size, per isaac-6ef2) is unaffected.

## Acceptance

Scenario TBD by the planner in isaac-claude-code's `claude_driver.feature`: a fake CLI that repeats an assistant message per content block (same id, same usage) yields turn totals equal to the sum over distinct messages. The fake must emit the repeated-message shape the real CLI does.

## Likely repo scope

isaac-claude-code (maybe isaac-agent accounting).
