---
# isaac-i66k
title: 'Episode crews: recall tools advertised by the recall block must actually be allowed'
status: draft
type: bug
priority: normal
created_at: 2026-08-29T04:43:16Z
updated_at: 2026-08-29T04:43:16Z
---

Repo: **isaac-agent** (`isaac.recall.*` injection + tool allow cascade). Found in
the isaac-h5dk field check, 2026-08-28.

## Problem

The recall-at-open block header says "fetch full detail with `recall__scene <id>`",
but the crew's tool allow list decides whether `recall__scene` / `recall__search`
exist for the turn. Pilot had no `:tools :allow`, so the model improvised
`skill__load recall__scene` → `Error: unknown skill`. A dead-end escape hatch
is worse than none (decision 23: gist-only tiers must be fetchable).

## Options (pick in planning)

1. Episode crews (`:conversation :episodes`) implicitly allow `:recall/*` —
   the crew opted into recall; the tools are part of that contract.
2. The header only advertises the tool when it is allowed for that crew;
   otherwise says "(scene ids for reference)".
3. Config validation: `:conversation :episodes` without `:recall/*` allowed
   (and without `:all`) is a warning at load.

Recommendation: 1 + 2 (2 is a one-line guard either way).

## Scenarios (to write before todo)

- episode crew with no `:tools :allow` → first turn after a recalling cold
  open can call `recall__scene` successfully.
- chronicle crew with `:recall/*` denied → no recall tools in the request.
