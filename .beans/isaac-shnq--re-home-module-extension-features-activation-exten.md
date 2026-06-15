---
# isaac-shnq
title: Restore module extension-kind features into isaac-agent
status: in-progress
type: task
priority: normal
created_at: 2026-06-15T16:52:43Z
updated_at: 2026-06-15T17:09:39Z
---

Five baseline features/module/ files testing module EXTENSION KINDS, whose berths are agent-tier.
Confirmed: comm/provider/slash berths are declared in isaac-agent's manifest; tool/api are the same
agent-tier family (isaac.api is the agent public surface) — confirm the exact berth key during work.

Baseline (@ 09795481), features/module/ -> isaac-agent:
- comm_extension.feature      — "Comm extension"
- provider_extension.feature  — "Provider extension"
- slash_extension.feature     — "Slash command extension"
- tool_extension.feature      — "Tool extension"
- api_extension.feature       — "Api extension"

Restore each .feature + its step-defs + helpers, exercised through agent's marigold fixtures
(marigold / marigold_comms — fixture modules that contribute each kind via the real berth). Remove @wip,
green via bb features. Boot is data-driven (discover! + start-modules! fire the berth :factory) — fixture-module
integration, no host.

Acceptance: each feature green under isaac-agent bb features, no @wip/pending, real berth machinery + real
fixture modules (no faked steps)."
