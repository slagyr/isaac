---
# isaac-82nx
title: isaac init scaffolds through a berth (foundation names no module's starter files)
status: in-progress
type: task
priority: low
created_at: 2026-09-30T02:44:10Z
updated_at: 2026-09-30T19:03:01Z
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

## Acceptance (gated, Micah approved 2026-09-30)

- The @wip scenarios in isaac-foundation `features/module/module_setup.feature` pass with @wip removed: bare init; install runs setup, prints writes and hints; setup never overwrites; rerun reports already set up; --dry-run; a module without setup says so.
- A Marigold fixture module `modules/marigold.setup` provides the setup (proposes marigold.greeting "ahoy", marigold.chimes 3, hint "Polish the bell before first use.") and declares its :marigold schema.
- `modules upgrade` also runs setup (covered by specs; upgrade scenarios need real git coords).
- One-time: the two old init scenarios in `features/cli/init.feature` ("output lists created files…", "scaffolds each file…") are deleted, along with the crew/model/provider/cron scaffold in foundation; the "refuses when a config already exists" scenario stays. Follow-up beans (not this one) move the agent/cron starters into their own setups.
- `bb ci`, `bb features-slow`, `bb jvm-spec` green.

feature-baseline: isaac-foundation 69c8def0dc9924247908223147c1a15e326579ca
feature-blob: isaac-foundation features/module/module_setup.feature e587ae7cf128d96589dd0ceaf4aac214fa9ed660
