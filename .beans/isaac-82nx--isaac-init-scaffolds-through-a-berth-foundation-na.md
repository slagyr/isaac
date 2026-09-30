---
# isaac-82nx
title: isaac init scaffolds through a berth (foundation names no module's starter files)
status: draft
type: task
priority: low
created_at: 2026-09-30T02:44:10Z
updated_at: 2026-09-30T02:44:10Z
---

Found during the cleanup survey (2026-09-30). `isaac init` (foundation `src/isaac/cli/registry.clj`, `scaffold!`/`created-files`) hard-codes a full crew/models/providers/cron/ollama onboarding scaffold: foundation naming agent, cron and provider concepts.

## Wanted (planner recommendation)

Foundation declares a scaffold berth; each module contributes the starter files it owns (agent: a crew, a model, a provider; cron: an example job). `isaac init` with only foundation creates the bare root and `isaac.edn`. Clean cutover.

## Acceptance

Scenarios TBD by the planner (foundation: bare init; a Marigold fixture module contributes a starter file that init writes). Agent/cron contribute their current starters so `isaac init` output on a full install is unchanged.
