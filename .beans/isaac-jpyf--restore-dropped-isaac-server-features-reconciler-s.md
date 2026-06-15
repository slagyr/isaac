---
# isaac-jpyf
title: 'Restore dropped isaac-server features: reconciler + service'
status: completed
type: task
priority: normal
created_at: 2026-06-15T16:52:43Z
updated_at: 2026-06-15T19:00:06Z
---

Two baseline feature files dropped during extraction; both belong in isaac-server (pair with already-landed
server specs). Restore the .feature files AND their step-defs + helpers (gherclj), remove @wip, green via bb features.

Baseline (@ 09795481):
- features/config/reconciler.feature  — "Lifecycle reconciler keeps comm object tree synced with config"
    (pairs with config/configurator_spec -> isaac-server, bean isaac-dw0g)
- features/cli/service.feature         — "isaac service — macOS LaunchAgent management"
    (pairs with service/cli_spec + service/macos_spec already in isaac-server)
Target: isaac-server/features/...

Acceptance: both features green under bb features (no @wip, no pending), step text routes to real steps,
behavior exercised through real code paths (no helper fakes).

## Implemented

Repo: isaac-server @ b56995d
Features: features/config/reconciler.feature (5 scenarios), features/cli/service.feature (14 scenarios)
Steps: spec/isaac/configurator_steps.clj, spec/isaac/service/service_steps.clj
Support: src/isaac/api.clj (comm module shim), :isaac.server/comm berth in resources/isaac-manifest.edn, :comms schema in test-resources/isaac-manifest.edn, isaac.comm.telly test dep (excludes monolith)
Verify: `cd isaac-server && git pull --rebase && bb features features/config/reconciler.feature features/cli/service.feature && bb ci`
Result: reconciler 5/0, service 14/0 (19 scenarios, 82 assertions). Full CI: spec 117/0, features 35/0.
