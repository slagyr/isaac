---
# isaac-rqlc
title: 'Session frequencies: shared selection+override namespace and map schema'
status: draft
type: feature
priority: normal
created_at: 2026-06-27T16:01:15Z
updated_at: 2026-06-27T16:32:02Z
parent: isaac-4e4b
---

Rename the shared selection/override code to the unified 'frequencies' vocabulary (hail's term, now system-wide) and host the frequencies-map SCHEMA in the core so every consumer validates one shape.

## Scope
- isaac.session.selector -> isaac.session.frequencies (core): resolve-session-targets, matching-sessions, :reach, :prefer tiebreak, :create policy, override projection. HOSTS the frequencies-map schema.
- isaac.session.selector-cli -> isaac.session.frequencies-cli: build the frequencies map from CLI args (tools.cli option-specs -> flat map) + validation. The reusable CLI adapter.
- The frequencies map = {:session :session-tags :crew :reach :prefer :create :with-crew :with-model :with-effort :with-context-mode}. Override applied via session.context/create-with-resolved-behavior!.
- Update existing consumers (prompt, hail) to the new names.

## Why
Foundational. Every consumer (cli, hail, cron, hooks, discord, acp, chat) builds a frequencies map from its own input and feeds the same core; the schema-in-core gives one validated shape. Do this before/with the consumer migrations.

## Validation: strict + fail-loud (no legacy tests)
The frequencies-map schema validates strictly at config load/boot; non-conforming config fails LOUDLY with a clear error. Scenarios cover the NEW shape ONLY — a valid frequencies map loads; a malformed one is rejected. Per Micah: do NOT write legacy-shape or migration scenarios. Old-shape config just fails as ordinary invalid config; operators migrate via each consumer's Deploy checklist.
