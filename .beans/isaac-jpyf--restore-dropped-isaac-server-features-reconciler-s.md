---
# isaac-jpyf
title: 'Restore dropped isaac-server features: reconciler + service'
status: todo
type: task
priority: normal
created_at: 2026-06-15T16:52:43Z
updated_at: 2026-06-15T16:52:43Z
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
behavior exercised through real code paths (no helper fakes)."
