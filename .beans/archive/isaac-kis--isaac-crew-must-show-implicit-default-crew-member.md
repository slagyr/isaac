---
# isaac-kis
title: "isaac crew must show implicit default crew member"
status: completed
type: bug
priority: normal
created_at: 2026-04-14T04:23:53Z
updated_at: 2026-04-14T04:36:30Z
---

## Description

## Problem

`isaac crew` only shows crew members explicitly listed in `crew.list` config. The implicit default 'main' crew member (resolved from `crew.defaults` + workspace SOUL.md) doesn't appear.

## Fix

The crew listing command should always include the default crew member ('main') even when it's not in `crew.list`. The resolver already handles this for turn context — the listing just needs the same fallback.

## Scenario (already exists)

```gherkin
Scenario: crew with no configured crew shows the default
  When isaac is run with "crew"
  Then the output matches:
    | pattern |
    | main    |
```

## Acceptance

- `isaac crew` always shows 'main' (or whatever the default is)
- Explicit crew members also shown
- No duplicate if main is explicitly listed too

## Acceptance Criteria

isaac crew shows implicit default crew member. bb features features/cli/crew.feature passes.

