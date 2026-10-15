---
# isaac-vmlf
title: System-section contributors are handed the turn's session key
status: completed
type: feature
priority: normal
created_at: 2026-10-15T13:33:54Z
updated_at: 2026-10-15T15:24:02Z
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

## Scenarios

isaac-agent `features/module/system_section_extension.feature` (committed
`@wip` at 3507d73). Run from isaac-agent:

- `bb features features/module/system_section_extension.feature:114` — a contributor is handed the turn's session

Uses a new fixture module `isaac.section.lantern` (under `modules/`, beside
`isaac.section.beacon`) whose section text is "Lantern lit on <session
key>."

## Acceptance

- `@wip` removed and the new scenario passes; the existing system-section
  scenarios still pass.

feature-baseline: isaac-agent 3507d7352bf5824b4325da3c93808a6656c20cdb
feature-blob: isaac-agent features/module/system_section_extension.feature e9d608782d3cf3d63ccfa15afd2fde546989d8e4 114

## Landed on main (2026-10-15)

main-sha: isaac-agent 588d06cc4dba8a0160253d8979e96fde32bf9c22
