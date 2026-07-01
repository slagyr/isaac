---
# isaac-5kn9
title: 'Restore dropped foundation config features: root_pointer + berth_lifecycle'
status: completed
type: task
priority: normal
created_at: 2026-06-15T16:52:43Z
updated_at: 2026-06-15T17:16:35Z
---

Two foundation-owned feature files dropped. Restore into isaac-foundation with step-defs + helpers, remove @wip.

Baseline (@ 09795481):
- features/cli/root_pointer.feature      — "Root-pointer config file" (isaac.config.root resolution; pairs with cli/args_spec)
- features/module/berth_lifecycle.feature — "Config-berth node lifecycle (generic — no comm machinery)"
    (fixture-based, pairs with config/berths_spec -> foundation, bean isaac-ktvs)
Target: isaac-foundation/features/...

Acceptance: both green under bb features, no @wip/pending, real behavior."


## Implemented

Repo: isaac-foundation @ 4068e81
Files: features/cli/root_pointer.feature, features/module/berth_lifecycle.feature, spec/isaac/config/config_steps.clj (3 new steps)
Verify: cd isaac-foundation && bb features features/cli/root_pointer.feature features/module/berth_lifecycle.feature
Result: 5 examples, 0 failures, 14 assertions; no @wip/pending. Full suite: features 77/0, spec 747/0.

berth_lifecycle: faithful port; drives real berths/reconcile! against marigold.bridge/longwave fixtures. Added steps: \"the config is reloaded\", \"the nexus node at <path> has state:\", \"the nexus has no node at <path>\" (ensure-config-berths-installed! runs start-modules! + berths/install!; reload does reconcile old->new).

FINDING/adaptation: root_pointer resolution (isaac.config.root) works correctly. Baseline asserted via defaults.model, but :defaults/:crew/:models/:providers config tables are agent/server-side, NOT in foundation schema (loader warns \"unknown key\" + strips them). Rewired the assertion to :tz (foundation-owned key) -- same root-resolution behavior, valid foundation config. Feature/step-only, no production edits.
