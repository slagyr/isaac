---
# isaac-ypyk
title: 'isaac-agent: six attribution scenarios never check their first output row'
status: draft
type: bug
created_at: 2026-10-11T21:05:10Z
updated_at: 2026-10-11T21:05:10Z
parent: isaac-zt1x
---

DRAFT. Found 2026-10-11 while planning the contacts module (isaac-o7tm).

## Problem

The step `the stdout matches:` reads its table with a header row, so the first row is never checked. The convention is to lead with `| pattern |`. Six tables written for the attribution beans skip that header, so their first row asserts nothing:

- `features/module/identifier_extension.feature` lines 42, 60, 75, 94 — the `from.kind: …` row in all four scenarios (isaac-s715).
- `features/turn/attribution.feature` lines 39 and 55 — the `from.kind: …` row in both scenarios (isaac-v403). In the CLI scenario that row is the only positive check.

The code is believed correct: the remaining rows pass and unit specs cover the kinds. The scenarios are just weaker than they read.

A second, smaller weakness: a row such as `from.id: cordelia` also matches the raw handle `cordelia-7`, because rows are unanchored regexes.

## Fix

Planner work, since these are baselined feature files: add the `| pattern |` header row to each table, anchor the id rows, mark the touched scenarios `@wip`, and baseline a small bean for a worker to confirm they pass and remove the tags.

## Likely repo scope

`isaac-agent`. Check `isaac-gchat features/comm/gchat/inbound.feature` (isaac-dlw5), which uses its own step and may not be affected.
