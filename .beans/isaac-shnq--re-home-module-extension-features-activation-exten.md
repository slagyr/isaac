---
# isaac-shnq
title: Re-home module-extension features (activation, *_extension, schema_composition)
status: draft
type: task
priority: normal
created_at: 2026-06-15T16:52:43Z
updated_at: 2026-06-15T16:52:43Z
---

Seven baseline module/ feature files testing the module-EXTENSION machinery are dropped. They exercise
"a module can contribute X via its berth and isaac picks it up", using fixture modules.

Baseline (@ 09795481), features/module/:
- activation.feature          — "Module activation"
- schema_composition.feature  — "Module schema composition"
- api_extension.feature       — "Api extension"
- comm_extension.feature      — "Comm extension"
- provider_extension.feature  — "Provider extension"
- slash_extension.feature     — "Slash command extension"
- tool_extension.feature      — "Tool extension"

OPEN (blocks promotion to todo): target per feature.
Lean: generic loader/berth/schema machinery -> isaac-foundation with fixture modules (it already carries
marigold.bridge/longwave/telly-style fixtures); extension-KIND features whose berth is agent-tier
(comm/provider/slash/tool/api) -> isaac-agent (owns those berths + marigold.agent fixtures). Decide the
split before working. Boot is data-driven (module.loader/discover! + start-modules! fire each berth :factory),
so these are fixture-module integration features, not a new host.

Acceptance (once targets set): each feature green under its owning repo's bb features, no @wip/pending,
exercised via real fixture modules + real berth machinery (no faked steps)."
