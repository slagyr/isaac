---
# isaac-702d
title: "Persist usage and reasoning on assistant transcript entries"
status: completed
type: feature
priority: low
created_at: 2026-05-01T18:23:39Z
updated_at: 2026-05-11T18:46:22Z
---

## Description

Right now Isaac's storage path strips assistant responses to
`{:role :content :model :provider :crew}` — `parse-usage` reduces the
rich usage block to `{:input-tokens, :output-tokens}` and the
reasoning block is discarded entirely.

Without persistence we cannot:
  - Audit historical turns for cost/token consumption
  - Build cost dashboards
  - Show per-turn 'this used effort=high, N reasoning tokens' in any UI

## Scope

Extend the assistant transcript entry to include:
  - `:usage` with full token detail (`input_tokens`, `output_tokens`,
    `reasoning_tokens`, `cached_tokens`)
  - `:reasoning` with `{:effort ... :summary ...}` when present

Storage already supports `:usage`/`:cost` in some adapters; openclaw
stores `usage/cost/stopReason/durationMs` (see project memory
`openclaw-session-format-comparison-2026-04-09`).

## Why deferred

Capturing the data has no consumer yet. Pulling forward when the
first dashboard / UI / cost report is in flight.

## Depends on

- isaac-ibme (the diagnostic log already surfaces these fields per
  turn; this bead just persists them)

