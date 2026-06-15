---
# isaac-5kn9
title: 'Restore dropped foundation config features: root_pointer + berth_lifecycle'
status: todo
type: task
priority: normal
created_at: 2026-06-15T16:52:43Z
updated_at: 2026-06-15T16:52:43Z
---

Two foundation-owned feature files dropped. Restore into isaac-foundation with step-defs + helpers, remove @wip.

Baseline (@ 09795481):
- features/cli/root_pointer.feature      — "Root-pointer config file" (isaac.config.root resolution; pairs with cli/args_spec)
- features/module/berth_lifecycle.feature — "Config-berth node lifecycle (generic — no comm machinery)"
    (fixture-based, pairs with config/berths_spec -> foundation, bean isaac-ktvs)
Target: isaac-foundation/features/...

Acceptance: both green under bb features, no @wip/pending, real behavior."
