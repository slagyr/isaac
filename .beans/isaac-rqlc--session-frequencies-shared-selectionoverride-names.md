---
# isaac-rqlc
title: 'Session frequencies: shared selection+override namespace and map schema'
status: completed
type: feature
priority: normal
tags: []
created_at: 2026-06-27T16:01:15Z
updated_at: 2026-06-27T16:55:16Z
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

## DoD / scenarios (2026-06-27)

Rename is mechanical; the existing prompt (cli-prompt.feature) + hail features are the REGRESSION NET — they must stay green under the new namespaces. No new gherkin for the rename itself.

NEW coverage = speclj specs on the isaac.session.frequencies schema (NEW shape only, per no-legacy-tests):
- validates a complete valid frequencies map (all keys, valid enum values)
- rejects :create outside #{:never :if-missing :always}
- rejects :prefer outside #{:recent :oldest}
- rejects :reach outside #{:one :all}
- rejects an unrecognized key (strict -> this is what makes a missed migration fail loud; no legacy shape named)

The schema must be usable in BOTH places the frequencies map appears:
- CONFIG (discord channels, cron jobs, hooks, hail bands) — referenceable via :isaac.config/schema so consumers get fail-loud validation at boot.
- RUNTIME flat-map (CLI args, protocol params) — frequencies-cli cross-field validation (--session exclusivity, enum values) already exists; keep it.

Acceptance:
- isaac.session.selector -> isaac.session.frequencies; isaac.session.selector-cli -> isaac.session.frequencies-cli; all callers (prompt bridge, hail) updated.
- frequencies-map schema hosted in the core + speclj specs above green.
- Full prompt + hail suites green under the new names.

## Verification

Verified on fetched GitHub heads:

- `isaac-agent` `10093b4e62af2bec1baf92213dc50223d70b959a`
- `isaac-hail` `438fc3e1725fbca0e49ea2168912c98669c08f02`
- supporting sibling proof heads:
  - `isaac-foundation` `a8344457b8b187738092072e92e0776a0128c721`
  - `isaac-server` `eb51cc48b8964dabb678086ac36051a86d94c03a`

Proofs were green:

- `isaac-agent` `bb spec spec/isaac/session/frequencies_spec.clj spec/isaac/session/frequencies_cli_spec.clj` -> `37 examples, 0 failures, 61 assertions`
- `isaac-agent` `bb features features/bridge/cli-prompt.feature` -> `29 examples, 0 failures, 59 assertions`
- `isaac-hail` `bb spec spec/isaac/config/hail_loader_spec.clj spec/isaac/hail/router_spec.clj spec/isaac/hail/cli_spec.clj` -> `27 examples, 0 failures, 70 assertions`
- `isaac-hail` `bb features features/router.feature features/send-addressing.feature features/spawn-session.feature` in a sibling worktree layout matching its `../isaac-*` local-root feature alias -> `27 examples, 0 failures, 117 assertions`

That covers the new shared frequencies schema, CLI adapter rename, prompt
regression surface, and hail's downstream consumer rename under the new
vocabulary.
