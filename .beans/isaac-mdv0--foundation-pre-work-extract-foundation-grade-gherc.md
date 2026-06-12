---
# isaac-mdv0
title: 'Foundation pre-work: extract foundation-grade gherclj steps and split spec_helper'
status: in-progress
type: task
priority: normal
created_at: 2026-06-12T12:50:37Z
updated_at: 2026-06-12T14:06:33Z
parent: isaac-brth
---

Phase A step 12 of the isaac-foundation extraction (see isaac-brth reshaping
note). Foundation features (features/cli/*, features/module/* machinery
subset) currently use steps defined in server-flavored namespaces. Extract a
foundation-grade steps layer so those features can move at cut time; server
steps require + delegate (gherclj reports ambiguity if both repos later define
the same phrase).

- [ ] New foundation step namespaces (matching the isaac.**-steps selector,
      e.g. spec/isaac/foundation/cli_steps.clj) holding exactly the steps the
      foundation features use:
      "isaac is run with {args}" (server-free main/run wrapper — the existing
      register-isaac-run-preflight! hook in
      spec/isaac/server/cli/cli_steps.clj moves with it),
      "an (empty )Isaac root at" (from session_steps.clj),
      "the isaac file ... exists with:" (from server_steps.clj),
      "Isaac root ... contains config" (from cli_steps.clj),
      "the config is loaded" (from config_steps.clj, sans the
      isaac.server.app require),
      stdout/exit-code assertions, step_tables helpers if used.
- [ ] Server step namespaces require + delegate instead of redefining; run the
      gherclj ambiguity check.
- [ ] Split src/isaac/spec_helper.clj: foundation scaffolding vs server store
      helpers (it currently requires isaac.session.store*).
- [ ] Re-theme stray server requires in foundation specs so they can move
      later: main_spec requires isaac.session.store; module/loader_spec
      requires isaac.server.routes, isaac.comm.registry, isaac.hooks (use
      marigold berths instead).
- [ ] Per-feature audit of features/{cli,module}/* for stray server-step usage
      (bb match-step per phrase).

## Acceptance

- bb spec and bb features green; gherclj ambiguity check clean.
- Foundation features and the foundation specs listed above require only
  foundation namespaces + the new step layer.
