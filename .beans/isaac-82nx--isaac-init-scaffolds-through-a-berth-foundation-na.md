---
# isaac-82nx
title: isaac init scaffolds through a berth (foundation names no module's starter files)
status: draft
type: task
priority: low
created_at: 2026-09-30T02:44:10Z
updated_at: 2026-09-30T15:19:03Z
blocked_by:
    - isaac-on0o
---

Found during the cleanup survey (2026-09-30). `isaac init` (foundation `src/isaac/cli/registry.clj`, `scaffold!`/`created-files`) hard-codes a full crew/models/providers/cron/ollama onboarding scaffold: foundation naming agent, cron and provider concepts.

## Wanted (planner recommendation)

Foundation declares a scaffold berth; each module contributes the starter files it owns (agent: a crew, a model, a provider; cron: an example job). `isaac init` with only foundation creates the bare root and `isaac.edn`. Clean cutover.

## Acceptance

Scenarios TBD by the planner (foundation: bare init; a Marigold fixture module contributes a starter file that init writes). Agent/cron contribute their current starters so `isaac init` output on a full install is unchanged.

## Design (Micah, 2026-09-30)

- `isaac init` is foundation only: creates the root and a very basic `isaac.edn`. Nothing module-specific.
- Module setup is opt-in: a module contributes a setup to a foundation berth; no contribution = nothing to do.
- A setup is a function of the current config returning writes; writes go through the validated atomic write path (`set-many!`, same as `config set` / `handbook__configure`). Idempotent: add what is missing, never overwrite (e.g. agent adds a starter crew only when there are no crews). New fields with schema defaults need no setup.
- Runs automatically on `modules install` and `modules upgrade`; rerun by hand with `isaac modules setup <id>`; `--dry-run` shows the writes. Every write is printed.
- Agent/cron/providers move their current init starters into their setups, so a full install ends up with what init produces today.

Scenarios to be written by the planner (foundation: bare init, a Marigold fixture setup that runs on install, is idempotent on rerun, dry-run).
