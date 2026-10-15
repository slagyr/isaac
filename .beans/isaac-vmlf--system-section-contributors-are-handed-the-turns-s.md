---
# isaac-vmlf
title: System-section contributors are handed the turn's session key
status: draft
type: feature
created_at: 2026-10-15T13:33:54Z
updated_at: 2026-10-15T13:33:54Z
blocking:
    - isaac-jkxd
---

## Problem

A system-section contributor (`:isaac.agent/system-sections`, isaac-3dnw) is
called each turn with crew, cwd, config, root and fs
(`isaac.agent.drive.turn`, the `system-sections/resolve-sections` call). It
is not told which session the turn is for, so a module cannot grant a tool
only on its own turns. The gchat module needs exactly that: grant its reply
placement tool only when the turn came from Google Chat (isaac-jkxd).

## Change

Add `:session-key` to the facts `resolve-sections` hands each contributor.
Nothing else: the agent stays ignorant of what a module does with it.

Update the feature's header prose (facts list, fixtures) to match.

## Scenarios

Drafted in chat; to be committed `@wip` to isaac-agent
`features/module/system_section_extension.feature`, then baselined. Uses a
new fixture module `isaac.section.lantern` (under `modules/`) whose section
text is "Lantern lit on <session key>."

## Acceptance

- `@wip` removed and the new scenario passes; the existing system-section
  scenarios still pass.
